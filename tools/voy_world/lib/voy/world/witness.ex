defmodule Voy.World.Witness do
  @moduledoc """
  Seeded skolemization and local-card rendering for a `Voy.World.Pathway`.

  Relations stay in `Pathway`, prose stays in `Voy.World.Content`, and this
  module binds `(world_seed, branch_id, relic, fate)` to concrete batch numbers,
  industrial carriers, and an exact metal-resonance tick measured as `Delayed`
  by `Voy.World.Contract`. Five main cards and two decoys know only their own
  hop; no global narrative summary is assembled here.
  """

  alias Voy.World.{Contract, Content, Pathway, Schema}

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
  @spec bind(integer(), term(), atom(), atom() | String.t()) :: map()
  def bind(seed, branch_id, relic, fate) do
    spec = branch!(relic)
    fate = fate!(fate)
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

    %{
      seed: seed,
      branch_id: branch_id,
      relic: relic,
      fate: fate,
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
  end

  @doc "Exact integer ticks measured as delayed for the metal-resonance pulse."
  @spec safe_ticks() :: [pos_integer()]
  def safe_ticks, do: Enum.map(dynamics().safe_ticks, & &1.tick)

  @doc "Render the five main cards and two decoys from the authoring pack."
  @spec render_dossier(integer(), term(), atom(), atom() | String.t(), term()) :: map()
  def render_dossier(seed, branch_id, relic, fate, names \\ []) do
    render_dossier(seed, branch_id, relic, fate, names, Content.default_pack())
  end

  @doc "Render a dossier with an explicit pack, allowing author edits to be tested."
  def render_dossier(seed, branch_id, relic, fate, names, %Content.Pack{} = pack) do
    binding = bind(seed, branch_id, relic, fate)
    {:ok, %{chain: chain}} = Pathway.compile_chain(relic, binding.fate)
    decoys = Pathway.decoys(relic)
    interpolation = interpolation(binding, places(names))

    main = Enum.map(chain, &document(&1, interpolation, :main, relic, binding.fate, pack))

    decoy_documents =
      Enum.map(decoys, &document(&1, interpolation, :decoy, relic, binding.fate, pack))

    %{
      seed: seed,
      branch_id: branch_id,
      relic: relic,
      fate: binding.fate,
      binding: binding,
      documents: main ++ decoy_documents
    }
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
end
