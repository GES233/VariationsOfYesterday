defmodule Voy.World.Slice do
  @moduledoc """
  The one node of the vertical slice, as a trace the numeric layer settled.

  A trace is the *hidden* explanation (`c0_state`, `pulse_kind`,
  `safe_phase_window`) together with the measured consequences of it: how
  many genuine excursions the season held, what the pulse did to the surge
  that followed it, and when the rain came. Every number is read out of
  `Voy.World.Contract`; nothing here is authored prose, and nothing here is
  a claim about what any institution *said*.

  The three lenses in `Voy.World.Observation` see only this trace, and the
  abducer in `Voy.World.Ascribe` has to recover the hidden part from what
  they recorded.
  """

  alias Voy.World.Contract

  @rain_lag 60.0
  @sites "branch_01_baishui"

  @type hidden :: %{
          c0_state: atom(),
          pulse_kind: atom(),
          safe_phase_window: atom()
        }

  @doc "The declared hidden-explanation space H."
  @spec hypotheses(Contract.t()) :: [hidden()]
  def hypotheses(%Contract{} = contract) do
    for c0 <- [:c0_collapsed, :c0_intact],
        kind <- Contract.world_pulses(contract),
        window <- [:absolute, :relative, :late] do
      %{c0_state: c0, pulse_kind: kind, safe_phase_window: window}
    end
  end

  @doc "Build the underlying trace for one hypothesis. Raises on an unknown one."
  @spec trace(hidden(), Contract.t()) :: map()
  def trace(hidden, %Contract{} = contract) do
    with {:ok, truth} <- Contract.c0_truth(contract, hidden.c0_state),
         {:ok, pulse} <- Contract.pulse(contract, hidden.pulse_kind),
         {:ok, outcome} <- Contract.outcome(contract, hidden.pulse_kind, hidden.safe_phase_window) do
      %{
        node: @sites,
        site: @sites <> "/up",
        hidden: hidden,
        # --- what the integrator did ---
        # `settled_period` is the cell's own steady-state cycle period (nil when
        # c0_intact sits in the resting basin with a single transient excursion);
        # `period` / `reference_period` is the valley's reference tide period T_ref
        # against which the survey rig's perturbed surge lag (T_ref + delta) is timed.
        period: Contract.period(contract),
        reference_period: Contract.period(contract),
        settled_period: truth.settled_period,
        oscillating?: truth.settled_period != nil,
        shift: outcome.delta,
        outcome: outcome.outcome,
        impacts: truth.impacts,
        stress: truth.mean_stress,
        frame_slams: pulse.taps,
        direction: if(pulse.area < 0, do: :negative, else: :positive),
        shape: pulse.shape,
        # --- a declared world fact, identical in every hypothesis ---
        rain_lag: @rain_lag,
        contract: contract
      }
    else
      :error -> raise ArgumentError, "unknown hypothesis: #{inspect(hidden)}"
    end
  end

  @doc "The interval from the work order to the next genuine surge."
  @spec surge_lag(map()) :: number()
  def surge_lag(%{period: period, shift: shift}), do: period + shift

  @doc "Whether the surge beat the rain, which the phenology records as order."
  @spec signs_before_rain?(map()) :: boolean()
  def signs_before_rain?(%{period: period, shift: shift, rain_lag: rain}), do: period + shift < rain
end
