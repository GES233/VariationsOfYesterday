defmodule Voy.World.Dynamics do
  @moduledoc "Shared regime vocabulary and historical disturbance identities."

  use Voy.Kanren

  @regimes [:Calm, :Strained, :Oscillating, :Burnout]
  @phases [:Resting, :Excitation, :AbsoluteRefractory, :RelativeRefractory]

  def regimes, do: @regimes
  def phases, do: @phases

  @disturbance_pulses [
    [disturbance: :periodic_shallow_blasting, pulse: :alpha_shock],
    [disturbance: :overwhelming_reverse_suppression, pulse: :OverwhelmingNegativeSuppression],
    [disturbance: :low_level_logging_shock, pulse: :heavy_kick]
  ]

  def disturbance_pulseo(disturbance, pulse) do
    tableo(@disturbance_pulses, disturbance: disturbance, pulse: pulse)
  end
end
