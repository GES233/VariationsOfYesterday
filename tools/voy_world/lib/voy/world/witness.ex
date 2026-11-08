defmodule Voy.World.Witness do
  @moduledoc """
  Seeded skolemization and local-card rendering for a `Voy.World.Pathway`.

  Relations stay in `Pathway`, prose stays in `Voy.World.Content`, and this
  module binds `(world_seed, branch_id, relic, fate)` to concrete batch numbers,
  industrial carriers, and an exact metal-resonance tick measured as `Delayed`
  by `Voy.World.Contract`. Five main cards and two decoys know only their own
  hop; no global narrative summary is assembled here.
  """

  alias Voy.World.{Chronicle, Contract, Content, Fields, Pathway, Schema}

  @relic_bronze :ancestral_bronze_zheng
  @relic_stele :carved_diversion_stele

  @bronze_batches [
    "滇垦废字（79）第04号",
    "云农调字（79）第11号",
    "垦机收字（80）第07号",
    "边森回字（79）第19号"
  ]

  @bronze_vessels [
    {"东方红-54型叁号气缸盖", "翻-79-09-丙"},
    {"丰收-35型副变速箱壳体", "铸-79-11-甲"},
    {"红旗-100型高压水泵蜗壳", "修-80-02-乙"},
    {"195型柴油机平衡飞轮盘", "翻-79-12-丁"}
  ]

  @stele_batches [
    "滇垦基字（79）第03号",
    "云水调字（79）第08号",
    "林工石字（80）第12号",
    "垦建征字（80）第15号"
  ]

  @stele_vessels [
    {"叁号冷却水塔副溢流槽", "砌-79-10-甲"},
    {"机修房尾水消力池隔墙", "水-79-12-丙"},
    {"二号水轮泵站回水弯道", "建-80-03-乙"},
    {"选矿场沉砂池虹吸底坎", "砌-79-11-丁"}
  ]

  @branches [
    %{relic: @relic_bronze, batches: @bronze_batches, vessels: @bronze_vessels},
    %{relic: @relic_stele, batches: @stele_batches, vessels: @stele_vessels}
  ]

  @doc "Bind one seed to concrete, mutually distinct pathway credentials."
  @spec bind(integer(), term(), atom(), atom() | String.t(), Keyword.t() | map()) :: map()
  def bind(seed, branch_id, relic, fate, opts \\ []) do
    spec = branch!(relic)
    fate = fate!(fate)
    horizon = horizon!(Fields.fetch_opt(opts, :horizon, :locked))
    dynamics = dynamics()
    relation_decoys = Pathway.decoys(relic)

    material_decoy =
      Enum.find(relation_decoys, &(&1.attrs[:decoy_class] == :material_mismatch))

    destination_decoy =
      Enum.find(relation_decoys, &(&1.attrs[:decoy_class] == :destination_dead_end))

    [batch_id, decoy_a_batch, decoy_b_batch] =
      rotate(spec.batches, hash(seed, branch_id, :batch, relic, fate, length(spec.batches)), 3)

    [vessel, decoy_vessel] =
      rotate(spec.vessels, hash(seed, branch_id, :vessel, relic, fate, length(spec.vessels)), 2)

    ticks = dynamics.safe_ticks
    safe = Enum.at(ticks, hash(seed, branch_id, :safe_tick, relic, fate, length(ticks)))

    base = %{
      seed: seed,
      branch_id: branch_id,
      relic: relic,
      fate: fate,
      horizon: horizon,
      settled: horizon == :locked,
      batch_id: batch_id,
      vessel_name: elem(vessel, 0),
      vessel_code: elem(vessel, 1),
      safe_tick: safe.tick,
      safe_phase: safe.phase,
      safe_delay: safe.delta,
      safe_window: safe.window,
      decoys: %{
        material_mismatch: %{
          batch_id: decoy_a_batch,
          vessel_name: elem(decoy_vessel, 0),
          vessel_code: elem(decoy_vessel, 1),
          material: material_decoy.attrs[:material],
          anchor: material_decoy.attrs[:anchor]
        },
        destination_dead_end: %{
          batch_id: decoy_b_batch,
          material: destination_decoy.attrs[:material],
          disposition: destination_decoy.attrs[:disposition]
        }
      },
      dynamics: dynamics
    }

    if horizon == :open do
      Map.put(
        base,
        :reachable_transitions,
        build_reachable_transitions(fate, batch_id, elem(vessel, 0), elem(vessel, 1), safe.tick)
      )
    else
      base
    end
  end

  @doc "Exact integer ticks measured as delayed for the metal-resonance pulse."
  @spec safe_ticks() :: [pos_integer()]
  def safe_ticks, do: Enum.map(dynamics().safe_ticks, & &1.tick)

  @doc "Render the five main cards (or Hop 1..4 when `horizon: :open`) and two decoys from the authoring pack. Options: `:pack` and `:horizon`."
  def render_dossier(seed, branch_id, relic, fate, names \\ [], opts \\ [])
      when is_list(opts) or (is_map(opts) and not is_struct(opts)) do
    pack = Fields.fetch_opt(opts, :pack, nil) || Content.default_pack()

    horizon_opt =
      Fields.fetch_opt(opts, :horizon, nil) || Fields.fetch_opt(names, :horizon, :locked)

    horizon = horizon!(horizon_opt)
    binding = bind(seed, branch_id, relic, fate, horizon: horizon)
    {:ok, %{chain: chain}} = Pathway.compile_chain(relic, binding.fate)
    decoys = Pathway.decoys(relic)
    resolved_places = places(names)
    interpolation = interpolation(binding, resolved_places)

    main = Enum.map(chain, &document(&1, interpolation, :main, relic, binding.fate, pack))

    decoy_documents =
      Enum.map(decoys, &document(&1, interpolation, :decoy, relic, binding.fate, pack))

    case horizon do
      :locked ->
        %{
          seed: seed,
          branch_id: branch_id,
          relic: relic,
          fate: binding.fate,
          horizon: :locked,
          settled: true,
          binding: binding,
          documents: main ++ decoy_documents
        }

      :open ->
        open_main = Enum.reject(main, &(&1.hop == 5))

        %{
          seed: seed,
          branch_id: branch_id,
          relic: relic,
          fate: binding.fate,
          baseline_fate: binding.fate,
          horizon: :open,
          settled: false,
          reachable_transitions: binding.reachable_transitions,
          binding: binding,
          documents: open_main ++ decoy_documents
        }
    end
  end

  @doc """
  Settle an `:open` dossier under a player intervention (`:no_intervention`,
  `:reanchor_vessel`, `:dampen_headwater`, `:reanchor_and_dampen`), preserving
  the seed-bound `batch_id`, `vessel_code`, and `safe_tick` while unlocking the
  Hop 5 council ordinance for the resulting `target_fate`.
  """
  def settle_open_dossier(dossier, intervention, opts \\ []) when is_map(dossier) do
    pack = Fields.fetch_opt(opts, :pack, nil) || Content.default_pack()
    names = Fields.fetch_opt(opts, :names, [])

    with {:ok, horizon} <- Schema.normalize_horizon(Fields.fetch_opt(dossier, :horizon, :locked)),
         :ok <- ensure_open(horizon),
         :ok <- ensure_unsettled(Fields.fetch_opt(dossier, :settled, false)),
         {:ok, normalized_intervention} <- Schema.normalize_intervention(intervention),
         {:ok, relic} <- normalize_relic(Fields.fetch_opt(dossier, :relic, nil)),
         {:ok, baseline_fate} <-
           Schema.normalize_fate(
             Fields.fetch_opt(dossier, :baseline_fate, nil) ||
               Fields.fetch_opt(dossier, :fate, nil)
           ),
         {:ok, binding} <-
           normalize_binding(Fields.fetch_opt(dossier, :binding, nil), relic, baseline_fate),
         transitions =
           build_reachable_transitions(
             baseline_fate,
             binding.batch_id,
             binding.vessel_name,
             binding.vessel_code,
             binding.safe_tick
           ),
         {:ok, transition} <- find_transition(transitions, normalized_intervention) do
      target_fate = transition.target_fate
      target_regime = transition.target_regime

      settled_binding =
        binding
        |> Map.put(:fate, target_fate)
        |> Map.put(:settled, true)
        |> Map.put(:applied_intervention, normalized_intervention)

      hop5_edge =
        relic
        |> Pathway.edges(target_fate)
        |> Enum.find(&(&1.hop == 5))

      interpolation = interpolation(settled_binding, places(names))
      hop5_doc = document(hop5_edge, interpolation, :main, relic, target_fate, pack)

      existing_docs =
        dossier
        |> Fields.fetch_opt(:documents, [])
        |> Enum.map(&normalize_doc/1)

      main_1_to_4 =
        existing_docs
        |> Enum.filter(&(&1.role == :main and &1.hop in [1, 2, 3, 4]))
        |> Enum.sort_by(& &1.hop)

      decoy_docs = Enum.filter(existing_docs, &(&1.role == :decoy))

      settled_dossier = %{
        seed: Fields.fetch_opt(dossier, :seed, settled_binding.seed),
        branch_id: Fields.fetch_opt(dossier, :branch_id, settled_binding.branch_id),
        relic: relic,
        baseline_fate: baseline_fate,
        fate: target_fate,
        final_regime: target_regime,
        horizon: :open,
        settled: true,
        applied_intervention: normalized_intervention,
        reachable_transitions: transitions,
        binding: settled_binding,
        hop5: hop5_doc,
        documents: main_1_to_4 ++ [hop5_doc] ++ decoy_docs
      }

      {:ok, settled_dossier}
    end
  end

  defp build_reachable_transitions(fate, batch_id, vessel_name, vessel_code, safe_tick) do
    {:ok, transitions} = Chronicle.solve_interventions(fate)

    Enum.map(transitions, fn t ->
      %{
        intervention: t.intervention,
        effective_c0: t.effective_c0,
        effective_pulse: t.effective_pulse,
        target_fate: t.target_fate,
        fate: t.target_fate,
        target_regime: t.target_regime,
        final_regime: t.target_regime,
        requires_reanchor: t.intervention in [:reanchor_vessel, :reanchor_and_dampen],
        batch_id: batch_id,
        vessel_name: vessel_name,
        vessel_code: vessel_code,
        safe_tick: safe_tick
      }
    end)
  end

  defp ensure_open(:open), do: :ok
  defp ensure_open(_other), do: {:error, :dossier_locked}

  defp ensure_unsettled(false), do: :ok
  defp ensure_unsettled(_other), do: {:error, :already_settled}

  defp find_transition(transitions, intervention) do
    case Enum.find(transitions, &(&1.intervention == intervention)) do
      nil -> {:error, {:unreachable_intervention, intervention}}
      transition -> {:ok, transition}
    end
  end

  defp normalize_relic(relic) when relic in [@relic_bronze, @relic_stele], do: {:ok, relic}
  defp normalize_relic("ancestral_bronze_zheng"), do: {:ok, @relic_bronze}
  defp normalize_relic("carved_diversion_stele"), do: {:ok, @relic_stele}
  defp normalize_relic(_other), do: {:error, :unknown_relic}

  defp normalize_binding(binding, relic, fate) when is_map(binding) do
    dyn = dynamics()
    decoys = Fields.fetch_opt(binding, :decoys, %{})
    mm = Fields.fetch_opt(decoys, :material_mismatch, %{})
    dd = Fields.fetch_opt(decoys, :destination_dead_end, %{})

    {:ok,
     %{
       seed: Fields.fetch_opt(binding, :seed, nil),
       branch_id: Fields.fetch_opt(binding, :branch_id, nil),
       relic: relic,
       fate: fate,
       horizon: :open,
       settled: false,
       batch_id: Fields.fetch_opt(binding, :batch_id, nil),
       vessel_name: Fields.fetch_opt(binding, :vessel_name, nil),
       vessel_code: Fields.fetch_opt(binding, :vessel_code, nil),
       safe_tick: Fields.fetch_opt(binding, :safe_tick, nil),
       safe_phase: Fields.fetch_opt(binding, :safe_phase, nil),
       safe_delay: Fields.fetch_opt(binding, :safe_delay, nil),
       safe_window: Fields.fetch_opt(binding, :safe_window, :relative),
       decoys: %{
         material_mismatch: %{
           batch_id: Fields.fetch_opt(mm, :batch_id, ""),
           vessel_name: Fields.fetch_opt(mm, :vessel_name, ""),
           vessel_code: Fields.fetch_opt(mm, :vessel_code, ""),
           material: Fields.fetch_opt(mm, :material, nil),
           anchor: Fields.fetch_opt(mm, :anchor, nil)
         },
         destination_dead_end: %{
           batch_id: Fields.fetch_opt(dd, :batch_id, ""),
           material: Fields.fetch_opt(dd, :material, nil),
           disposition: Fields.fetch_opt(dd, :disposition, nil)
         }
       },
       dynamics: dyn
     }}
  end

  defp normalize_binding(_other, _relic, _fate), do: {:error, :invalid_binding}

  defp normalize_doc(doc) when is_map(doc) do
    role =
      case Fields.fetch_opt(doc, :role, nil) do
        r when r in [:main, :decoy] -> r
        "main" -> :main
        "decoy" -> :decoy
        other -> other
      end

    decoy_class =
      case Fields.fetch_opt(doc, :decoy_class, nil) do
        nil -> nil
        c when is_atom(c) -> c
        "material_mismatch" -> :material_mismatch
        "destination_dead_end" -> :destination_dead_end
        other -> other
      end

    %{
      hop: Fields.fetch_opt(doc, :hop, nil),
      role: role,
      doc_kind: normalize_atom_if_known(Fields.fetch_opt(doc, :doc_kind, nil)),
      lens: normalize_atom_if_known(Fields.fetch_opt(doc, :lens, nil)),
      decoy_class: decoy_class,
      title: Fields.fetch_opt(doc, :title, ""),
      body: Fields.fetch_opt(doc, :body, "")
    }
  end

  defp normalize_atom_if_known(val) when is_atom(val), do: val

  defp normalize_atom_if_known(val) when is_binary(val) do
    try do
      String.to_existing_atom(val)
    rescue
      ArgumentError -> val
    end
  end

  # ==== deterministic skolemization ==== //

  defp hash(seed, branch_id, salt, relic, fate, size) do
    :erlang.phash2({seed, branch_id, salt, relic, fate}) |> rem(size)
  end

  defp rotate(items, offset, count) do
    size = length(items)
    Enum.map(0..(count - 1), fn position -> Enum.at(items, rem(offset + position, size)) end)
  end

  # ==== local cards ==== //

  defp document(edge, interpolation, role, relic, fate, pack) do
    criteria = %{
      role: role,
      hop: edge.hop,
      relic: relic,
      fate: if(role == :main and edge.hop in [4, 5], do: fate),
      c0_status: if(role == :main and edge.hop == 2, do: edge.attrs[:c0_status]),
      decoy_class: edge.attrs[:decoy_class]
    }

    {:ok, card} = Content.card_for(criteria, pack)

    %{
      hop: edge.hop,
      role: role,
      doc_kind: edge.doc_kind,
      lens: edge.lens,
      decoy_class: edge.attrs[:decoy_class],
      title: card.title,
      body: Content.render(card.body, interpolation)
    }
  end

  defp interpolation(binding, places) do
    dynamics = binding.dynamics
    decoys = binding.decoys

    [
      {"place.up", places.up},
      {"place.pass", places.pass},
      {"place.down", places.down},
      {"batch_id", binding.batch_id},
      {"vessel_name", binding.vessel_name},
      {"vessel_code", binding.vessel_code},
      {"onset_period", decimals(dynamics.onset_period, 1)},
      {"onset_percent", dynamics.onset_percent},
      {"onset_ticks", decimals(dynamics.onset_period * dynamics.onset_fraction, 1)},
      {"settled_period", decimals(dynamics.settled_period, 1)},
      {"settled_percent", dynamics.settled_percent},
      {"settled_ticks", decimals(dynamics.settled_period * dynamics.settled_fraction, 1)},
      {"settled_delay", signed(dynamics.settled_mid_cycle_delta, 2)},
      {"safe_tick", binding.safe_tick},
      {"safe_phase", decimals(binding.safe_phase, 2)},
      {"decoy_a_batch", decoys.material_mismatch.batch_id},
      {"decoy_a_vessel_name", decoys.material_mismatch.vessel_name},
      {"decoy_a_vessel_code", decoys.material_mismatch.vessel_code},
      {"decoy_b_batch", decoys.destination_dead_end.batch_id}
    ]
  end

  defp places(names) do
    %{
      up: place(names, :up, "上游"),
      pass: place(names, :pass, "隘口"),
      down: place(names, :down, "谷地")
    }
  end

  defp place(names, key, default) when is_list(names) do
    names |> Keyword.get(key, default) |> to_string()
  end

  defp place(names, key, default) when is_map(names) do
    value = Map.get(names, key) || Map.get(names, Atom.to_string(key))
    to_string(value || default)
  end

  defp place(_names, _key, default), do: default

  defp decimals(number, places), do: :erlang.float_to_binary(number * 1.0, decimals: places)

  defp signed(number, places) do
    sign = if number < 0, do: "-", else: "+"
    sign <> decimals(abs(number), places)
  end

  defp percent(fraction), do: :erlang.float_to_binary(fraction * 100, decimals: 1) <> "%"

  # ==== contract readings ==== //

  defp dynamics do
    contract = Contract.load!()
    onset = Contract.onset_comparison(contract)
    settled = Contract.oscillator(contract)
    {:ok, collapsed} = Contract.c0_truth(contract, :c0_collapsed)
    {:ok, delayed} = Contract.outcome(contract, :alpha_shock, :relative)
    {:ok, safe_ticks} = Contract.safe_ticks(contract, :alpha_metal_resonance)

    %{
      onset_period: onset.period,
      onset_fraction: onset.plateau_fraction,
      onset_percent: percent(onset.plateau_fraction),
      onset_mid_cycle_delta: onset.mid_cycle_delta,
      settled_period: collapsed.settled_period,
      settled_fraction: settled.plateau_fraction,
      settled_percent: percent(settled.plateau_fraction),
      settled_mid_cycle_delta: delayed.delta,
      safe_ticks: safe_ticks
    }
  end

  # ==== inputs ==== //

  defp branch!(relic) do
    case Enum.find(@branches, &(&1.relic == relic)) do
      nil -> raise ArgumentError, "unknown relic: #{inspect(relic)}"
      branch -> branch
    end
  end

  defp fate!(fate) do
    case Schema.normalize_fate(fate) do
      {:ok, normalized} -> normalized
      {:error, reason} -> raise ArgumentError, "unknown fate #{inspect(fate)}: #{inspect(reason)}"
    end
  end

  defp horizon!(horizon) do
    case Schema.normalize_horizon(horizon) do
      {:ok, normalized} ->
        normalized

      {:error, reason} ->
        raise ArgumentError, "unknown horizon #{inspect(horizon)}: #{inspect(reason)}"
    end
  end
end
