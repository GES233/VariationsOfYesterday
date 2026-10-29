defmodule Voy.World.AscribeTest do
  use ExUnit.Case, async: true

  alias Voy.World.{Ascribe, Contract, Facts, Observation, Slice}

  setup_all do
    {:ok, contract: Contract.load!()}
  end

  defp evidence_for(hidden, contract, opts \\ []) do
    hidden
    |> Slice.trace(contract)
    |> Observation.observe_all(opts)
    |> Observation.evidence()
  end

  @truth %{c0_state: :c0_collapsed, pulse_kind: :alpha_shock, safe_phase_window: :relative}

  # ==== the closed loop ==== //

  test "every hypothesis in H is recovered uniquely from its own record", %{contract: contract} do
    hypotheses = Slice.hypotheses(contract)
    assert length(hypotheses) == 24

    for hidden <- hypotheses do
      evidence = evidence_for(hidden, contract)

      assert {:unique, ^hidden} = Ascribe.explain(evidence, contract: contract),
             "abduction did not recover #{inspect(hidden)}"
    end
  end

  test "the answer is a function of the evidence and nothing else", %{contract: contract} do
    hidden = %{c0_state: :c0_intact, pulse_kind: :alpha_metal_resonance, safe_phase_window: :late}
    evidence = evidence_for(hidden, contract)

    # Shuffled evidence must give the same answer: no order dependence, no
    # first-match shortcut.
    assert Ascribe.explain(Enum.shuffle(evidence), contract: contract) ==
             Ascribe.explain(evidence, contract: contract)
  end

  # ==== the four answers, four shapes ==== //

  test "the four answers are distinguishable and never an empty list", %{contract: contract} do
    unique = evidence_for(@truth, contract)

    # 1. unique
    assert {:unique, @truth} = Ascribe.explain(unique, contract: contract)

    # 2. multiple: the record is complete, the instrument's resolution cannot
    #    separate two pulse identities that differ by less than one tick.
    coarse = evidence_for(@truth, contract, resolution: 4.0)
    assert {:multiple, %{candidates: candidates, differing: differing}} =
             Ascribe.explain(coarse, contract: contract)

    assert length(candidates) > 1
    assert Enum.any?(differing, &(&1.field == :surge_lag))

    # 3. incomplete: the field that would have separated them is missing.
    without_lag = Enum.reject(unique, &(&1.field == :surge_lag and &1.lens == :tech_inner))
    assert {:incomplete, %{candidates: many, missing: missing}} =
             Ascribe.explain(without_lag, contract: contract)

    assert length(many) > 1
    assert missing == [:surge_lag]

    # 4. none: a definite recorded value that every hypothesis contradicts.
    contradicted =
      Enum.map(unique, fn record ->
        if record.lens == :tech_inner and record.field == :surge_lag, do: %{record | value: 12.0}, else: record
      end)

    assert {:none, %{contradictions: contradictions}} = Ascribe.explain(contradicted, contract: contract)
    assert contradictions != []
  end

  test "a contradiction names the field, the recorded value and the prediction", %{contract: contract} do
    evidence = evidence_for(@truth, contract)

    contradicted =
      Enum.map(evidence, fn record ->
        if record.field == :impact_count, do: %{record | value: :single}, else: record
      end)

    assert {:none, %{contradictions: [{_hypothesis, contradiction} | _]}} =
             Ascribe.explain(contradicted, contract: contract)

    assert contradiction.field == :impact_count
    assert contradiction.recorded == :single
    assert contradiction.predicted == :repeated
  end

  # ==== minimal chain and ablation ==== //

  test "the ablation is per item and measured, not authored", %{contract: contract} do
    evidence = evidence_for(@truth, contract)
    report = Ascribe.ablate(evidence, contract: contract)

    assert {:unique, @truth} = report.baseline
    assert length(report.items) == length(evidence)

    # Removing the timestamped lag loses uniqueness; it is the only item that
    # carries the landing window.
    lag = Enum.find(report.items, &(&1.lens == :tech_inner and &1.field == :surge_lag))
    assert lag.necessary?
    refute match?({:unique, _}, lag.without)

    # Removing anything else changes nothing.
    for item <- report.items, item.field != :surge_lag do
      assert {:unique, @truth} = item.without, "removing #{item.field} changed the answer"
      assert item.irrelevant?
    end

    assert Ascribe.minimal_chain(evidence, contract: contract) == [
             %{lens: :tech_inner, field: :surge_lag}
           ]
  end

  test "the c0 witnesses are redundant with each other", %{contract: contract} do
    evidence = evidence_for(@truth, contract)

    # Either witness alone pins c0 ...
    for field <- [:impact_count, :water_level] do
      kept = Enum.reject(evidence, &(&1.field == :water_level or &1.field == :impact_count))
      kept = kept ++ Enum.filter(evidence, &(&1.field == field))
      assert {:unique, @truth} = Ascribe.explain(kept, contract: contract)
    end

    # ... so neither is individually necessary, and dropping both loses it.
    both_gone = Enum.reject(evidence, &(&1.field in [:impact_count, :water_level]))
    refute match?({:unique, _}, Ascribe.explain(both_gone, contract: contract))
  end

  # ==== negative controls ==== //

  test "a plausible alternative etiology is excluded by the record", %{contract: contract} do
    evidence = evidence_for(@truth, contract)

    assert {:excluded, reasons} = Ascribe.refute(evidence, :autumn_rain)
    assert Enum.any?(reasons, &(&1.field == :impact_count))
    assert Enum.any?(reasons, &(&1.field == :sign_order))

    assert {:excluded, _} = Ascribe.refute(evidence, :natural_collapse)
    assert :unknown_alternative = Ascribe.refute(evidence, :a_made_up_cause)
  end

  test "the rain explanation survives only once all three of its witnesses are gone", %{contract: contract} do
    evidence = evidence_for(@truth, contract)
    witnesses = [:impact_count, :sign_order, :amplitude]

    # Any one of them alone refutes the rain story ...
    for field <- witnesses do
      kept = Enum.filter(evidence, &(&1.field == field)) ++ Enum.reject(evidence, &(&1.field == field))
      assert {:excluded, reasons} = Ascribe.refute(kept, :autumn_rain), "#{field} alone must refute"
      assert Enum.any?(reasons, &(&1.field == field))
    end

    # ... and only dropping all three lets it stand. An alternative that
    # cannot be excluded is a result, and it names what would settle it.
    gone = Enum.reject(evidence, &(&1.field in witnesses))
    assert {:not_excluded, survivors} = Ascribe.refute(gone, :autumn_rain)
    assert Enum.map(survivors, & &1.field) |> Enum.sort() == Enum.sort(witnesses)
  end

  test "the naive cause-tag classifier is right by accident, and provably so", %{contract: contract} do
    world_a = @truth
    world_b = %{c0_state: :c0_intact, pulse_kind: :alpha_metal_resonance, safe_phase_window: :late}

    evidence_a = evidence_for(world_a, contract)
    evidence_b = evidence_for(world_b, contract)

    # The administrative record is identical in both worlds ...
    tag_a = Enum.find(evidence_a, &(&1.field == :cause_tag))
    tag_b = Enum.find(evidence_b, &(&1.field == :cause_tag))
    assert tag_a == tag_b

    # ... so a rule keyed on it must answer identically for both, and be
    # wrong about one of them. The real abducer separates them.
    assert Ascribe.official_only(evidence_a) == Ascribe.official_only(evidence_b)
    assert Ascribe.official_only(evidence_b) == {:unique, world_a}
    assert {:unique, ^world_b} = Ascribe.explain(evidence_b, contract: contract)
  end

  # ==== resolution as the real constraint ==== //

  test "identifiability is measured against the instrument resolution", %{contract: contract} do
    trace =
      Slice.trace(
        %{c0_state: :c0_intact, pulse_kind: :heavy_kick, safe_phase_window: :absolute},
        contract
      )

    answers = Map.new(Ascribe.identifiability(trace, [0.1, 0.25, 1.0, 4.0]))

    assert match?({:unique, _}, answers[0.1])
    assert match?({:unique, _}, answers[0.25])
    # A whole-unit clock cannot separate alpha_shock from heavy_kick in this
    # window, and that is a property of the record, not of the story.
    assert match?({:multiple, _}, answers[1.0])
    assert match?({:multiple, _}, answers[4.0])
  end

  test "the abducer never reads a fate, a document id or a person", %{contract: contract} do
    evidence = evidence_for(@truth, contract)
    fields = evidence |> Enum.map(& &1.field) |> MapSet.new()

    for forbidden <- [:fate, :document, :author, :claim, :verdict, :expected_cause] do
      refute MapSet.member?(fields, forbidden)
    end
  end

  # ==== the numeric layer settles the symbolic claim ==== //

  test "a claim from the archives is settled by the numeric oracle", %{contract: contract} do
    # The archives claim the pulse landed in the vulnerable window and fired
    # the surge. The oracle measured a delay. The ledger records the claim,
    # then records its settlement against the measurement.
    {:ok, predicted, log} =
      Facts.record(Facts.new(),
        tick: 100,
        kind: :predicted,
        actor: {:player, "u1"},
        site: "branch_01_baishui/up",
        props: [claim: "易损窗内点炮将当场激发一次尖峰", due_tick: 160]
      )

    outcome =
      case Contract.outcome(contract, :alpha_shock, :relative) do
        {:ok, %{outcome: :premature}} -> :held
        {:ok, _} -> :failed
        :error -> :partial
      end

    {:ok, settled, log} = Facts.settle(log, predicted.id, outcome, tick: 160)
    assert settled.props[:outcome] == :failed
    assert Facts.pending(log) == []
  end

  # ==== relational Kanren & minimal sufficient sets ==== //

  test "Kanren disequality taboo forces OfficialHQ onto the rain scapegoat" do
    causes =
      Voy.Kanren.Core.run_all(fn q -> Ascribe.official_tabooo(q) end)
      |> Voy.Kanren.Core.terms()

    assert causes == [:seasonal_rain_mudslide]
  end

  test "minimal_sufficient_sets are genuinely sufficient and minimal, and intersect to minimal_chain", %{contract: contract} do
    evidence = evidence_for(@truth, contract)
    core = Ascribe.minimal_chain(evidence, contract: contract)
    sets = Ascribe.minimal_sufficient_sets(evidence, contract: contract)

    # Because c0_state has two redundant witnesses (water_level and impact_count),
    # the leave-one-out core alone is NOT sufficient ...
    refute match?(
             {:unique, @truth},
             Ascribe.explain(Ascribe.subset_records(evidence, core), contract: contract)
           )

    # ... whereas every minimal sufficient set S IS sufficient on its own,
    # and dropping any single element from S loses uniqueness.
    assert length(sets) == 2

    for s <- sets do
      assert {:unique, @truth} =
               Ascribe.explain(Ascribe.subset_records(evidence, s), contract: contract)

      for item <- s do
        proper_subset = List.delete(s, item)

        refute match?(
                 {:unique, @truth},
                 Ascribe.explain(Ascribe.subset_records(evidence, proper_subset), contract: contract)
               ),
               "dropping #{inspect(item)} from #{inspect(s)} should lose uniqueness"
      end
    end

    # The intersection of all minimal sufficient sets equals the leave-one-out core.
    intersection =
      sets
      |> Enum.map(&MapSet.new/1)
      |> Enum.reduce(&MapSet.intersection/2)

    assert MapSet.equal?(intersection, MapSet.new(core))
  end

  test "colliding lag pairs in the late window make OldZhai count_of_blows the essential core and span all three lenses", %{contract: contract} do
    late_res = %{c0_state: :c0_collapsed, pulse_kind: :alpha_metal_resonance, safe_phase_window: :late}
    evidence = evidence_for(late_res, contract)

    # On this hypothesis, surge_lag (37.0) collides with alpha_shock/late and
    # is backed up by surge_direction (:early) within alpha_metal_resonance,
    # so OldZhai's count_of_blows becomes the unique leave-one-out core item!
    core = Ascribe.minimal_chain(evidence, contract: contract)
    assert core == [%{lens: :old_zhai, field: :count_of_blows}]

    sets = Ascribe.minimal_sufficient_sets(evidence, contract: contract)
    assert length(sets) >= 2

    # Minimal sufficient sets for this hypothesis span all three institutions:
    # OfficialHQ (water_level) + TechInner (surge_lag or surge_direction) + OldZhai (count_of_blows).
    assert Enum.any?(sets, fn s ->
             lenses = s |> Enum.map(& &1.lens) |> Enum.uniq() |> Enum.sort()
             lenses == [:official_hq, :old_zhai, :tech_inner]
           end)
  end
end
