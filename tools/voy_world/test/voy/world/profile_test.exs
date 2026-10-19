defmodule Voy.World.ProfileTest do
  use ExUnit.Case, async: true
  use Voy.Kanren

  alias Voy.World.{Causality, Dynamics, Profile}

  test "the default stock reproduces the frozen lag vocabulary" do
    profile = Profile.default()

    assert :ok = Profile.validate(profile)
    assert Profile.id(profile) == "proto_v1"

    assert profile |> Profile.tau_bands() |> Map.keys() |> Enum.sort() ==
             [:tau_10, :tau_5, :tau_immediate]

    # Closed intervals: both bounds belong to the band.
    assert {:ok, :tau_5} = Profile.lag_band(profile, 4.0)
    assert {:ok, :tau_5} = Profile.lag_band(profile, 5.0)
    assert {:ok, :tau_5} = Profile.lag_band(profile, 6.0)
    assert {:ok, :tau_10} = Profile.lag_band(profile, 10.0)
    assert {:ok, :tau_immediate} = Profile.lag_band(profile, 0.5)

    # The declared gaps are deliberate: an unmeasured latency is not a band.
    assert :out_of_band = Profile.lag_band(profile, 7.5)
    assert {:error, {:not_a_number, "5"}} = Profile.lag_band(profile, "5")
  end

  test "a rescaled stock moves the same measurement into another band" do
    default = Profile.default()

    shifted =
      Profile.new(
        id: "shifted_v1",
        tau: %{tau_immediate: {0.0, 0.5}, tau_5: {1.0, 2.0}, tau_10: {4.0, 6.0}}
      )

    assert {:ok, :tau_5} = Profile.lag_band(default, 5.0)
    assert {:ok, :tau_10} = Profile.lag_band(shifted, 5.0)

    # Same rule table, different stock, different selected branch.
    assert {:ok, tau_5_states} = next_states(:tau_5)
    assert :premature_excitation_spike in tau_5_states

    assert {:ok, tau_10_states} = next_states(:tau_10)
    refute :premature_excitation_spike in tau_10_states
  end

  test "the rule tables may not name a band the stock does not declare" do
    declared =
      Profile.default()
      |> Profile.tau_bands()
      |> Map.keys()
      |> MapSet.new()

    used =
      run_all(fn query ->
        fresh [c0, regime, phase, pulse, next, lag] do
          Dynamics.phase_transitiono(c0, regime, phase, pulse, next, lag)
          eq(query, lag)
        end
      end)
      |> terms()
      |> MapSet.new()

    assert used == declared

    doses = Profile.default() |> Profile.doses()

    pulses =
      run_all(fn query ->
        fresh [disturbance, pulse] do
          Dynamics.disturbance_pulseo(disturbance, pulse)
          eq(query, pulse)
        end
      end)
      |> terms()

    artifact_pulses =
      run_all(fn query ->
        fresh [org, c0, actor, role, item, pulse] do
          Causality.artifact_actiono(org, c0, actor, role, item)
          propso(item, pulse: pulse)
          eq(query, pulse)
        end
      end)
      |> terms()

    for pulse <- pulses ++ artifact_pulses do
      assert Map.has_key?(doses, pulse), "stock declares no dose range for #{inspect(pulse)}"
    end

    profile = Profile.default()
    assert Profile.within_dose?(profile, :alpha_shock, 5.0)
    refute Profile.within_dose?(profile, :alpha_shock, 50.0)
    refute Profile.within_dose?(profile, :unknown_pulse, 5.0)
  end

  test "the regime projection only emits the rule vocabulary" do
    profile = Profile.default()

    assert Profile.regime(profile, 0.0, 0.0) == :Calm
    assert Profile.regime(profile, 0.5, 0.0) == :Strained
    assert Profile.regime(profile, 0.5, 0.9) == :Oscillating
    assert Profile.regime(profile, 0.99, 0.0) == :Burnout
    # Burnout is a saturation corner and wins over the activity tracer.
    assert Profile.regime(profile, 0.99, 0.9) == :Burnout

    assert Profile.within_tolerance?(profile, 0.1, 0.0)
    refute Profile.within_tolerance?(profile, 0.1, 0.9)

    for stress <- [0.0, 0.2, 0.5, 0.99], activity <- [0.0, 0.5, 1.0] do
      assert Profile.regime(profile, stress, activity) in Dynamics.regimes()
    end
  end

  test "windows are fractions of the cycle and must not overlap" do
    profile = Profile.default()

    assert Profile.in_window?(profile, :absolute, 0.25)
    refute Profile.in_window?(profile, :absolute, 0.35)
    assert Profile.in_window?(profile, :relative, 0.5)
    refute Profile.in_window?(profile, :relative, 0.1)
    refute Profile.in_window?(profile, :unknown, 0.5)
    assert {:ok, {0.2, 0.3}} = Profile.window(profile, :absolute)

    assert {:error, {:window, :touching_or_overlapping, _}} =
             Profile.validate(%{profile | window: %{absolute: {0.2, 0.5}, relative: {0.4, 0.6}}})

    assert {:error, {:window, :missing, [:relative]}} =
             Profile.validate(%{profile | window: %{absolute: {0.2, 0.3}}})
  end

  test "describe/from_map round-trips the stock through JSON" do
    profile =
      Profile.new(
        id: "shifted_v1",
        tau: %{tau_immediate: {0.0, 0.5}, tau_5: {1.0, 2.0}, tau_10: {4.0, 6.0}}
      )

    described = Profile.describe(profile)
    assert {:ok, restored} = Profile.from_map(described)
    assert Profile.describe(restored) == described

    decoded = described |> JSON.encode!() |> JSON.decode!()
    assert {:ok, from_json} = Profile.from_map(decoded)
    assert Profile.describe(from_json) == described
    assert {:ok, :tau_10} = Profile.lag_band(from_json, 5.0)
  end

  test "an invalid stock is rejected" do
    assert_raise ArgumentError, fn -> Profile.new(id: "") end
    assert_raise ArgumentError, fn -> Profile.new(tau: %{broken: {2.0, 1.0}}) end

    assert {:error, {:tau, :touching_or_overlapping, _}} =
             Profile.validate(%{
               Profile.default()
               | tau: %{
                   tau_immediate: {0.0, 1.0},
                   tau_5: {1.0, 6.0},
                   tau_10: {9.0, 11.0}
                 }
             })

    assert {:error, {:stress, :out_of_order, _}} =
             Profile.validate(%{
               Profile.default()
               | stress: %{calm: 0.5, strained: 0.2, burnout: 0.95, oscillating_q: 0.4}
             })

    assert {:error, {:not_a_profile, :nope}} = Profile.validate(:nope)
    assert {:error, {:not_a_profile_map, :nope}} = Profile.from_map(:nope)
  end

  defp next_states(lag) do
    states =
      run_all(fn query ->
        fresh [c0, regime, phase, pulse, next] do
          Dynamics.phase_transitiono(c0, regime, phase, pulse, next, lag)
          eq(query, next)
        end
      end)
      |> terms()
      |> Enum.uniq()

    case states do
      [] -> :error
      states -> {:ok, states}
    end
  end
end
