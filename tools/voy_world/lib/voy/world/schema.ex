defmodule Voy.World.Schema do
  @moduledoc "Canonical topography attributes, domain vocabularies, fate metadata, and JSON-safe conversion."

  alias Voy.Kanren.ReifiedVar
  alias Voy.World.Fields

  @topology_keys [
    :branch_id,
    :up_name,
    :pass_name,
    :down_name,
    :up_terrain,
    :pass_terrain,
    :down_terrain,
    :up_geology,
    :accessibility
  ]
  @feature_keys [:up_terrain, :pass_terrain, :down_terrain, :up_geology, :accessibility]

  @feature_domains %{
    up_terrain: [:steep_fissure_headwater, :karst_sinkhole_headwater],
    pass_terrain: [:canyon_pass, :karst_gorge],
    down_terrain: [:alluvial_flat, :mid_terrace],
    up_geology: [:active_fault_vein, :deep_stress_cavity, :minor_fracture],
    accessibility: [:tractor_road_accessible, :cliff_isolated]
  }

  @relics [:ancestral_bronze_zheng, :carved_diversion_stele]
  @c0_states [:c0_collapsed, :c0_intact]
  @pulses [:alpha_shock, :OverwhelmingNegativeSuppression, :heavy_kick]

  @fates [
    :Fate_Assimilated_NewZhai,
    :Fate_Annihilated_BurnoutZone,
    :Fate_Ascended_SpatialRift,
    :Fate_Ancestral_ControlGroup,
    :Fate_Deferred_ChronicSeep,
    :Fate_Tolerated_FalseAlarm
  ]

  @fate_specs %{
    Fate_Assimilated_NewZhai: %{
      c0_status: :c0_collapsed,
      pulse: :alpha_shock,
      regime: :Oscillating,
      governance: :brigade_elder_joint_council,
      hop2_relation: :requisitioned_as_scrap,
      hop3_relation: :cast_into_industrial_vessel,
      hop4_relation: :duty_cycle_inversion_diagnosed,
      hop5_relation: :enacted_as_survival_ritual
    },
    Fate_Annihilated_BurnoutZone: %{
      c0_status: :c0_collapsed,
      pulse: :OverwhelmingNegativeSuppression,
      regime: :Burnout,
      governance: :depopulated_concrete_sealed_zone,
      hop2_relation: :requisitioned_as_scrap,
      hop3_relation: :cast_into_industrial_vessel,
      hop4_relation: :overwhelming_grout_burnout_diagnosed,
      hop5_relation: :enacted_as_concrete_quarantine
    },
    Fate_Deferred_ChronicSeep: %{
      c0_status: :c0_collapsed,
      pulse: :heavy_kick,
      regime: :Strained,
      governance: :deferred_evacuation_watchpost,
      hop2_relation: :requisitioned_as_scrap,
      hop3_relation: :cast_into_industrial_vessel,
      hop4_relation: :subthreshold_chronic_seep_diagnosed,
      hop5_relation: :enacted_as_watchpost_ledger_rule
    },
    Fate_Ancestral_ControlGroup: %{
      c0_status: :c0_intact,
      pulse: :heavy_kick,
      regime: :Calm,
      governance: :restored_ancestral_compact,
      hop2_relation: :catalogued_in_situ_custody,
      hop3_relation: :coupled_into_auxiliary_vessel,
      hop4_relation: :intact_basin_control_diagnosed,
      hop5_relation: :enacted_as_ancestral_compact
    },
    Fate_Tolerated_FalseAlarm: %{
      c0_status: :c0_intact,
      pulse: :alpha_shock,
      regime: :Calm,
      governance: :cliff_observation_hermitage,
      hop2_relation: :catalogued_in_situ_custody,
      hop3_relation: :coupled_into_auxiliary_vessel,
      hop4_relation: :forty_seven_false_alarms_diagnosed,
      hop5_relation: :enacted_as_hermitage_watch_rule
    },
    Fate_Ascended_SpatialRift: %{
      c0_status: :c0_intact,
      pulse: :OverwhelmingNegativeSuppression,
      regime: :Strained,
      governance: :rift_guarding_cliff_compact,
      hop2_relation: :catalogued_in_situ_custody,
      hop3_relation: :coupled_into_auxiliary_vessel,
      hop4_relation: :excitation_zenith_rift_diagnosed,
      hop5_relation: :enacted_as_rift_fire_oath
    }
  }

  @horizons [:locked, :open]

  @interventions [
    :no_intervention,
    :reanchor_vessel,
    :dampen_headwater,
    :reanchor_and_dampen
  ]

  @regimes [:Calm, :Strained, :Oscillating, :Burnout]

  def topology_keys, do: @topology_keys
  def feature_keys, do: @feature_keys
  def feature_domain(key), do: Map.fetch!(@feature_domains, key)
  def relics, do: @relics
  def c0_states, do: @c0_states
  def pulses, do: @pulses
  def fates, do: @fates
  def fate_specs, do: @fate_specs
  def horizons, do: @horizons
  def interventions, do: @interventions
  def regimes, do: @regimes

  def fate_spec(fate) do
    case normalize_fate(fate) do
      {:ok, normalized} ->
        Map.fetch!(@fate_specs, normalized)

      {:error, reason} ->
        raise ArgumentError, "unknown fate #{inspect(fate)}: #{inspect(reason)}"
    end
  end

  def normalize_fate(fate), do: normalize_enum(fate, @fates, :unknown_fate)
  def normalize_relic(relic), do: normalize_enum(relic, @relics, :unknown_relic)
  def normalize_c0(c0), do: normalize_enum(c0, @c0_states, :invalid_c0_status)
  def normalize_pulse(pulse), do: normalize_enum(pulse, @pulses, :invalid_pulse)
  def normalize_horizon(horizon), do: normalize_enum(horizon, @horizons, :invalid_horizon)

  def normalize_intervention(intervention),
    do: normalize_enum(intervention, @interventions, :unknown_intervention)

  def normalize_regime(regime), do: normalize_enum(regime, @regimes, :unknown_regime)

  defp normalize_enum(value, allowed, error) do
    cond do
      value in allowed ->
        {:ok, value}

      is_binary(value) ->
        case Enum.find(allowed, &(Atom.to_string(&1) == value)) do
          nil -> {:error, error}
          normalized -> {:ok, normalized}
        end

      true ->
        {:error, error}
    end
  end

  def topology(fields) do
    Enum.map(@topology_keys, fn key ->
      {key, normalize(key, Fields.fetch!(fields, key))}
    end)
  end

  def features(fields) do
    Enum.flat_map(@feature_keys, fn key ->
      case Fields.fetch(fields, key) do
        {:ok, value} -> [{key, normalize(key, value)}]
        :error -> []
      end
    end)
  end

  def complete_features(fields) do
    Enum.map(@feature_keys, fn key ->
      {key, normalize(key, Fields.fetch!(fields, key))}
    end)
  end

  defp normalize(key, value) when key in @feature_keys and is_binary(value) do
    Enum.find(feature_domain(key), &(Atom.to_string(&1) == value)) ||
      raise ArgumentError, "unknown #{key}: #{value}"
  end

  defp normalize(_key, value), do: value

  def to_public(%ReifiedVar{id: id}), do: %{"_var" => id}
  def to_public(nil), do: nil
  def to_public(term) when is_boolean(term), do: term
  def to_public([]), do: []

  def to_public(term) when is_list(term) do
    if Keyword.keyword?(term) do
      Map.new(term, fn {key, value} -> {public_key(key), to_public(value)} end)
    else
      Enum.map(term, &to_public/1)
    end
  end

  def to_public(term) when is_tuple(term) do
    term |> Tuple.to_list() |> Enum.map(&to_public/1)
  end

  def to_public(term) when is_map(term) do
    Map.new(term, fn {key, value} -> {public_key(key), to_public(value)} end)
  end

  def to_public(term) when is_atom(term), do: Atom.to_string(term)
  def to_public(term), do: term

  defp public_key(key) when is_atom(key), do: Atom.to_string(key)
  defp public_key(key), do: key
end
