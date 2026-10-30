defmodule Voy.World.ObservationTest do
  use ExUnit.Case, async: true

  alias Voy.World.{Contract, Observation, Slice}

  setup_all do
    {:ok, contract: Contract.load!()}
  end

  defp trace(contract) do
    Slice.trace(
      %{c0_state: :c0_collapsed, pulse_kind: :alpha_shock, safe_phase_window: :relative},
      contract
    )
  end

  test "the three institutions are three measurement procedures", %{contract: contract} do
    observed = Observation.observe_all(trace(contract))

    assert Map.keys(observed) |> Enum.sort() == [:official_hq, :old_zhai, :tech_inner]

    for {lens, report} <- observed do
      assert report.lens == lens
      assert is_list(report.records) and report.records != []
      assert report.retains != []
      assert report.loses != []
    end
  end

  test "every record declares where its value came from", %{contract: contract} do
    evidence = Observation.evidence(Observation.observe_all(trace(contract)))

    for record <- evidence do
      assert record.provenance in [
               :measured,
               :quantised,
               :clipped,
               :categorical,
               :administrative,
               :unrecorded
             ]

      assert is_atom(record.field)
      assert Map.has_key?(record, :value)
    end
  end

  test "what a lens cannot sense is recorded as unrecorded, not dropped", %{contract: contract} do
    observed = Observation.observe_all(trace(contract))

    official = observed.official_hq.records
    assert Enum.any?(official, &(&1.field == :surge_count and &1.value == :unrecorded))

    zhai = observed.old_zhai.records
    assert Enum.any?(zhai, &(&1.field == :surge_lag and &1.value == :unrecorded))

    # A missing observation and an observation that excluded something are
    # different facts, so the missing one is a value, never an absence.
    refute Enum.any?(zhai, &(&1.field == :sign_order and &1.value == :unrecorded))
  end

  test "the administrative lens supplies its cause column by rule", %{contract: contract} do
    for hidden <- [
          %{c0_state: :c0_collapsed, pulse_kind: :alpha_shock, safe_phase_window: :relative},
          %{c0_state: :c0_intact, pulse_kind: :alpha_metal_resonance, safe_phase_window: :late}
        ] do
      official = Observation.observe_all(Slice.trace(hidden, contract)).official_hq.records

      cause = Enum.find(official, &(&1.field == :cause_tag))
      assert cause.value == :seasonal_rain_mudslide
      assert cause.provenance == :administrative
    end
  end

  test "the technical lens clips amplitude and keeps timing", %{contract: contract} do
    tech = Observation.observe_all(trace(contract)).tech_inner.records

    amplitude = Enum.find(tech, &(&1.field == :amplitude))
    assert amplitude.value == :frame_slam
    assert amplitude.provenance == :clipped

    lag = Enum.find(tech, &(&1.field == :surge_lag))
    assert is_number(lag.value)
    assert lag.provenance == :quantised
    assert lag.resolution == 0.25
  end

  test "the quantisation is the instrument's, and it is stamped", %{contract: contract} do
    raw = Slice.surge_lag(trace(contract))

    for resolution <- [0.1, 0.25, 1.0] do
      observed = Observation.observe_all(trace(contract), resolution: resolution)
      lag = Enum.find(observed.tech_inner.records, &(&1.field == :surge_lag))
      assert lag.resolution == resolution
      assert_in_delta lag.value, raw, resolution / 2 + 1.0e-9
    end
  end

  test "the communal lens keeps order and loses magnitude and date", %{contract: contract} do
    zhai = Observation.observe_all(trace(contract)).old_zhai.records

    order = Enum.find(zhai, &(&1.field == :sign_order))
    assert order.value in [:before_rain, :after_rain]
    assert Enum.any?(zhai, &(&1.field == :magnitude and &1.value == :unrecorded))
    refute Enum.any?(zhai, &(&1.field == :surge_lag and is_number(&1.value)))
  end

  test "the phenology order is a function of the trace, not of the lens", %{contract: contract} do
    for hidden <- Slice.hypotheses(contract) do
      t = Slice.trace(hidden, contract)
      expected = if Slice.signs_before_rain?(t), do: :before_rain, else: :after_rain
      zhai = Observation.observe_all(t).old_zhai.records

      assert Enum.find(zhai, &(&1.field == :sign_order)).value == expected
    end
  end

  test "a lens cannot write back into the trace it observed", %{contract: contract} do
    t = trace(contract)
    before = :erlang.term_to_binary(t)

    _ = Observation.observe_all(t, resolution: 0.1)
    _ = Observation.observe_all(t, resolution: 5.0)
    _ = Observation.evidence(Observation.observe_all(t))

    assert :erlang.term_to_binary(t) == before

    assert t.hidden == %{
             c0_state: :c0_collapsed,
             pulse_kind: :alpha_shock,
             safe_phase_window: :relative
           }
  end

  test "the same trace produces the same record through every lens", %{contract: contract} do
    t = trace(contract)
    first = Observation.observe_all(t)
    second = Observation.observe_all(t)
    assert first == second
  end

  test "the lens set is closed: no institution records outside its declared fields", %{
    contract: contract
  } do
    observed = Observation.observe_all(trace(contract))

    declared = %{
      official_hq: [:water_level, :surge_count, :rain_total, :material, :quantity, :cause_tag],
      tech_inner: [
        :impact_count,
        :amplitude,
        :surge_lag,
        :surge_direction,
        :rain_total,
        :material
      ],
      old_zhai: [:sign_order, :sign_sequence, :magnitude, :surge_lag, :count_of_blows]
    }

    for {lens, report} <- observed, record <- report.records do
      assert record.field in Map.fetch!(declared, lens),
             "#{lens} recorded an undeclared field #{record.field}"
    end
  end

  test "the trace distinguishes a self-sustained limit cycle from the resting basin", %{
    contract: contract
  } do
    collapsed =
      Slice.trace(
        %{c0_state: :c0_collapsed, pulse_kind: :alpha_shock, safe_phase_window: :relative},
        contract
      )

    intact =
      Slice.trace(
        %{c0_state: :c0_intact, pulse_kind: :alpha_shock, safe_phase_window: :relative},
        contract
      )

    assert collapsed.oscillating?
    assert is_number(collapsed.settled_period)
    assert_in_delta collapsed.settled_period, collapsed.reference_period, 0.10

    refute intact.oscillating?
    assert intact.settled_period == nil
    assert is_number(intact.reference_period)
  end
end
