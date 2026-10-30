defmodule Voy.World.Canon do
  @moduledoc """
  Consistency checks for history, structures, and five-hop local evidence.

  `Chronicle` owns historical facts, `Pathway` owns local relations, and
  `Witness` renders seeded cards. No global archive or attribution summary is
  assembled or validated here.
  """

  use Voy.Kanren

  alias Voy.World.{Chronicle, Content, Pathway, Schema, Texts, Timeline, Witness}

  @pathway_relic_names %{
    ancestral_bronze_zheng: "铜钲",
    carved_diversion_stele: "分水石堰"
  }
  @pathway_branch :canon_pathway

  def check_timeline, do: Timeline.validate()
  def check_texts, do: Texts.validate()

  def check_content(dir \\ Content.pack_dir()) do
    case Content.lint(dir) do
      %{errors: [], warnings: []} -> :ok
      %{errors: errors, warnings: warnings} -> {:error, errors ++ warnings}
    end
  end

  @doc "Check every five-hop chain and every requested seed."
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

  def check_fates do
    issues =
      for fate <- Schema.fates(),
          match?({:ok, []}, Voy.World.reverse_worldgen(fate)),
          do: {:unreachable_fate, fate}

    case issues do
      [] -> :ok
      issues -> {:error, issues}
    end
  end

  def check_topography(topo) do
    slices = run_all(fn query -> Chronicle.compile_branch_sliceo(topo, query) end) |> terms()

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

  def check_corpus(topologies) do
    results = Enum.map(topologies, &{&1, check_topography(&1)})

    %{
      checked: length(results),
      ok: Enum.count(results, &match?({_topo, :ok}, &1)),
      unsupported: Enum.count(results, &match?({_topo, {:unsupported, _reason}}, &1)),
      issues:
        for {topo, {:error, issues}} <- results do
          %{topography: Keyword.take(topo, Schema.feature_keys()), issues: issues}
        end
    }
  end

  @doc "Check structure identities and date ownership for one history slice."
  def check_slice(topo, slice) do
    issues =
      check_structure_sites(topo, slice) ++
        date_issues(strings(Keyword.fetch!(slice, :structures)))

    case issues do
      [] -> :ok
      issues -> {:error, issues}
    end
  end

  defp check_structure_sites(topo, slice) do
    structures = Keyword.fetch!(slice, :structures)
    expected = MapSet.new(sites(topo))

    for {key, role} <- [upstream_site: :upstream, pass_site: :pass, downstream_site: :downstream],
        {name, _description} = Keyword.fetch!(structures, key),
        not MapSet.member?(expected, name),
        do: {:unknown_structure_site, role, name}
  end

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
  defp anchored?([left, right | rest]), do: left.to == right.from and anchored?([right | rest])

  defp ablation_issues(relic, fate, seed, chain) do
    Enum.flat_map(1..5, fn dropped ->
      remaining = Enum.reject(chain, &(&1.hop == dropped))

      if Pathway.connect_edges(remaining) == [],
        do: [],
        else: [{:pathway_ablation_connected, relic, fate, seed, dropped}]
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
      [] ->
        mixed

      connections ->
        mixed ++ [{:pathway_decoy_self_chains, relic, fate, seed, length(connections)}]
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
    |> Kernel.++(date_issues(Enum.flat_map(dossier.documents, &[&1.title, &1.body])))
  end

  defp add_duty_cycle(issues, main, marker, relic, fate, seed) do
    add(
      issues,
      positions(main, marker) != [4],
      {:pathway_duty_cycle_leak, relic, fate, seed, marker, positions(main, marker)}
    )
  end

  defp positions(documents, needle) do
    documents |> Enum.filter(&String.contains?(&1.body, needle)) |> Enum.map(& &1.hop)
  end

  defp occurrences(body, needle), do: body |> String.split(needle) |> length() |> Kernel.-(1)

  # ==== dates and small helpers ==== //

  defp date_issues(texts) do
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

  defp add(issues, false, _issue), do: issues
  defp add(issues, true, issue), do: issues ++ [issue]

  defp sites(topo),
    do: [
      Keyword.fetch!(topo, :up_name),
      Keyword.fetch!(topo, :pass_name),
      Keyword.fetch!(topo, :down_name)
    ]

  defp strings(term) when is_binary(term), do: [term]
  defp strings(term) when is_tuple(term), do: term |> Tuple.to_list() |> strings()

  defp strings(term) when is_list(term) do
    if Keyword.keyword?(term),
      do: Enum.flat_map(term, fn {_key, value} -> strings(value) end),
      else: Enum.flat_map(term, &strings/1)
  end

  defp strings(_term), do: []
end
