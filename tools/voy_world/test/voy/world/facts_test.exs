defmodule Voy.World.FactsTest do
  use ExUnit.Case, async: true
  use Voy.Kanren

  alias Voy.World.Facts

  test "entries keep log order and ids while ticks stay monotonic" do
    log = Facts.new()

    {:ok, first, log} =
      Facts.record(log,
        tick: 0,
        kind: :saw,
        actor: {:player, "u1"},
        site: "node_baishui_01/up",
        props: [phase: :RelativeRefractory]
      )

    {:ok, second, log} =
      Facts.record(log,
        tick: 3,
        kind: :did,
        actor: {:crowd, "brigade_04"},
        site: "node_baishui_01/up",
        props: [phase: :RelativeRefractory, item: "红绸包裹的东方红-54气缸体"],
        caused_by: first.id,
        evidence: [first.id]
      )

    assert [^first, ^second] = Facts.entries(log)
    assert second.id == first.id + 1
    assert Facts.count(log) == 2
    assert Facts.now(log) == 3
    assert Facts.last(log) == second
    assert {:ok, [^first, ^second]} = Facts.ancestors(log, second.id)
    assert {:ok, [^first]} = Facts.ancestors(log, first.id)
    assert :error = Facts.ancestors(log, 99)
  end

  test "malformed entries are rejected instead of stored" do
    log = Facts.new()

    assert {:error, {:missing_fields, [:site]}} = Facts.record(log, tick: 0, kind: :saw)

    assert {:error, {:invalid_kind, :exploded}} =
             Facts.record(log, tick: 0, kind: :exploded, site: "s")

    assert {:error, {:invalid_tick, -1}} = Facts.record(log, tick: -1, kind: :saw, site: "s")

    assert {:error, {:invalid_actor, "u1"}} =
             Facts.record(log, tick: 0, kind: :saw, site: "s", actor: "u1")

    assert {:error, {:unknown_caused_by, 99}} =
             Facts.record(log, tick: 0, kind: :did, site: "s", caused_by: 99)

    assert {:error, {:invalid_props, [{"k", "v"}]}} =
             Facts.record(log, tick: 0, kind: :did, site: "s", props: [{"k", "v"}])

    {:ok, _fact, log} = Facts.record(log, tick: 5, kind: :saw, site: "s")

    assert {:error, {:tick_going_backwards, 3, 5}} =
             Facts.record(log, tick: 3, kind: :saw, site: "s")
  end

  test "predictions stay pending until a settlement closes them" do
    {:ok, prediction, log} =
      Facts.record(Facts.new(),
        tick: 10,
        kind: :predicted,
        site: "node_baishui_01/down",
        props: [claim: "下游将在 5 刻内提前起峰", due_tick: 15]
      )

    assert Facts.pending(log) == [prediction]

    {:ok, settled, log} =
      Facts.settle(log, prediction.id, :held, tick: 16, props: [observed_tick: 15])

    assert settled.kind == :settled
    assert settled.caused_by == prediction.id
    assert settled.tick == 16
    assert settled.props[:outcome] == :held
    assert Facts.pending(log) == []

    assert {:ok, [^prediction, ^settled]} = Facts.ancestors(log, settled.id)

    assert {:error, {:already_settled, _}} = Facts.settle(log, prediction.id, :failed, tick: 17)

    assert {:error, {:invalid_outcome, :maybe, _}} =
             Facts.settle(log, prediction.id, :maybe, tick: 17)

    assert :error = Facts.settle(log, 99, :held, tick: 17)
  end

  test "a prediction needs a claim and a future due tick" do
    assert {:error, {:prediction_needs_claim, nil}} =
             Facts.record(Facts.new(), tick: 0, kind: :predicted, site: "s", props: [due_tick: 5])

    assert {:error, {:prediction_needs_due_tick, nil}} =
             Facts.record(Facts.new(), tick: 0, kind: :predicted, site: "s", props: [claim: "x"])

    assert {:error, {:prediction_due_in_the_past, 3, 5}} =
             Facts.record(Facts.new(),
               tick: 5,
               kind: :predicted,
               site: "s",
               props: [claim: "x", due_tick: 3]
             )
  end

  test "a settlement must point at a prediction" do
    {:ok, observation, log} = Facts.record(Facts.new(), tick: 0, kind: :saw, site: "s")

    assert {:error, {:settlement_needs_caused_by}} =
             Facts.record(log, tick: 1, kind: :settled, site: "s", props: [outcome: :held])

    assert {:error, {:wrong_fact_kind, _, :saw, :predicted}} =
             Facts.record(log,
               tick: 1,
               kind: :settled,
               site: "s",
               props: [outcome: :held],
               caused_by: observation.id
             )
  end

  test "queries give an as-of view of the log" do
    log = Facts.new()
    {:ok, early, log} = Facts.record(log, tick: 0, kind: :saw, site: "a")
    {:ok, late, log} = Facts.record(log, tick: 10, kind: :did, site: "b")

    assert Facts.up_to(log, 5) == [early]
    assert Facts.up_to(log, 10) == [early, late]
    assert Facts.since(log, 5) == [late]
    assert Facts.at(log, 10) == [late]
    assert Facts.by_site(log, "b") == [late]
    assert Facts.by_kind(log, :saw) == [early]
  end

  test "entries are open attribute lists the relational kernel can query" do
    log = Facts.new()

    {:ok, _observation, log} =
      Facts.record(log,
        tick: 0,
        kind: :saw,
        site: "node/up",
        props: [phase: :RelativeRefractory]
      )

    {:ok, _action, log} =
      Facts.record(log,
        tick: 3,
        kind: :did,
        site: "node/up",
        props: [phase: :RelativeRefractory, item: "2号岩石硝铵炸药点炮"]
      )

    results =
      run_all(fn query ->
        fresh [fact, id, props, phase, site] do
          membero(fact, Facts.terms(log))
          propso(fact, kind: :did, id: id, site: site)
          lookupo(:props, props, fact)
          lookupo(:phase, phase, props)
          eq(query, id: id, site: site, phase: phase)
        end
      end)
      |> terms()

    assert [result] = results
    assert result[:site] == "node/up"
    assert result[:phase] == :RelativeRefractory
    assert result[:id] == 2
  end
end
