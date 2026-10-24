defmodule Voy.World.Schema do
  @moduledoc "Canonical topography attributes and JSON-safe boundary conversion."

  alias Voy.Kanren.ReifiedVar

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

  @fates [
    :Fate_Assimilated_NewZhai,
    :Fate_Annihilated_BurnoutZone,
    :Fate_Ascended_SpatialRift,
    :Fate_Ancestral_ControlGroup,
    :Fate_Deferred_ChronicSeep,
    :Fate_Tolerated_FalseAlarm
  ]

  def topology_keys, do: @topology_keys
  def feature_keys, do: @feature_keys
  def feature_domain(key), do: Map.fetch!(@feature_domains, key)
  def fates, do: @fates

  def normalize_fate(fate) when fate in @fates, do: {:ok, fate}

  def normalize_fate(fate) when is_binary(fate) do
    case Enum.find(@fates, &(Atom.to_string(&1) == fate)) do
      nil -> {:error, :unknown_fate}
      normalized -> {:ok, normalized}
    end
  end

  def normalize_fate(_fate), do: {:error, :unknown_fate}

  def topology(fields) do
    Enum.map(@topology_keys, fn key ->
      {key, normalize(key, fetch_field!(fields, key))}
    end)
  end

  def features(fields) do
    Enum.flat_map(@feature_keys, fn key ->
      case fetch_field(fields, key) do
        {:ok, value} -> [{key, normalize(key, value)}]
        :error -> []
      end
    end)
  end

  def complete_features(fields) do
    Enum.map(@feature_keys, fn key ->
      {key, normalize(key, fetch_field!(fields, key))}
    end)
  end

  defp fetch_field(fields, key) when is_list(fields), do: Keyword.fetch(fields, key)

  defp fetch_field(fields, key) when is_map(fields) do
    case Map.fetch(fields, key) do
      {:ok, value} -> {:ok, value}
      :error -> Map.fetch(fields, Atom.to_string(key))
    end
  end

  defp fetch_field!(fields, key) do
    case fetch_field(fields, key) do
      {:ok, value} -> value
      :error -> raise KeyError, key: key, term: fields
    end
  end

  defp normalize(key, value) when key in @feature_keys and is_binary(value) do
    Enum.find(feature_domain(key), &(Atom.to_string(&1) == value)) ||
      raise ArgumentError, "unknown #{key}: #{value}"
  end

  defp normalize(_key, value), do: value

  def to_public(%ReifiedVar{id: id}), do: %{"_var" => id}
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
