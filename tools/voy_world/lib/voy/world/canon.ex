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

  alias Voy.World.{Chronicle, Content, Pathway, Schema, Texts, Timeline, Witness}

  @pathway_relic_names %{
    ancestral_bronze_zheng: "铜钲",
    carved_diversion_stele: "分水石堰"
  }

  # The witness only uses the branch id as a hash salt, so a canonical,
  # non-world branch keeps `check_pathways/1` independent of any topography.
  @pathway_branch :canon_pathway

  @doc "Check the timeline's internal coherence."
  @spec check_timeline() :: :ok | {:error, term()}
  def check_timeline, do: Timeline.validate()

  @doc "Check that every code-side template renders and every placeholder is bound."
  @spec check_texts() :: :ok | {:error, [term()]}
  def check_texts, do: Texts.validate()

  @doc "Check that the authoring content pack loads cleanly with no errors or unused claims."
  @spec check_content(Path.t()) :: :ok | {:error, [map()]}
  def check_content(dir \\ Content.pack_dir()) do
    case Content.lint(dir) do
      %{errors: [], warnings: []} -> :ok
      %{errors: errors, warnings: warnings} -> {:error, errors ++ warnings}
    end
  end

  @doc """
  Check that every topography the rule layer pairs also has a complete archive
  set.

  `compile_branch/1` reports such a gap as `:unsupported_topography`, the same
  answer it gives for an upstream geology the disturbance table never pairs.
  This check separates the two: a paired topography with no slice means the
  content pack -- or a document's pinned precedent -- no longer covers a
  history the rules can still produce.
  """
  @spec check_archives([Keyword.t()]) :: :ok | {:error, [term()]}
  def check_archives(topologies) do
    issues =
      for topo <- topologies,
          Voy.World.paired?(topo),
          match?({:error, _reason}, Voy.World.compile_branch(topo)) do
        {:archives_uncovered, Keyword.take(topo, Voy.World.Schema.feature_keys())}
      end

    case issues do
      [] -> :ok
      issues -> {:error, issues}
    end
  end

  @doc """
  Check that every five-hop micro-chain obeys the mediKanren contract.

  For all twelve `(relic, fate)` pairs and every seed in `seeds` this verifies:

    1. **unique synthesis** — `compile_chain/2` yields exactly one five-hop
       chain whose `:to`/`:from` anchors bind end to end;
    2. **path ablation** — deleting any single hop from the chain leaves a
       pile that `connect_edges/1` cannot connect at all, so no card can
       short-cut the story;
    3. **decoy rejection** — mixing the two same-workshop decoys into the
       five true cards still yields only the true `{:vessel, :main}` path;
    4. **epistemic locality** — the seed-bound batch number occurs only in
       hops 2 and 3, the carrier drawing code only in hops 3 and 5, the two
       duty-cycle readings only in hop 4, and no single card joins the relic
       name, the batch and the carrier code.

  Returns `:ok`, or `{:error, issues}` with one tuple per violation.
  """
  @spec check_pathways([integer()]) :: :ok | {:error, [term()]}
  def check_pathways(seeds \\ [101, 202]) when is_list(seeds) do
    issues =
      for relic <- Pathway.relics(),
          fate <- Schema.fates(),
          seed <- seeds,
          issue <- check_pathway(relic, fate, seed) do
        issue
      end

    case issues do
      [] -> :ok
      issues -> {:error, issues}
    end
  end

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
      strings(Keyword.fetch!(slice, :archives)) ++
        strings(Keyword.fetch!(slice, :structures))

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

  # ==== pathway checks ==== //

  defp check_pathway(relic, fate, seed) do
    case Pathway.compile_chain(relic, fate) do
      {:ok, %{chain: chain, decoys: decoys}} ->
        chain_issues(relic, fate, seed, chain) ++
          ablation_issues(relic, fate, seed, chain) ++
          decoy_issues(relic, fate, seed, chain, decoys) ++
          locality_issues(relic, fate, seed)

      {:error, reason} ->
        [{:pathway_uncompilable, relic, fate, seed, reason}]
    end
  end

  defp chain_issues(relic, fate, seed, chain) do
    shape_issues =
      cond do
        length(chain) != 5 ->
          [{:pathway_chain_shape, relic, fate, seed, {:hop_count, length(chain)}}]

        Enum.map(chain, & &1.hop) != [1, 2, 3, 4, 5] ->
          [{:pathway_chain_shape, relic, fate, seed, {:hop_order, Enum.map(chain, & &1.hop)}}]

        not anchored?(chain) ->
          [{:pathway_chain_shape, relic, fate, seed, :unanchored_hops}]

        true ->
          []
      end

    uniqueness_issues =
      case Pathway.connect_edges(chain) do
        [%{path: ^chain, vessel: {:vessel, :main}}] ->
          []

        connections ->
          [
            {:pathway_chain_not_unique, relic, fate, seed, length(connections),
             Enum.map(connections, & &1.vessel)}
          ]
      end

    shape_issues ++ uniqueness_issues
  end

  defp anchored?([_single]), do: true

  defp anchored?([left, right | rest]) do
    left.to == right.from and anchored?([right | rest])
  end

  defp ablation_issues(relic, fate, seed, chain) do
    Enum.flat_map(1..5, fn dropped ->
      remaining = Enum.reject(chain, &(&1.hop == dropped))

      if Pathway.connect_edges(remaining) == [] do
        []
      else
        [{:pathway_ablation_connected, relic, fate, seed, dropped}]
      end
    end)
  end

  defp decoy_issues(relic, fate, seed, chain, decoys) do
    mixed =
      case Pathway.connect_edges(chain ++ decoys) do
        [%{path: ^chain, vessel: {:vessel, :main}}] ->
          []

        connections ->
          [
            {:pathway_decoy_not_rejected, relic, fate, seed, length(connections),
             Enum.map(connections, & &1.vessel)}
          ]
      end

    case Pathway.connect_edges(decoys) do
      [] -> mixed
      connections -> mixed ++ [{:pathway_decoy_self_chains, relic, fate, seed, length(connections)}]
    end
  end

  defp locality_issues(relic, fate, seed) do
    dossier = Witness.render_dossier(seed, @pathway_branch, relic, fate, [])
    binding = dossier.binding
    main = Enum.filter(dossier.documents, &(&1.role == :main))
    relic_name = Map.fetch!(@pathway_relic_names, relic)
    total = fn needle -> Enum.sum(Enum.map(main, &occurrences(&1.body, needle))) end

    batch_positions = positions(main, binding.batch_id)
    carrier_positions = positions(main, binding.vessel_code)

    []
    |> add(
      batch_positions != [2, 3] or total.(binding.batch_id) != 2,
      {:pathway_batch_leak, relic, fate, seed, batch_positions}
    )
    |> add(
      carrier_positions != [3, 5] or total.(binding.vessel_code) != 2,
      {:pathway_carrier_leak, relic, fate, seed, carrier_positions}
    )
    |> add_duty_cycle(main, "28.1%", relic, fate, seed)
    |> add_duty_cycle(main, "60.6%", relic, fate, seed)
    |> add(
      Enum.any?(main, fn document ->
        String.contains?(document.body, relic_name) and
          String.contains?(document.body, binding.batch_id) and
          String.contains?(document.body, binding.vessel_code)
      end),
      {:pathway_omniscient_card, relic, fate, seed}
    )
  end

  defp add_duty_cycle(issues, main, marker, relic, fate, seed) do
    add(
      issues,
      positions(main, marker) != [4],
      {:pathway_duty_cycle_leak, relic, fate, seed, marker, positions(main, marker)}
    )
  end

  defp positions(documents, needle) do
    documents
    |> Enum.filter(&String.contains?(&1.body, needle))
    |> Enum.map(& &1.hop)
  end

  defp occurrences(body, needle) do
    body |> String.split(needle) |> length() |> Kernel.-(1)
  end

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
