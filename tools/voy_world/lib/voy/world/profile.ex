defmodule Voy.World.Profile do
  @moduledoc """
  Numeric stock the relational layer is allowed to name.

  The dynamics layer owns the real numbers (synapse tau, cycle-window
  fractions, dose thresholds, stress cut points). This module is the
  *contract* for those numbers, not a second simulation:

    * every lag band a rule may name is declared here, with its closed
      interval;
    * a measured real is mapped onto a band through `band/3`, so a rule
      selects a branch by band name instead of comparing floats;
    * `describe/1` renders the whole stock as JSON-safe data, so an
      exported canon records which stock produced it and the Scala
      readout can be checked against the same numbers.

  Band names mirror the frozen rule vocabulary (`:tau_immediate`,
  `:tau_5`, `:tau_10`), so the existing rule tables stay valid while the
  numbers behind them become external. Swapping the profile changes which
  band a measurement lands in without editing a single rule.

  Two sections are not axes and are therefore not banded: `dose` gives a
  per-pulse acceptance range (pulse names are identities, not positions on
  a scale), and `stress` gives the cut points of the discrete regime
  partition.

  `regime/3` is the provisional partition the Scala readout must
  reproduce. It is deliberately the same shape the development HUD uses
  today (stress plus the catastrophe-activity tracer), so the two sides
  can be pinned by goldens before the port; once `MandateReadout` exists,
  that implementation becomes authoritative and this one is kept only as
  a cross-check.
  """

  @type interval :: {number(), number()}
  @type band :: atom()
  @type regime :: :Calm | :Strained | :Oscillating | :Burnout

  @type t :: %__MODULE__{
          id: String.t(),
          tau: %{band() => interval()},
          window: %{atom() => interval()},
          stress: %{atom() => float()},
          dose: %{band() => interval()}
        }

  @default_id "proto_v1"

  defstruct id: @default_id,
            tau: %{
              tau_immediate: {0.0, 1.0},
              tau_5: {4.0, 6.0},
              tau_10: {9.0, 11.0}
            },
            window: %{absolute: {0.2, 0.3}, relative: {0.4, 0.6}},
            stress: %{calm: 0.18, strained: 0.45, burnout: 0.95, oscillating_q: 0.4},
            dose: %{
              heavy_kick: {0.5, 6.0},
              alpha_shock: {1.0, 12.0},
              alpha_metal_resonance: {1.0, 12.0},
              OverwhelmingNegativeSuppression: {12.0, 40.0}
            }

  @doc "The stock the frozen rule tables were written against."
  @spec default() :: t()
  def default, do: struct!(__MODULE__, [])

  @doc "Build a validated profile from overrides (keyword list or map)."
  @spec new(Keyword.t() | map()) :: t()
  def new(overrides), do: __MODULE__ |> struct(overrides) |> validate!()

  @doc "Stable profile id, as recorded in exports and goldens."
  @spec id(t()) :: String.t()
  def id(%__MODULE__{id: id}), do: id

  @doc "Declared propagation-lag bands."
  @spec tau_bands(t()) :: %{band() => interval()}
  def tau_bands(%__MODULE__{tau: tau}), do: tau

  @doc "Declared per-pulse dose ranges."
  @spec doses(t()) :: %{band() => interval()}
  def doses(%__MODULE__{dose: dose}), do: dose

  @doc """
  Map a measured propagation lag (in ticks) onto the band whose closed
  interval contains it.

  Returns `:out_of_band` for a value in a declared gap. An ambiguous result
  can only come from an unvalidated profile: `validate/1` rejects touching
  or overlapping intervals, so every measurement has at most one band.
  """
  @spec lag_band(t(), number()) :: {:ok, band()} | :out_of_band | {:error, term()}
  def lag_band(%__MODULE__{tau: tau}, ticks) do
    if is_number(ticks) do
      matches =
        Enum.filter(tau, fn {_band, {low, high}} -> low <= ticks and ticks <= high end)

      case matches do
        [{band, _interval}] -> {:ok, band}
        [] -> :out_of_band
        many -> {:error, {:ambiguous, Enum.map(many, &elem(&1, 0))}}
      end
    else
      {:error, {:not_a_number, ticks}}
    end
  end

  @doc "Accepted dose range for one pulse identity."
  @spec dose(t(), band()) :: {:ok, interval()} | :error
  def dose(%__MODULE__{dose: dose}, pulse), do: Map.fetch(dose, pulse)

  @doc "Whether a measured pulse area lies inside the accepted range."
  @spec within_dose?(t(), band(), number()) :: boolean()
  def within_dose?(%__MODULE__{dose: dose}, pulse, area) do
    case Map.fetch(dose, pulse) do
      {:ok, {low, high}} when is_number(area) -> low <= area and area <= high
      _ -> false
    end
  end

  @doc "Interval of a named phase window, as a fraction of the cycle."
  @spec window(t(), atom()) :: {:ok, interval()} | :error
  def window(%__MODULE__{window: windows}, name), do: Map.fetch(windows, name)

  @doc "Whether a cycle fraction falls inside a named phase window."
  @spec in_window?(t(), atom(), number()) :: boolean()
  def in_window?(%__MODULE__{window: windows}, name, fraction) do
    case Map.fetch(windows, name) do
      {:ok, {low, high}} when is_number(fraction) -> low <= fraction and fraction <= high
      _ -> false
    end
  end

  @doc """
  Reference stress-regime projection: the discrete partition of the slow
  variable (plus the catastrophe-activity tracer) into the rule vocabulary.

  Burnout is a saturation corner (the state is clamped at the stress
  ceiling), so it wins over oscillation; otherwise the activity tracer
  decides self-sustained oscillation before the stress level decides mere
  strain.
  """
  @spec regime(t(), number(), number()) :: regime()
  def regime(%__MODULE__{stress: cut}, stress, activity) do
    cond do
      stress >= cut.burnout -> :Burnout
      activity >= cut.oscillating_q -> :Oscillating
      stress >= cut.strained -> :Strained
      true -> :Calm
    end
  end

  @doc "Whether the cell still sits inside the tolerance basin (c0 covered)."
  @spec within_tolerance?(t(), number(), number()) :: boolean()
  def within_tolerance?(%__MODULE__{stress: cut}, stress, activity) do
    stress <= cut.calm and activity < cut.oscillating_q
  end

  @doc "JSON-safe description of the whole stock."
  @spec describe(t()) :: map()
  def describe(%__MODULE__{} = profile) do
    %{
      "id" => profile.id,
      "tau" => describe_intervals(profile.tau),
      "window" => describe_intervals(profile.window),
      "stress" => Map.new(profile.stress, fn {key, value} -> {Atom.to_string(key), value} end),
      "dose" => describe_intervals(profile.dose)
    }
  end

  @doc "Rebuild a profile from `describe/1` output (string or atom keys)."
  @spec from_map(map()) :: {:ok, t()} | {:error, term()}
  def from_map(map) when is_map(map) do
    with {:ok, id} <- fetch(map, :id),
         {:ok, tau} <- fetch_intervals(map, :tau),
         {:ok, window} <- fetch_intervals(map, :window),
         {:ok, stress} <- fetch_intervals(map, :stress),
         {:ok, dose} <- fetch_intervals(map, :dose) do
      profile = %__MODULE__{id: id, tau: tau, window: window, stress: stress, dose: dose}

      case validate(profile) do
        :ok -> {:ok, profile}
        {:error, _reason} = error -> error
      end
    end
  end

  def from_map(other), do: {:error, {:not_a_profile_map, other}}

  @doc false
  @spec validate(t()) :: :ok | {:error, term()}
  def validate(%__MODULE__{} = profile) do
    with :ok <- check_id(profile.id),
         :ok <- check_bands(profile.tau, :tau),
         :ok <- check_intervals(profile.dose, :dose),
         :ok <- check_windows(profile.window),
         :ok <- check_stress(profile.stress) do
      :ok
    end
  end

  def validate(other), do: {:error, {:not_a_profile, other}}

  @doc false
  @spec validate!(t()) :: t()
  def validate!(%__MODULE__{} = profile) do
    case validate(profile) do
      :ok ->
        profile

      {:error, reason} ->
        raise ArgumentError, "invalid profile #{inspect(profile.id)}: #{inspect(reason)}"
    end
  end

  defp describe_intervals(intervals) do
    Map.new(intervals, fn {band, {low, high}} ->
      {Atom.to_string(band), %{"lo" => low, "hi" => high}}
    end)
  end

  defp check_id(id) when is_binary(id) and id != "", do: :ok
  defp check_id(id), do: {:error, {:invalid_id, id}}

  # A banded axis must be totally partitioned by disjoint intervals.
  defp check_bands(tau, name) do
    with :ok <- check_intervals(tau, name) do
      check_separated(tau, name)
    end
  end

  defp check_intervals(intervals, name) when is_map(intervals) and map_size(intervals) > 0 do
    Enum.reduce_while(intervals, :ok, fn {band, interval}, :ok ->
      case check_interval(interval) do
        :ok -> {:cont, :ok}
        {:error, reason} -> {:halt, {:error, {name, band, reason}}}
      end
    end)
  end

  defp check_intervals(intervals, name), do: {:error, {name, :empty_family, intervals}}

  defp check_interval({low, high}) when is_number(low) and is_number(high) and low < high, do: :ok
  defp check_interval(interval), do: {:error, {:invalid_interval, interval}}

  # Closed intervals must not touch, so every measurement lands in exactly
  # one band.
  defp check_separated(intervals, name) do
    pairs = Map.to_list(intervals)

    overlaps =
      for {band_a, {low_a, high_a}} <- pairs,
          {band_b, {low_b, high_b}} <- pairs,
          band_a < band_b,
          not (high_a < low_b or high_b < low_a),
          do: {band_a, band_b}

    case overlaps do
      [] -> :ok
      pairs -> {:error, {name, :touching_or_overlapping, pairs}}
    end
  end

  defp check_windows(windows) when is_map(windows) do
    with :ok <- check_required_windows(windows),
         :ok <- check_intervals(windows, :window),
         :ok <- check_window_bounds(windows),
         :ok <- check_separated(windows, :window) do
      :ok
    end
  end

  defp check_windows(windows), do: {:error, {:window, :not_a_map, windows}}

  defp check_required_windows(windows) do
    missing = Enum.reject([:absolute, :relative], &Map.has_key?(windows, &1))

    case missing do
      [] -> :ok
      keys -> {:error, {:window, :missing, keys}}
    end
  end

  defp check_window_bounds(windows) do
    Enum.reduce_while(windows, :ok, fn {name, {low, high}}, :ok ->
      cond do
        low >= 0 and high <= 1 -> {:cont, :ok}
        true -> {:halt, {:error, {:window, name, {:outside_cycle, {low, high}}}}}
      end
    end)
  end

  defp check_stress(cut) when is_map(cut) do
    with :ok <- check_stress_keys(cut) do
      check_stress_order(cut)
    end
  end

  defp check_stress(cut), do: {:error, {:stress, :not_a_map, cut}}

  defp check_stress_keys(cut) do
    missing = Enum.reject([:calm, :strained, :burnout, :oscillating_q], &Map.has_key?(cut, &1))

    case missing do
      [] -> :ok
      keys -> {:error, {:stress, :missing, keys}}
    end
  end

  defp check_stress_order(cut) do
    calm = cut.calm
    strained = cut.strained
    burnout = cut.burnout
    activity = cut.oscillating_q

    cond do
      not Enum.all?([calm, strained, burnout, activity], &is_number/1) ->
        {:error, {:stress, :not_numeric, cut}}

      not (0 <= calm and calm < strained and strained < burnout and burnout <= 1) ->
        {:error, {:stress, :out_of_order, cut}}

      not (activity > 0 and activity < 1) ->
        {:error, {:stress, :bad_activity_threshold, activity}}

      true ->
        :ok
    end
  end

  defp fetch(map, key) do
    case Map.fetch(map, key) do
      {:ok, value} -> {:ok, value}
      :error -> Map.fetch(map, Atom.to_string(key))
    end
  end

  defp fetch_intervals(map, key) do
    with {:ok, family} <- fetch(map, key) do
      {:ok, normalize_intervals(family)}
    end
  end

  defp normalize_intervals(family) when is_map(family) do
    Map.new(family, fn {band, interval} ->
      {to_band(band), normalize_interval(interval)}
    end)
  end

  defp normalize_intervals(other), do: other

  defp normalize_interval({low, high}), do: {low, high}
  defp normalize_interval(%{"lo" => low, "hi" => high}), do: {low, high}
  defp normalize_interval(%{lo: low, hi: high}), do: {low, high}
  defp normalize_interval(other), do: other

  defp to_band(name) when is_atom(name), do: name

  defp to_band(name) when is_binary(name) do
    String.to_existing_atom(name)
  rescue
    ArgumentError -> name
  end
end
