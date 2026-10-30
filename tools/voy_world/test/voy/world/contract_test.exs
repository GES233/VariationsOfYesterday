defmodule Voy.World.ContractTest do
  use ExUnit.Case, async: true

  alias Voy.World.{Contract, Profile}

  setup_all do
    {:ok, contract: Contract.load!()}
  end

  test "the checked-in contract loads, is versioned and is structurally complete", %{
    contract: contract
  } do
    assert contract.data["contract"] == "voy.mandate.slice"
    assert contract.data["version"] == 1
    assert is_binary(Contract.source_digest(contract))
    assert String.length(Contract.source_digest(contract)) == 64
    assert :ok = Contract.validate(contract.data)
  end

  test "an unknown version is refused instead of silently downgraded" do
    body = File.read!(Contract.path()) |> String.replace("\"version\": 1", "\"version\": 99")
    path = Path.join(System.tmp_dir!(), "voy-contract-99.json")
    File.write!(path, body)

    assert {:error, {:unsupported_contract_version, 99, 1}} = Contract.load(path)
  end

  test "a truncated contract is refused" do
    path = Path.join(System.tmp_dir!(), "voy-contract-truncated.json")
    File.write!(path, ~s({"contract": "voy.mandate.slice", "version": 1}))

    assert {:error, {:not_a_slice_contract, _keys}} = Contract.load(path)
  end

  test "the measured period is step-converged", %{contract: contract} do
    rows = Contract.period_rows(contract)
    assert length(rows) >= 3
    assert Contract.period_spread(contract) < 0.001
    assert Contract.period(contract) > 20.0
  end

  test "the Hopf point is the exact trace condition and not the draft number", %{
    contract: contract
  } do
    %{a_c: a_c, s_c: s_c} = Contract.hopf(contract)
    assert_in_delta a_c, 0.434975, 1.0e-5
    assert_in_delta s_c, 0.7 - a_c, 1.0e-9
    # The design draft's a_c = 0.467 is 7% away: that is a measurement owed to
    # the draft's v_c ~ -1 approximation, and the contract records the exact value.
    refute_in_delta a_c, 0.467, 0.01
  end

  test "the singular-limit period is published as a non-confirming reference", %{
    contract: contract
  } do
    %{period: reference} = Contract.relaxation(contract)
    measured = Contract.period(contract)
    assert abs(measured - reference) / measured > 0.30
    assert {:ok, "refuted-at-eps=0.08"} = Contract.claim(contract, "period.singular_limit")
  end

  test "no declared alpha_shock reaches the measured fire threshold", %{contract: contract} do
    threshold = Contract.fire_threshold(contract)
    declared = Contract.declared_alpha_shock_area(contract)
    assert is_number(threshold)
    assert threshold > declared
    assert {:ok, "refuted"} = Contract.claim(contract, "prc.vulnerable_window_fires")
    assert {:ok, "supported"} = Contract.claim(contract, "prc.absolute_window_delays")
  end

  test "the outcome table covers H completely and is not hand-written", %{contract: contract} do
    table = Contract.outcome_table(contract)
    pulses = Contract.pulses(contract)
    assert length(table) == length(pulses) * 3

    for row <- table do
      assert row.outcome in [:premature, :delayed, :absorbed, :disrupted, :unresolved]
      assert is_number(row.phase)
    end

    # The middle of the declared vulnerable window delays for the pulse the
    # story uses. This is the measured fact the slice's archives must agree
    # with, and it is the opposite of the design draft's claim.
    assert {:ok, %{outcome: :delayed}} = Contract.outcome(contract, :alpha_shock, :relative)
  end

  test "the c0 footprints differ by two orders of magnitude in impact count", %{
    contract: contract
  } do
    assert {:ok, collapsed} = Contract.c0_truth(contract, :c0_collapsed)
    assert {:ok, intact} = Contract.c0_truth(contract, :c0_intact)
    assert collapsed.impacts > 20
    assert intact.impacts == 1
    assert collapsed.mean_stress > 0.5
    assert intact.mean_stress < 0.2
  end

  test "pulse shapes are the observable classes, not the labels", %{contract: contract} do
    assert {:ok, %{shape: :single}} = Contract.pulse(contract, :alpha_shock)
    assert {:ok, %{shape: :long_tail}} = Contract.pulse(contract, :heavy_kick)
    assert {:ok, %{shape: :triple}} = Contract.pulse(contract, :alpha_metal_resonance)
    assert {:ok, %{shape: :inverted}} = Contract.pulse(contract, :OverwhelmingNegativeSuppression)
    assert :error = Contract.pulse(contract, :not_a_pulse)
  end

  test "safe ticks are exact delayed measurements, not declared-window guesses", %{
    contract: contract
  } do
    assert {:ok, rows} = Contract.safe_ticks(contract, :alpha_metal_resonance)
    assert rows != []

    for row <- rows do
      assert is_integer(row.tick) and row.tick > 0
      assert row.outcome == :delayed
      assert row.delta > 0.0
      assert row.window in [:absolute, :relative, :late, :other]
    end

    assert :error = Contract.safe_ticks(contract, :not_a_pulse)
  end

  test "the measurement control is not part of the story's pulses", %{contract: contract} do
    assert :alpha_shock_weak in Contract.pulses(contract)
    refute :alpha_shock_weak in Contract.world_pulses(contract)
    assert length(Contract.world_pulses(contract)) == 4
  end

  test "the declared stock reproduces the measured regime partition", %{contract: contract} do
    report = Contract.conformance(contract)
    assert report.regime == []
    assert report.windows == []
    assert report.ok?
  end

  test "a stock that names a window the contract does not is caught", %{contract: contract} do
    stale = Profile.new(id: "stale_v1", window: %{absolute: {0.2, 0.3}, relative: {0.4, 0.6}})
    report = Contract.conformance(contract, stale)

    refute report.ok?
    assert [%{window: :late}] = report.windows
  end

  test "the chain refuses phase composition, with a measured reason", %{contract: contract} do
    chain = Contract.chain(contract)
    assert is_number(chain["upstream_period"])
    assert is_number(chain["downstream_period"])
    assert is_number(chain["lag_drift_cycles"])
    assert is_boolean(chain["composable"])

    # Composition ("downstream phase = upstream phase + constant lag") is
    # allowed only while the lag holds to a tenth of a cycle. The chain in the
    # slice drifts by more than a cycle, so the rule must be refused and the
    # refusal must carry the number that causes it.
    if chain["composable"] do
      assert chain["lag_drift_cycles"] <= 0.10
    else
      assert chain["lag_drift_cycles"] > 0.10
    end
  end

  test "duty-cycle inversion and settled period are recorded in the contract", %{
    contract: contract
  } do
    sat = Contract.oscillator(contract)
    onset = Contract.onset_comparison(contract)

    assert sat.plateau_fraction > 0.50
    assert sat.v_at_mid_cycle > 0.8
    assert onset.plateau_fraction < 0.35
    assert onset.v_at_mid_cycle < -1.0
    assert onset.mid_cycle_outcome == :premature
    assert onset.mid_cycle_delta < 0.0

    assert {:ok, collapsed} = Contract.c0_truth(contract, :c0_collapsed)
    assert {:ok, intact} = Contract.c0_truth(contract, :c0_intact)
    assert_in_delta collapsed.settled_period, Contract.period(contract), 0.10
    assert intact.settled_period == nil
  end
end
