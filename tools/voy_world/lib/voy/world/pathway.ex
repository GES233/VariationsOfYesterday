defmodule Voy.World.Pathway do
  @moduledoc """
  mediKanren-style five-hop local micro-chains (`hop 1..5`) plus two same-workshop
  decoy edges per relic branch.

  Edges chain purely by `:from` / `:to` anchor term unification:

      {:relic, relic, damping}
        --hop 1 / gazetteer excerpt-->    {:precedent_omen, omen, anchor}
        --hop 2 / requisition slip-->     {:scrap_batch, batch, material, c0_status, anchor}
        --hop 3 / workshop card-->        {:industrial_vessel, vessel, kind, c0_status, anchor}
        --hop 4 / field notebook-->       {:modulation_protocol, vessel, window, fate}
        --hop 5 / council ordinance-->    {:emergent_ritual, fate, vessel, outcome}
  """

  use Voy.Kanren

  alias Voy.Kanren.Var
  alias Voy.World.Schema

  @max_depth 5
  @window :measured_delay_window
  @ritual_outcome :spike_delayed_or_stabilized

  @relic_specs [
    %{
      relic: :ancestral_bronze_zheng,
      damping: :acoustic_damping,
      omen: :fish_scale_ripple_and_slide,
      anchor: {:tau_breaths, 3},
      material: :high_tin_bronze,
      vessel_kind: :bronze_alloy_casting,
      workshop_lens: :workshop_tech,
      decoy_material: :plain_grey_iron,
      decoy_anchor: {:tau_breaths, 0},
      decoy_vessel_kind: :grey_iron_casting,
      shipped_out: :shipped_out_to_provincial_smelter
    },
    %{
      relic: :carved_diversion_stele,
      damping: :karst_siphon_relief,
      omen: :sinkhole_backflow_and_flood,
      anchor: {:siphon_head_m, 12},
      material: :grooved_limestone_ashlar,
      vessel_kind: :siphon_weir_lining,
      workshop_lens: :hydro_tech,
      decoy_material: :plain_limestone_block,
      decoy_anchor: {:siphon_head_m, 0},
      decoy_vessel_kind: :plain_ashlar_lining,
      shipped_out: :shipped_out_to_provincial_water_bureau
    }
  ]

  @doc "The relic branches the pathway knows, in declaration order."
  @spec relics() :: [atom()]
  def relics, do: Schema.relics()

  @doc "Physical `(c0_status, pulse, regime, governance)` metadata for a fate."
  @spec fate_spec(atom() | String.t()) :: map()
  def fate_spec(fate), do: Schema.fate_spec(fate)

  @doc "The declared cycle window the safe taps live in."
  @spec window() :: atom()
  def window, do: @window

  @doc "The single ending a completed pathway can reach."
  @spec ritual_outcome() :: atom()
  def ritual_outcome, do: @ritual_outcome

  @doc """
  The five true edges of one `(relic, fate)` pathway, in hop order.

  The terms are ground except for the symbolic batch/vessel slots, which
  `Voy.World.Witness` turns into concrete identifiers.
  """
  @spec edges(atom(), atom() | String.t()) :: [map()]
  def edges(relic, fate) do
    spec = spec!(relic)
    fate = fate!(fate)
    fspec = Schema.fate_spec(fate)
    batch = {:batch, :main}
    vessel = {:vessel, :main}

    [
      %{
        hop: 1,
        doc_kind: :gazetteer_excerpt,
        lens: :gazetteer,
        from: {:relic, spec.relic, spec.damping},
        relation: :removal_provokes_omen,
        to: {:precedent_omen, spec.omen, spec.anchor},
        attrs: [
          relic: spec.relic,
          damping: spec.damping,
          omen: spec.omen,
          anchor: spec.anchor
        ]
      },
      %{
        hop: 2,
        doc_kind: :requisition_slip,
        lens: :official_logistics,
        from: {:precedent_omen, spec.omen, spec.anchor},
        relation: fspec.hop2_relation,
        to: {:scrap_batch, batch, spec.material, fspec.c0_status, spec.anchor},
        attrs: [
          batch: batch,
          material: spec.material,
          c0_status: fspec.c0_status,
          anchor: spec.anchor,
          disposition:
            if(fspec.c0_status == :c0_collapsed,
              do: :workshop_intake,
              else: :in_situ_custody_sample
            )
        ]
      },
      %{
        hop: 3,
        doc_kind: :workshop_card,
        lens: spec.workshop_lens,
        from: {:scrap_batch, batch, spec.material, fspec.c0_status, spec.anchor},
        relation: fspec.hop3_relation,
        to: {:industrial_vessel, vessel, spec.vessel_kind, fspec.c0_status, spec.anchor},
        attrs: [
          batch: batch,
          vessel: vessel,
          vessel_kind: spec.vessel_kind,
          material: spec.material,
          c0_status: fspec.c0_status,
          anchor: spec.anchor
        ]
      },
      %{
        hop: 4,
        doc_kind: :field_notebook,
        lens: :hydro_tech,
        from: {:industrial_vessel, vessel, spec.vessel_kind, fspec.c0_status, spec.anchor},
        relation: fspec.hop4_relation,
        to: {:modulation_protocol, vessel, @window, fate},
        attrs: [
          vessel: vessel,
          c0_status: fspec.c0_status,
          pulse: fspec.pulse,
          regime: fspec.regime,
          window: @window,
          observable: :duty_cycle_inversion
        ]
      },
      %{
        hop: 5,
        doc_kind: :council_ordinance,
        lens: :council_folk,
        from: {:modulation_protocol, vessel, @window, fate},
        relation: fspec.hop5_relation,
        to: {:emergent_ritual, fate, vessel, @ritual_outcome},
        attrs: [
          vessel: vessel,
          c0_status: fspec.c0_status,
          regime: fspec.regime,
          governance: fspec.governance,
          window: @window,
          outcome: @ritual_outcome
        ]
      }
    ]
  end

  @doc """
  The two decoy records declared next to the true chain, in class order.

  Each carries `:decoy_class` and the hop pair it fails to cross in `:attrs`.
  """
  @spec decoys(atom()) :: [map()]
  def decoys(relic) do
    spec = spec!(relic)
    batch = {:batch, :decoy_a}
    vessel = {:vessel, :decoy_a}

    [
      %{
        hop: 3,
        doc_kind: :workshop_card,
        lens: :workshop_tech,
        from: {:scrap_batch, batch, spec.decoy_material, :c0_collapsed, spec.decoy_anchor},
        relation: :cast_into_industrial_vessel,
        to:
          {:industrial_vessel, vessel, spec.decoy_vessel_kind, :c0_collapsed, spec.decoy_anchor},
        attrs: [
          decoy_class: :material_mismatch,
          breaks_at: {3, 4},
          batch: batch,
          vessel: vessel,
          material: spec.decoy_material,
          anchor: spec.decoy_anchor,
          origin: :requisitioned_as_scrap
        ]
      },
      %{
        hop: 2,
        doc_kind: :requisition_slip,
        lens: :official_logistics,
        from: {:precedent_omen, spec.omen, spec.anchor},
        relation: :requisitioned_as_scrap,
        to: {:scrap_shipment, {:batch, :decoy_b}, spec.material, spec.shipped_out, spec.anchor},
        attrs: [
          decoy_class: :destination_dead_end,
          breaks_at: {2, 3},
          batch: {:batch, :decoy_b},
          material: spec.material,
          anchor: spec.anchor,
          disposition: spec.shipped_out
        ]
      }
    ]
  end

  @doc """
  Single-hop relation: unify `edge` with any declared edge of `(relic, fate)`.

  With a free `fate`, hops 4 and 5 are enumerated for every declared fate;
  hops 1-3 are fate-independent and appear once.
  """
  @spec edgeo(atom(), term(), term()) :: Voy.Kanren.Core.goal()
  def edgeo(relic, fate, edge) do
    any(Enum.map(candidates(relic, fate, :all), &eq(edge, &1)))
  end

  @doc """
  Five-hop relation: the only accepted path is five edges whose `:to` and the
  next `:from` unify, from `start_node` to `end_node`.

  `hops` is unified with the five edges in hop order.
  """
  @spec chaino(atom(), term(), term(), term(), term()) :: Voy.Kanren.Core.goal()
  def chaino(relic, fate, start_node, end_node, hops) do
    fresh_n(5, fn [e1, e2, e3, e4, e5] ->
      fresh_n(10, fn [f1, t1, f2, t2, f3, t3, f4, t4, f5, t5] ->
        all([
          stepo(relic, fate, 1, e1, f1, t1),
          stepo(relic, fate, 2, e2, f2, t2),
          eq(t1, f2),
          stepo(relic, fate, 3, e3, f3, t3),
          eq(t2, f3),
          stepo(relic, fate, 4, e4, f4, t4),
          eq(t3, f4),
          stepo(relic, fate, 5, e5, f5, t5),
          eq(t4, f5),
          eq(start_node, f1),
          eq(end_node, t5),
          eq(hops, [e1, e2, e3, e4, e5])
        ])
      end)
    end)
  end

  @doc """
  Deterministic forward solve: the unique five-hop micro-chain and the two
  decoy records for one `(relic, fate)`.

  The chain is obtained by *querying* `chaino/5`, so the forward answer and the
  relational answer cannot drift apart; an ambiguous or malformed result is an
  error rather than a silent pick.
  """
  @spec compile_chain(atom(), atom() | String.t()) ::
          {:ok, %{chain: [map()], decoys: [map()]}} | {:error, term()}
  def compile_chain(relic, fate) do
    with {:ok, spec} <- expect_spec(relic),
         {:ok, fate} <- expect_fate(fate),
         [solution] <- solve_chain(relic, fate),
         :ok <- verify_solution(solution, spec, fate) do
      {:ok, %{chain: solution[:hops], decoys: decoys(relic)}}
    else
      [] -> {:error, :no_chain}
      [_ | _] -> {:error, :ambiguous_chain}
      :error -> {:error, :invalid_chain}
      {:error, _reason} = error -> error
    end
  end

  @doc """
  Connect a loose pile of edges using node unification alone.

  Returns every path that starts at a `{:relic, _, _}` node and finishes at
  `{:emergent_ritual, fate, vessel, :spike_delayed_or_stabilized}`, with the
  edges in traversal order. Paths are at most five hops long (the length of a
  pathway); results are sorted by length, hop sequence and endpoint terms, so
  the output does not depend on the order in which the edges were supplied.

  The function does not look at any `:fate` key on the edge maps: only
  `:from`/`:to` anchor terms take part in chaining.
  """
  @spec connect_edges([map()]) :: [
          %{start: term(), finish: term(), vessel: term(), path: [map()]}
        ]
  def connect_edges(edges) when is_list(edges) do
    triples =
      Enum.map(edges, fn edge -> {Map.fetch!(edge, :from), Map.fetch!(edge, :to), edge} end)

    run_all(fn q ->
      fresh_n(4, fn [start, finish, vessel, path] ->
        all([
          relic_nodeo(start),
          patho(triples, start, finish, path, @max_depth),
          ritual_nodeo(finish, vessel),
          eq(q, start: start, finish: finish, vessel: vessel, path: path)
        ])
      end)
    end)
    |> terms()
    |> Enum.map(&to_connection/1)
    |> Enum.uniq()
    |> Enum.sort_by(&sort_key/1)
  end

  @doc "Whether two terms unify, i.e. whether an edge can be chained onto a node."
  @spec unifies?(term(), term()) :: boolean()
  def unifies?(left, right) do
    run_all(fn q -> all([eq(left, right), eq(q, :unified)]) end)
    |> terms()
    |> Kernel.!=([])
  end

  # ==== relation internals ==== //

  defp solve_chain(relic, fate) do
    run_all(fn q ->
      fresh_n(3, fn [start, finish, hops] ->
        all([
          chaino(relic, fate, start, finish, hops),
          eq(q, start: start, finish: finish, hops: hops)
        ])
      end)
    end)
    |> terms()
  end

  defp verify_solution(solution, spec, fate) do
    start = solution[:start]
    finish = solution[:finish]
    hops = solution[:hops]
    expected_start = {:relic, spec.relic, spec.damping}
    expected_finish = {:emergent_ritual, fate, {:vessel, :main}, @ritual_outcome}

    cond do
      start != expected_start -> :error
      finish != expected_finish -> :error
      Enum.map(hops, & &1.hop) != [1, 2, 3, 4, 5] -> :error
      not chained?(hops) -> :error
      true -> :ok
    end
  end

  @doc "Whether a hop list is anchored end-to-end (`left.to == right.from`)."
  def chained?([_single]), do: true

  def chained?([left, right | rest]) do
    left.to == right.from and chained?([right | rest])
  end

  defp stepo(relic, fate, hop, edge, from, to) do
    branches =
      for candidate <- candidates(relic, fate, hop) do
        all([eq(edge, candidate), eq(from, candidate.from), eq(to, candidate.to)])
      end

    any(branches)
  end

  defp candidates(relic, fate, hop) do
    for branch_fate <- fate_domain(fate),
        edge <- edges(relic, branch_fate),
        hop_matches?(edge, hop) do
      edge
    end
    |> Enum.uniq()
  end

  defp hop_matches?(_edge, :all), do: true
  defp hop_matches?(edge, hop), do: edge.hop == hop

  defp fate_domain(%Var{}), do: Schema.fates()

  defp fate_domain(fate) do
    if fate in Schema.fates(), do: [fate], else: []
  end

  defp relic_nodeo(start) do
    any(Enum.map(@relic_specs, fn spec -> eq(start, {:relic, spec.relic, spec.damping}) end))
  end

  defp ritual_nodeo(finish, vessel) do
    any(
      Enum.map(Schema.fates(), fn fate ->
        eq(finish, {:emergent_ritual, fate, vessel, @ritual_outcome})
      end)
    )
  end

  defp patho(_triples, current, target, path, 0) do
    all([eq(target, current), eq(path, [])])
  end

  defp patho(triples, current, target, path, depth) do
    fresh_n(4, fn [from, to, edge, rest] ->
      any([
        all([eq(target, current), eq(path, [])]),
        all([
          membero({from, to, edge}, triples),
          eq(from, current),
          eq(path, [{current, to, edge} | rest]),
          patho(triples, to, target, rest, depth - 1)
        ])
      ])
    end)
  end

  defp to_connection(result) do
    path = Enum.map(result[:path], fn {_from, _to, edge} -> edge end)

    %{
      start: result[:start],
      finish: result[:finish],
      vessel: result[:vessel],
      path: path
    }
  end

  defp sort_key(%{start: start, finish: finish, path: path}) do
    {length(path), Enum.map(path, & &1.hop), start, finish}
  end

  # ==== inputs ==== //

  defp expect_spec(relic) do
    case spec_for(relic) do
      :error -> {:error, {:unknown_relic, relic}}
      spec -> {:ok, spec}
    end
  end

  defp expect_fate(fate), do: Schema.normalize_fate(fate)

  defp spec_for(relic), do: Enum.find(@relic_specs, &(&1.relic == relic)) || :error

  defp spec!(relic) do
    case spec_for(relic) do
      :error -> raise ArgumentError, "unknown relic: #{inspect(relic)}"
      spec -> spec
    end
  end

  defp fate!(fate) do
    case Schema.normalize_fate(fate) do
      {:ok, normalized} ->
        normalized

      {:error, reason} ->
        raise ArgumentError, "unknown fate #{inspect(fate)}: #{inspect(reason)}"
    end
  end
end
