defmodule Voy.World.ExportTest do
  use ExUnit.Case, async: true

  alias Voy.World.{Export, Profile, Witness}

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
    seed: 101,
    names: [node: "白水支沟", up: "野猪箐裂隙", pass: "断碑垭口", down: "白水坝"]
  ]

  test "a node export carries v2, history, and the sole five-hop dossier" do
    assert {:ok, bundle} = Export.export_node(@spec_fields, features: @complete_features)
    assert :ok = Export.validate(bundle)
    assert bundle["schema_version"] == 2
    assert bundle["schema_version"] == Export.schema_version()
    assert bundle["rule_set"] == "default_branch_v1"
    assert bundle["profile"]["id"] == Profile.id(Profile.default())

    assert [node] = bundle["nodes"]
    refute Map.has_key?(node, "archives")

    assert node["sites"] == %{
             "up" => "node_baishui_01/up",
             "pass" => "node_baishui_01/pass",
             "down" => "node_baishui_01/down"
           }

    assert node["topography"]["up_terrain"] == "steep_fissure_headwater"
    assert node["history"]["era3"]["fate"] == "Fate_Assimilated_NewZhai"
    assert %{"site" => "野猪箐裂隙", "description" => _} = node["structures"]["upstream"]

    dossier = node["dossier"]
    assert dossier["seed"] == 101
    assert dossier["relic"] == "ancestral_bronze_zheng"
    assert dossier["fate"] == "Fate_Assimilated_NewZhai"
    assert length(dossier["documents"]) == 7

    assert Enum.map(Enum.filter(dossier["documents"], &(&1["role"] == "main")), & &1["hop"]) == [
             1,
             2,
             3,
             4,
             5
           ]

    assert dossier["binding"]["safe_tick"] in Witness.safe_ticks()

    assert Enum.all?(
             Enum.filter(dossier["documents"], &(&1["role"] == "main")),
             &is_nil(&1["decoy_class"])
           )
  end

  test "a bundle round-trips through JSON byte for byte" do
    assert {:ok, bundle} = Export.export_node(@spec_fields, features: @complete_features)
    assert {:ok, json} = Export.to_json(bundle)
    assert {:ok, loaded} = Export.from_json(json)
    assert loaded == bundle
    assert {:ok, json2} = Export.to_json(loaded)
    assert json2 == json
  end

  test "branch export requires a seed and derives stable node identities" do
    assert {:error, :missing_seed} = Export.export_branch(baishui_topo())
    assert {:error, {:invalid_seed, "101"}} = Export.export_branch(baishui_topo(), seed: "101")
    assert {:ok, bundle} = Export.export_branch(baishui_topo(), seed: 101)
    assert :ok = Export.validate(bundle)

    assert [node] = bundle["nodes"]
    assert node["node_id"] == "branch_01_baishui"
    assert node["sites"]["up"] == "branch_01_baishui/up"
    assert node["dossier"]["seed"] == 101

    assert {:ok, renamed} = Export.export_branch(baishui_topo(), seed: 101, node_id: "node_07")
    assert hd(renamed["nodes"])["node_id"] == "node_07"
    assert hd(renamed["nodes"])["sites"]["up"] == "branch_01_baishui/up"
  end

  test "a reversed candidate can be exported without re-solving" do
    spec = [
      node_id: "node_rift_07",
      branch_id: "branch_rift",
      rule_set: :default_branch_v1,
      fate_constraint: {:require, :Fate_Ascended_SpatialRift},
      seed: 303,
      names: [node: "黑水支沟", up: "鬼哭漏斗", pass: "断碑垭口", down: "悬棺寨"]
    ]

    assert {:ok, [candidate | _rest]} = Voy.World.reverse_node(spec)
    assert {:ok, bundle} = Export.export_candidate(spec, candidate)
    assert :ok = Export.validate(bundle)
    assert [node] = bundle["nodes"]
    assert node["history"]["era3"]["fate"] == "Fate_Ascended_SpatialRift"
    assert node["dossier"]["seed"] == 303
  end

  test "two stocks produce two valid bundles for the same canon" do
    shifted =
      Profile.new(
        id: "shifted_v1",
        tau: %{tau_immediate: {0.0, 0.5}, tau_5: {1.0, 2.0}, tau_10: {4.0, 6.0}}
      )

    assert {:ok, default_bundle} = Export.export_branch(baishui_topo(), seed: 101)

    assert {:ok, shifted_bundle} =
             Export.export_branch(baishui_topo(), seed: 101, profile: shifted)

    assert default_bundle["profile"]["id"] == "proto_v1"
    assert shifted_bundle["profile"]["id"] == "shifted_v1"
    assert default_bundle["nodes"] == shifted_bundle["nodes"]
    assert :ok = Export.validate(shifted_bundle)
  end

  test "validation rejects bundles that break the v2 contract" do
    assert {:ok, bundle} = Export.export_branch(baishui_topo(), seed: 101)
    [node] = bundle["nodes"]

    assert {:error, {:invalid_bundle, {:schema_version, 1, 2}}} =
             Export.validate(%{bundle | "schema_version" => 1})

    assert {:error, {:invalid_bundle, {:duplicate_node_ids, _}}} =
             Export.validate(%{bundle | "nodes" => [node, node]})

    assert {:error, {:invalid_bundle, {:missing_node_keys, ["dossier"]}}} =
             Export.validate(%{bundle | "nodes" => [Map.delete(node, "dossier")]})

    bad_dossier = put_in(node["dossier"]["fate"], "Fate_Ascended_SpatialRift")

    assert {:error, {:invalid_bundle, {:dossier, :shape_or_history_mismatch}}} =
             Export.validate(%{bundle | "nodes" => [bad_dossier]})

    assert {:error, {:invalid_bundle, {:not_json_safe, _}}} =
             Export.validate(Map.put(bundle, "extra", {:tuple, "value"}))
  end

  test "invalid input is reported, not raised" do
    assert {:error, {:invalid_json, _}} = Export.from_json("{not json")
    assert {:error, {:invalid_bundle, _}} = Export.from_json("{\"schema_version\": 2}")
    assert {:error, {:not_json, :nope}} = Export.from_json(:nope)

    assert {:error, {:invalid_profile, :nope}} =
             Export.export_branch(baishui_topo(), seed: 101, profile: :nope)

    unsupported = [
      branch_id: :branch_broken,
      up_name: "U",
      pass_name: "P",
      down_name: "D",
      up_terrain: :steep_fissure_headwater,
      pass_terrain: :canyon_pass,
      down_terrain: :alluvial_flat,
      up_geology: :deep_stress_cavity,
      accessibility: :tractor_road_accessible
    ]

    assert {:error, :unsupported_topography} = Export.export_branch(unsupported, seed: 101)

    inaccessible_basin = [
      branch_id: :branch_inaccessible,
      up_name: "U",
      pass_name: "P",
      down_name: "D",
      up_terrain: :steep_fissure_headwater,
      pass_terrain: :canyon_pass,
      down_terrain: :alluvial_flat,
      up_geology: :active_fault_vein,
      accessibility: :cliff_isolated
    ]

    assert {:ok, bundle} = Export.export_branch(inaccessible_basin, seed: 101)
    assert [node] = bundle["nodes"]
    assert node["history"]["era3"]["fate"] == "Fate_Tolerated_FalseAlarm"

    ascending =
      Keyword.put(@spec_fields, :fate_constraint, {:require, :Fate_Ascended_SpatialRift})

    assert {:error, :fate_constraint_unsatisfied} =
             Export.export_node(ascending, features: @complete_features)
  end

  test "exporting and settling an open variation node validates and round-trips through JSON v2" do
    open_spec = Keyword.put(@spec_fields, :horizon, :open)

    assert {:ok, open_bundle} = Export.export_node(open_spec, features: @complete_features)
    assert :ok = Export.validate(open_bundle)
    assert {:ok, json} = Export.to_json(open_bundle)
    assert {:ok, decoded} = Export.from_json(json)
    assert decoded == open_bundle

    assert [open_node] = open_bundle["nodes"]
    assert open_node["horizon"] == "open"
    assert open_node["dossier"]["horizon"] == "open"
    assert open_node["dossier"]["settled"] == false
    assert length(open_node["dossier"]["documents"]) == 6
    assert length(open_node["dossier"]["reachable_transitions"]) == 4

    # Settle the exported open dossier and verify the updated bundle still validates under v2
    assert {:ok, settled_dossier} =
             Voy.World.settle_open_dossier(
               open_node["dossier"],
               :reanchor_and_dampen,
               names: open_node["names"]
             )

    settled_node = %{open_node | "dossier" => settled_dossier}
    settled_bundle = %{open_bundle | "nodes" => [settled_node]}

    assert :ok = Export.validate(settled_bundle)
    assert {:ok, settled_json} = Export.to_json(settled_bundle)
    assert {:ok, settled_decoded} = Export.from_json(settled_json)
    assert settled_decoded == settled_bundle
  end
end
