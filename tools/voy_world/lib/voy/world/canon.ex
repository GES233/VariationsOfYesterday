defmodule Voy.World.Canon do
  @moduledoc """
  Lint for a compiled canon: the properties that make the world consistent
  rather than merely long.

  Four families of checks, all of them able to fail:

    1. **name identity** — structures and documents name the same sites as
       the topography, and the truth points at the same upstream site the
       official report blames for something else;
    2. **date ownership** — every year written into prose is a year the
       timeline declares, in whichever form (Gregorian, two-digit field
       notation, reign form); prose may not invent a date;
    3. **attribution split** — each compiled history carries a corroborating
       document and a contradicting one, and the official report never
       adopts the truth's cause tag;
    4. **uniqueness** — a complete topography compiles to exactly one
       history slice, so the reverse query is not ambiguous.

  Unsupported or undecided topographies are reported as `:unsupported`, not
  as canon errors: coverage is a separate question from consistency.
  """

  use Voy.Kanren

  alias Voy.World.{Chronicle, Texts, Timeline}

  @doc "Check the timeline's internal coherence."
  @spec check_timeline() :: :ok | {:error, term()}
  def check_timeline, do: Timeline.validate()

  @doc "Check that every document renders and every placeholder is bound."
  @spec check_texts() :: :ok | {:error, [term()]}
  def check_texts, do: Texts.validate()

  @doc "Check that every fate declared by the schema is reachable from some topography."
  @spec check_fates() :: :ok | {:error, [term()]}
  def check_fates do
    issues =
      for fate <- Voy.World.Schema.fates(),
          match?({:ok, []}, Voy.World.reverse_worldgen(fate)),
          do: {:unreachable_fate, fate}

    case issues do
      [] -> :ok
      issues -> {:error, issues}
    end
  end

  @doc """
  Compile one topography and lint the resulting history.

  Returns `:ok`, `{:error, issues}` for an inconsistent canon, or
  `{:unsupported, reason}` when the topography has no decided history yet.
  """
  @spec check_topography(Keyword.t()) :: :ok | {:error, [term()]} | {:unsupported, term()}
  def check_topography(topo) do
    slices =
      run_all(fn query -> Chronicle.compile_branch_sliceo(topo, query) end)
      |> terms()

    case slices do
      [] ->
        case Voy.World.compile_branch(topo) do
          {:error, reason} -> {:unsupported, reason}
          {:ok, _slices} -> {:error, [{:empty_history}]}
        end

      [slice] ->
        check_slice(topo, slice)

      many ->
        {:error, [{:ambiguous_history, length(many)}]}
    end
  end

  @doc "Lint every topography and summarise the outcome."
  @spec check_corpus([Keyword.t()]) :: map()
  def check_corpus(topologies) do
    results = Enum.map(topologies, &{&1, check_topography(&1)})

    %{
      checked: length(results),
      ok: Enum.count(results, &match?({_topo, :ok}, &1)),
      unsupported: Enum.count(results, &match?({_topo, {:unsupported, _reason}}, &1)),
      issues:
        for {topo, {:error, issues}} <- results do
          %{topography: Keyword.take(topo, Voy.World.Schema.feature_keys()), issues: issues}
        end
    }
  end

  @doc "Lint one compiled (raw) history slice against its topography."
  @spec check_slice(Keyword.t(), Keyword.t()) :: :ok | {:error, [term()]}
  def check_slice(topo, slice) do
    issues =
      check_structure_sites(topo, slice) ++
        check_archive_names(topo, slice) ++
        check_archive_dates(slice) ++
        check_attribution_split(slice)

    case issues do
      [] -> :ok
      issues -> {:error, issues}
    end
  end

  # ==== name identity ==== //

  defp check_structure_sites(topo, slice) do
    structures = Keyword.fetch!(slice, :structures)
    expected = MapSet.new(sites(topo))

    for {key, role} <- [
          upstream_site: :upstream,
          pass_site: :pass,
          downstream_site: :downstream
        ],
        {name, _description} = Keyword.fetch!(structures, key),
        not MapSet.member?(expected, name),
        do: {:unknown_structure_site, role, name}
  end

  defp check_archive_names(topo, slice) do
    archives = Keyword.fetch!(slice, :archives)
    up_name = Keyword.fetch!(topo, :up_name)
    down_name = Keyword.fetch!(topo, :down_name)
    gazetteer = Keyword.fetch!(archives, :gazetteer)
    official = Keyword.fetch!(archives, :official_report)
    tech = Keyword.fetch!(archives, :tech_note)
    council = Keyword.fetch!(archives, :council_document)

    Enum.reject(
      [
        {:gazetteer_settlement, gazetteer[:recorded_settlement], down_name},
        {:official_settlement, official[:target_settlement], down_name},
        {:tech_settlement, tech[:settlement], down_name},
        {:council_settlement, council[:settlement], down_name},
        {:official_upstream, official[:upstream_site], up_name},
        {:tech_upstream, tech[:true_source], up_name}
      ],
      fn {_field, actual, expected} -> actual == expected end
    )
    |> Enum.map(fn {field, actual, expected} -> {:name_mismatch, field, actual, expected} end)
  end

  # ==== date ownership ==== //

  defp check_archive_dates(slice) do
    texts =
      slice
      |> Keyword.fetch!(:archives)
      |> strings()

    years = Timeline.years()
    short_years = Enum.map(years, &Timeline.short_year/1)
    reign_numerals = reign_numerals()

    short_year_issues =
      for text <- texts,
          [_match, short] <- Regex.scan(~r/(?<!\d)(\d{2})年/, text),
          short not in short_years,
          do: {:undeclared_short_year, short, text}

    year_issues =
      for text <- texts,
          [_match, year] <- Regex.scan(~r/(?<!\d)(\d{4})(?!\d)/, text),
          String.to_integer(year) not in years,
          do: {:undeclared_year, year, text}

    reign_issues =
      for text <- texts,
          [_match, numeral] <- Regex.scan(~r/光绪([一二三四五六七八九十]+)年/, text),
          numeral not in reign_numerals,
          do: {:undeclared_reign_year, numeral, text}

    short_year_issues ++ year_issues ++ reign_issues
  end

  defp reign_numerals do
    for anchor <- Map.keys(Timeline.anchors()),
        year = Timeline.year(anchor),
        reign = Timeline.reign_of(year),
        reign != nil,
        do: Timeline.chinese_numeral(Timeline.reign_ordinal(reign, year))
  end

  # ==== attribution split ==== //

  defp check_attribution_split(slice) do
    archives = Keyword.fetch!(slice, :archives)
    gazetteer = Keyword.fetch!(archives, :gazetteer)
    official = Keyword.fetch!(archives, :official_report)
    tech = Keyword.fetch!(archives, :tech_note)

    truth = tech[:true_cause]
    claimed = official[:claimed_cause]

    []
    |> add(
      truth == claimed,
      {:official_adopts_truth, claimed}
    )
    |> add(
      tech[:corroborated_gazetteer] != gazetteer[:precedent],
      {:uncorroborated_claim, tech[:corroborated_gazetteer], gazetteer[:precedent]}
    )
    |> add(is_nil(truth) or not is_atom(truth), {:missing_true_cause, truth})
    |> add(is_nil(claimed) or not is_atom(claimed), {:missing_official_cause, claimed})
  end

  defp add(issues, false, _issue), do: issues
  defp add(issues, true, issue), do: issues ++ [issue]

  # ==== helpers ==== //

  defp sites(topo) do
    [
      Keyword.fetch!(topo, :up_name),
      Keyword.fetch!(topo, :pass_name),
      Keyword.fetch!(topo, :down_name)
    ]
  end

  defp strings(term) when is_binary(term), do: [term]
  defp strings(term) when is_tuple(term), do: term |> Tuple.to_list() |> strings()

  defp strings(term) when is_list(term) do
    if Keyword.keyword?(term) do
      Enum.flat_map(term, fn {_key, value} -> strings(value) end)
    else
      Enum.flat_map(term, &strings/1)
    end
  end

  defp strings(_term), do: []
end
