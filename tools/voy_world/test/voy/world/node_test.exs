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
    assert length(candidates) == 2

    assert candidates |> Enum.map(& &1["topography"]["pass_terrain"]) |> MapSet.new() ==
             MapSet.new(["canyon_pass", "karst_gorge"])

    for candidate <- candidates do
      topo = candidate["topography"]

      assert topo["node_id"] == "node_rift_07"
      assert topo["down_site"] == "node_rift_07/down"
      assert topo["up_terrain"] == "karst_sinkhole_headwater"
      assert topo["down_terrain"] == "mid_terrace"
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
    assert record["archives"]["tech_note"]["true_cause"] == "excitation_zenith_spatial_exile"
  end

  test "display names do not alter historical or causal semantics" do
    other_names = [node: "异名支沟", up: "无名源头", pass: "无名隘口", down: "无名聚落"]

    assert {:ok, [left]} = Voy.World.compile_node(node_spec(), @ascended_features)

    assert {:ok, [right]} =
             Voy.World.compile_node(node_spec(names: other_names), @ascended_features)

    for era <- ["era1", "era2a", "era2b", "era3"] do
      assert left[era] == right[era]
    end

    assert left["archives"]["official_report"]["claimed_cause"] ==
             right["archives"]["official_report"]["claimed_cause"]

    assert left["archives"]["tech_note"]["true_cause"] ==
             right["archives"]["tech_note"]["true_cause"]
  end

  test "node specifications validate fate sets, rule sets, names, and incompatible goals" do
    multi_fate_spec =
      node_spec(
        fate_constraint: {:one_of, ["Fate_Ascended_SpatialRift", "Fate_Ancestral_ControlGroup"]}
      )

    assert {:ok, candidates} = Voy.World.reverse_node(multi_fate_spec)
    assert length(candidates) == 4

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

  defp node_spec(overrides \\ []) do
    Keyword.merge(
      [
        node_id: "node_rift_07",
        branch_id: "branch_rift",
        rule_set: :default_branch_v1,
        fate_constraint: {:require, :Fate_Ascended_SpatialRift},
        names: [node: "黑水支沟", up: "鬼哭漏斗", pass: "断碑垭口", down: "悬棺寨"]
      ],
      overrides
    )
  end
end
