defmodule Voy.World.NodeTest do
  use ExUnit.Case, async: true

  @ascended_features [
    up_terrain: :karst_sinkhole_headwater,
    pass_terrain: :canyon_pass,
    down_terrain: :mid_terrace,
    up_geology: :deep_stress_cavity,
    accessibility: :cliff_isolated
  ]

  test "forward node compilation binds names only after semantic solving" do
    assert {:ok, [record]} = Voy.World.compile_node(node_spec(), @ascended_features)

    assert record["node_id"] == "node_rift_07"
    assert record["topography"]["up_site"] == "node_rift_07/up"
    assert record["topography"]["up_name"] == "鬼哭漏斗"
    assert record["era3"]["fate"] == "Fate_Ascended_SpatialRift"
    assert record["era3"]["final_regime"] == "Strained"
    assert record["names"]["down"] == "悬棺寨"
    assert hd(record["structures"]["upstream_site"]) == "鬼哭漏斗"
    assert JSON.encode!(record)
  end

  test "the default rule set expresses all four fates through node constraints" do
    cases = [
      {:Fate_Assimilated_NewZhai,
       [
         up_terrain: :steep_fissure_headwater,
         pass_terrain: :canyon_pass,
         down_terrain: :alluvial_flat,
         up_geology: :active_fault_vein,
         accessibility: :tractor_road_accessible
       ], "Oscillating"},
      {:Fate_Annihilated_BurnoutZone,
       [
         up_terrain: :karst_sinkhole_headwater,
         pass_terrain: :karst_gorge,
         down_terrain: :alluvial_flat,
         up_geology: :deep_stress_cavity,
         accessibility: :tractor_road_accessible
       ], "Burnout"},
      {:Fate_Ascended_SpatialRift, @ascended_features, "Strained"},
      {:Fate_Ancestral_ControlGroup,
       [
         up_terrain: :steep_fissure_headwater,
         pass_terrain: :canyon_pass,
         down_terrain: :mid_terrace,
         up_geology: :minor_fracture,
         accessibility: :cliff_isolated
       ], "Calm"}
    ]

    for {fate, features, regime} <- cases do
      spec = node_spec(fate_constraint: {:require, fate})
      assert {:ok, [record]} = Voy.World.compile_node(spec, features)
      assert record["era3"]["fate"] == Atom.to_string(fate)
      assert record["era3"]["final_regime"] == regime
    end
  end

  test "reverse node solving returns unnamed semantic candidates with stable site ids" do
    assert {:ok, candidates} = Voy.World.reverse_node(node_spec())
    assert length(candidates) == 4

    assert candidates |> Enum.map(& &1["topography"]["pass_terrain"]) |> MapSet.new() ==
             MapSet.new(["canyon_pass", "karst_gorge"])

    assert candidates |> Enum.map(& &1["topography"]["down_terrain"]) |> MapSet.new() ==
             MapSet.new(["alluvial_flat", "mid_terrace"])

    for candidate <- candidates do
      topo = candidate["topography"]

      assert topo["node_id"] == "node_rift_07"
      assert topo["down_site"] == "node_rift_07/down"
      assert topo["up_terrain"] == "karst_sinkhole_headwater"
      assert topo["down_terrain"] in ["alluvial_flat", "mid_terrace"]
      assert topo["up_geology"] == "deep_stress_cavity"
      assert topo["accessibility"] == "cliff_isolated"
      assert candidate["history"]["era3"]["fate"] == "Fate_Ascended_SpatialRift"

      refute Map.has_key?(topo, "up_name")
      refute Map.has_key?(topo, "pass_name")
      refute Map.has_key?(topo, "down_name")
    end
  end

  test "a reverse candidate materializes and survives a forward round-trip" do
    assert {:ok, [candidate | _]} = Voy.World.reverse_node(node_spec())
    assert {:ok, record} = Voy.World.materialize_node(node_spec(), candidate)

    features =
      Map.take(candidate["topography"], [
        "up_terrain",
        "pass_terrain",
        "down_terrain",
        "up_geology",
        "accessibility"
      ])

    assert {:ok, [recompiled]} = Voy.World.compile_node(node_spec(), features)
    assert recompiled == record
    assert record["dossier"]["fate"] == "Fate_Ascended_SpatialRift"
    refute Map.has_key?(record, "archives")
  end

  test "display names do not alter historical or pathway semantics" do
    other_names = [node: "异名支沟", up: "无名源头", pass: "无名隘口", down: "无名聚落"]

    assert {:ok, [left]} = Voy.World.compile_node(node_spec(), @ascended_features)

    assert {:ok, [right]} =
             Voy.World.compile_node(node_spec(names: other_names), @ascended_features)

    for era <- ["era1", "era2a", "era2b", "era3"] do
      assert left[era] == right[era]
    end

    assert left["dossier"]["relic"] == right["dossier"]["relic"]
    assert left["dossier"]["fate"] == right["dossier"]["fate"]
    assert left["dossier"]["binding"]["safe_tick"] == right["dossier"]["binding"]["safe_tick"]
  end

  test "node specifications validate fate sets, rule sets, names, and incompatible goals" do
    multi_fate_spec =
      node_spec(
        fate_constraint: {:one_of, ["Fate_Ascended_SpatialRift", "Fate_Ancestral_ControlGroup"]}
      )

    assert {:ok, candidates} = Voy.World.reverse_node(multi_fate_spec)
    assert length(candidates) == 8

    assert candidates
           |> Enum.map(& &1["history"]["era3"]["fate"])
           |> MapSet.new() ==
             MapSet.new(["Fate_Ascended_SpatialRift", "Fate_Ancestral_ControlGroup"])

    assert {:error, :unknown_fate} =
             Voy.World.reverse_node(node_spec(fate_constraint: {:require, :not_a_fate}))

    assert {:error, :invalid_fate_constraint} =
             Voy.World.reverse_node(node_spec(fate_constraint: {:one_of, []}))

    assert {:error, :unknown_rule_set} =
             Voy.World.reverse_node(node_spec(rule_set: :missing_rules))

    assert {:error, :incomplete_name_book} =
             Voy.World.compile_node(node_spec(names: nil), @ascended_features)

    baishui_features = [
      up_terrain: :steep_fissure_headwater,
      pass_terrain: :canyon_pass,
      down_terrain: :alluvial_flat,
      up_geology: :active_fault_vein,
      accessibility: :tractor_road_accessible
    ]

    assert {:error, :fate_constraint_unsatisfied} =
             Voy.World.solve_node(node_spec(), baishui_features)
  end

  test "open variation nodes expose Hop 1..4 + decoys and settle Hop 5 while preserving seed credentials" do
    baishui_features = [
      up_terrain: :steep_fissure_headwater,
      pass_terrain: :canyon_pass,
      down_terrain: :alluvial_flat,
      up_geology: :active_fault_vein,
      accessibility: :tractor_road_accessible
    ]

    locked_spec =
      node_spec(
        node_id: "node_baishui_locked",
        branch_id: "branch_01_baishui",
        horizon: :locked,
        fate_constraint: {:require, :Fate_Assimilated_NewZhai}
      )

    open_spec =
      node_spec(
        node_id: "node_baishui_open",
        branch_id: "branch_01_baishui",
        horizon: :open,
        fate_constraint: {:require, :Fate_Assimilated_NewZhai}
      )

    assert {:ok, %{horizon: :locked}} = Voy.World.Node.normalize_spec(node_spec())
    assert {:ok, %{horizon: :open}} = Voy.World.Node.normalize_spec(open_spec)

    assert {:error, :invalid_horizon} =
             Voy.World.Node.normalize_spec(node_spec(horizon: :invalid))

    assert {:ok, [locked_record]} = Voy.World.compile_node(locked_spec, baishui_features)
    assert {:ok, [open_record]} = Voy.World.compile_node(open_spec, baishui_features)

    assert locked_record["horizon"] == "locked"
    assert locked_record["dossier"]["horizon"] == "locked"
    assert locked_record["dossier"]["settled"] == true
    assert length(locked_record["dossier"]["documents"]) == 7

    open_dossier = open_record["dossier"]
    assert open_record["horizon"] == "open"
    assert open_dossier["horizon"] == "open"
    assert open_dossier["settled"] == false
    assert open_dossier["fate"] == "Fate_Assimilated_NewZhai"
    assert length(open_dossier["documents"]) == 6

    main_hops =
      open_dossier["documents"]
      |> Enum.filter(&(&1["role"] == "main"))
      |> Enum.map(& &1["hop"])

    assert main_hops == [1, 2, 3, 4]
    assert Enum.count(open_dossier["documents"], &(&1["role"] == "decoy")) == 2

    transitions = open_dossier["reachable_transitions"]
    assert length(transitions) == 4

    seed_batch = open_dossier["binding"]["batch_id"]
    seed_vessel = open_dossier["binding"]["vessel_code"]
    seed_tick = open_dossier["binding"]["safe_tick"]

    for t <- transitions do
      assert t["batch_id"] == seed_batch
      assert t["vessel_code"] == seed_vessel
      assert t["safe_tick"] == seed_tick
    end

    # Settle with :reanchor_vessel -> Fate_Tolerated_FalseAlarm (Calm)
    assert {:ok, settled_false_alarm} =
             Voy.World.Witness.settle_open_dossier(open_dossier, :reanchor_vessel)

    assert settled_false_alarm.settled == true
    assert settled_false_alarm.applied_intervention == :reanchor_vessel
    assert settled_false_alarm.baseline_fate == :Fate_Assimilated_NewZhai
    assert settled_false_alarm.fate == :Fate_Tolerated_FalseAlarm
    assert settled_false_alarm.final_regime == :Calm
    assert settled_false_alarm.binding.batch_id == seed_batch
    assert settled_false_alarm.binding.vessel_code == seed_vessel
    assert settled_false_alarm.binding.safe_tick == seed_tick
    assert length(settled_false_alarm.documents) == 7
    assert settled_false_alarm.hop5.hop == 5
    assert settled_false_alarm.hop5.body =~ seed_vessel
    assert settled_false_alarm.hop5.body =~ "第#{seed_tick}格"
    assert settled_false_alarm.hop5.body =~ "绝壁观山寮核销条规"

    # Settle with :reanchor_and_dampen -> Fate_Ancestral_ControlGroup (Calm)
    assert {:ok, settled_ancestral} =
             Voy.World.settle_open_dossier(open_dossier, :reanchor_and_dampen)

    assert settled_ancestral["settled"] == true
    assert settled_ancestral["applied_intervention"] == "reanchor_and_dampen"
    assert settled_ancestral["fate"] == "Fate_Ancestral_ControlGroup"
    assert settled_ancestral["final_regime"] == "Calm"
    assert settled_ancestral["binding"]["batch_id"] == seed_batch
    assert settled_ancestral["binding"]["vessel_code"] == seed_vessel
    assert settled_ancestral["binding"]["safe_tick"] == seed_tick
    assert settled_ancestral["hop5"]["body"] =~ seed_vessel
    assert settled_ancestral["hop5"]["body"] =~ "第#{seed_tick}格"
    assert settled_ancestral["hop5"]["body"] =~ "老寨护山联营旧约"
  end

  defp node_spec(overrides \\ []) do
    Keyword.merge(
      [
        node_id: "node_rift_07",
        branch_id: "branch_rift",
        rule_set: :default_branch_v1,
        fate_constraint: {:require, :Fate_Ascended_SpatialRift},
        seed: 101,
        names: [node: "黑水支沟", up: "鬼哭漏斗", pass: "断碑垭口", down: "悬棺寨"]
      ],
      overrides
    )
  end
end
