defmodule Voy.World.ExportTest do
  use ExUnit.Case, async: true

  alias Voy.World.{Export, Profile}

  import Voy.World.TestFixtures

  @complete_features [
    up_terrain: :steep_fissure_headwater,
    pass_terrain: :canyon_pass,
    down_terrain: :alluvial_flat,
    up_geology: :active_fault_vein,
    accessibility: :tractor_road_accessible
  ]

  @spec_fields [
    node_id: "node_baishui_01",
    branch_id: "branch_01_baishui",
    rule_set: :default_branch_v1,
    fate_constraint: {:require, :Fate_Assimilated_NewZhai},
    names: [node: "白水支沟", up: "野猪箐裂隙", pass: "断碑垭口", down: "白水坝"]
  ]

  test "a node export carries the version, the stock and normalized records" do
    assert {:ok, bundle} = Export.export_node(@spec_fields, features: @complete_features)

    assert :ok = Export.validate(bundle)
    assert bundle["schema_version"] == Export.schema_version()
    assert bundle["rule_set"] == "default_branch_v1"
    assert bundle["profile"]["id"] == Profile.id(Profile.default())
    assert bundle["generator"]["app"] == "voy_world"

    assert [node] = bundle["nodes"]
    assert node["node_id"] == "node_baishui_01"
    assert node["branch_id"] == "branch_01_baishui"
    assert node["rule_set"] == "default_branch_v1"

    assert node["sites"] == %{
             "up" => "node_baishui_01/up",
             "pass" => "node_baishui_01/pass",
             "down" => "node_baishui_01/down"
           }

    assert node["names"] == %{
             "node" => "白水支沟",
             "up" => "野猪箐裂隙",
             "pass" => "断碑垭口",
             "down" => "白水坝"
           }

    assert node["topography"]["up_terrain"] == "steep_fissure_headwater"
    assert node["history"]["era3"]["fate"] == "Fate_Assimilated_NewZhai"

    # Structures are (display name, description) pairs, so the wire format
    # says which is which instead of leaving a bare two-element array.
    assert %{"site" => "野猪箐裂隙", "description" => description} =
             node["structures"]["upstream"]

    assert is_binary(description) and description != ""
    assert node["structures"]["downstream"]["site"] == "白水坝"

    # Tagged tuples become tag/value maps.
    assert %{"tag" => "requisitioned_as_scrap", "value" => "ancestral_bronze_zheng"} =
             node["history"]["era2b"]["relic_fate"]

    assert node["archives"]["official_report"]["claimed_cause"] == "seasonal_rain_mudslide"

    assert node["archives"]["tech_note"]["true_cause"] ==
             "c0_removed_and_vulnerable_window_blasting"
  end

  test "a bundle round-trips through JSON byte for byte" do
    assert {:ok, bundle} = Export.export_node(@spec_fields, features: @complete_features)
    assert {:ok, json} = Export.to_json(bundle)
    assert {:ok, loaded} = Export.from_json(json)

    assert loaded == bundle
    assert {:ok, json2} = Export.to_json(loaded)
    assert json2 == json
  end

  test "branch export derives site ids and the node id from the branch" do
    assert {:ok, bundle} = Export.export_branch(baishui_topo())
    assert :ok = Export.validate(bundle)

    assert [node] = bundle["nodes"]
    assert node["node_id"] == "branch_01_baishui"
    assert node["sites"]["up"] == "branch_01_baishui/up"
    assert node["history"]["era3"]["fate"] == "Fate_Assimilated_NewZhai"

    assert {:ok, renamed} = Export.export_branch(baishui_topo(), node_id: "node_07")
    assert hd(renamed["nodes"])["node_id"] == "node_07"
    assert hd(renamed["nodes"])["sites"]["up"] == "branch_01_baishui/up"
  end

  test "a reversed candidate can be exported without re-solving" do
    spec = [
      node_id: "node_rift_07",
      branch_id: "branch_rift",
      rule_set: :default_branch_v1,
      fate_constraint: {:require, :Fate_Ascended_SpatialRift},
      names: [node: "黑水支沟", up: "鬼哭漏斗", pass: "断碑垭口", down: "悬棺寨"]
    ]

    assert {:ok, [candidate | _rest]} = Voy.World.reverse_node(spec)
    assert {:ok, bundle} = Export.export_candidate(spec, candidate)
    assert :ok = Export.validate(bundle)

    assert [node] = bundle["nodes"]
    assert node["history"]["era3"]["fate"] == "Fate_Ascended_SpatialRift"
    assert node["structures"]["upstream"]["site"] == "鬼哭漏斗"
    assert node["topography"]["up_geology"] == "deep_stress_cavity"
  end

  test "two stocks produce two valid bundles for the same canon" do
    shifted =
      Profile.new(
        id: "shifted_v1",
        tau: %{tau_immediate: {0.0, 0.5}, tau_5: {1.0, 2.0}, tau_10: {4.0, 6.0}}
      )

    assert {:ok, default_bundle} = Export.export_branch(baishui_topo())
    assert {:ok, shifted_bundle} = Export.export_branch(baishui_topo(), profile: shifted)

    assert default_bundle["profile"]["id"] == "proto_v1"
    assert shifted_bundle["profile"]["id"] == "shifted_v1"
    assert default_bundle["profile"]["tau"]["tau_5"] != shifted_bundle["profile"]["tau"]["tau_5"]

    # The canon is identical; only the numbers behind the bands moved.
    assert default_bundle["nodes"] == shifted_bundle["nodes"]
    assert :ok = Export.validate(shifted_bundle)

    assert {:ok, restored} = Export.profile(shifted_bundle)
    assert {:ok, :tau_10} = Profile.lag_band(restored, 5.0)
  end

  test "validation rejects bundles that break the contract" do
    assert {:ok, bundle} = Export.export_branch(baishui_topo())
    [node] = bundle["nodes"]

    assert {:error, {:invalid_bundle, {:schema_version, 2, 1}}} =
             Export.validate(%{bundle | "schema_version" => 2})

    assert {:error, {:invalid_bundle, {:duplicate_node_ids, _}}} =
             Export.validate(%{bundle | "nodes" => [node, node]})

    assert {:error, {:invalid_bundle, {:missing_node_keys, ["archives"]}}} =
             Export.validate(%{bundle | "nodes" => [Map.delete(node, "archives")]})

    assert {:error, {:invalid_bundle, {:node_rule_set, _, "other_rule_set", "default_branch_v1"}}} =
             Export.validate(%{bundle | "nodes" => [%{node | "rule_set" => "other_rule_set"}]})

    assert {:error, {:invalid_bundle, {:not_json_safe, _}}} =
             Export.validate(Map.put(bundle, "extra", {:tuple, "value"}))

    assert {:error, {:invalid_bundle, {:nodes, []}}} = Export.validate(%{bundle | "nodes" => []})
    assert {:error, {:invalid_bundle, {:missing_schema_version, _}}} = Export.validate(%{})
  end

  test "invalid input is reported, not raised" do
    assert {:error, {:invalid_json, _}} = Export.from_json("{not json")
    assert {:error, {:invalid_bundle, _}} = Export.from_json("{\"schema_version\": 1}")
    assert {:error, {:not_json, :nope}} = Export.from_json(:nope)

    assert {:error, {:invalid_profile, :nope}} =
             Export.export_branch(baishui_topo(), profile: :nope)

    unsupported = [
      branch_id: :branch_broken,
      up_name: "U",
      pass_name: "P",
      down_name: "D",
      up_terrain: :steep_fissure_headwater,
      pass_terrain: :canyon_pass,
      down_terrain: :alluvial_flat,
      up_geology: :active_fault_vein,
      accessibility: :cliff_isolated
    ]

    assert {:error, :unsupported_topography} = Export.export_branch(unsupported)

    unresolved = [
      branch_id: :branch_unresolved,
      up_name: "U",
      pass_name: "P",
      down_name: "D",
      up_terrain: :steep_fissure_headwater,
      pass_terrain: :canyon_pass,
      down_terrain: :mid_terrace,
      up_geology: :active_fault_vein,
      accessibility: :cliff_isolated
    ]

    assert {:error, :unresolved_history} = Export.export_branch(unresolved)

    ascending =
      Keyword.put(@spec_fields, :fate_constraint, {:require, :Fate_Ascended_SpatialRift})

    assert {:error, :fate_constraint_unsatisfied} =
             Export.export_node(ascending, features: @complete_features)
  end
end
