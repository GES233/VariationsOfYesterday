defmodule Voy.World.GoldenTest do
  use ExUnit.Case, async: true

  alias Voy.World.TestFixtures

  @cases JSON.decode!(File.read!(Path.join(__DIR__, "../../fixtures/world_cases.json")))

  test "forward golden cases preserve their semantic output" do
    for %{"topography" => topo, "expected" => expected} <- @cases["forward"] do
      assert {:ok, [slice]} = Voy.World.compile_branch(topo)
      assert slice["era3"]["fate"] == expected["fate"]
      assert slice["era3"]["final_regime"] == expected["regime"]
      assert slice["era1"]["guardian_relic"] == expected["relic"]
      assert slice["era2b"]["disturbance_mode"] == expected["disturbance"]
      assert is_map(slice["structures"])
      refute Map.has_key?(slice, "archives")
    end
  end

  test "reverse golden cases preserve the required terrain signatures" do
    for %{"fate" => fate, "expected_topographies" => expected} <- @cases["reverse"] do
      assert {:ok, slices} = Voy.World.reverse_worldgen(fate)

      actual = Enum.map(slices, &TestFixtures.public_feature_signature(&1["topography"]))
      assert MapSet.new(actual) == MapSet.new(expected)
    end
  end

  test "the seeded dossier golden preserves concrete evidence" do
    case_data = @cases["dossier"]
    expected = case_data["expected"]

    dossier =
      Voy.World.Witness.render_dossier(
        case_data["seed"],
        case_data["branch_id"],
        String.to_existing_atom(case_data["relic"]),
        String.to_existing_atom(case_data["fate"]),
        %{up: "野猪箐裂隙", pass: "断碑垭口", down: "白水坝"}
      )

    assert Map.take(dossier.binding, [
             :batch_id,
             :vessel_name,
             :vessel_code,
             :safe_tick,
             :safe_phase,
             :safe_delay
           ]) ==
             %{
               batch_id: expected["batch_id"],
               vessel_name: expected["vessel_name"],
               vessel_code: expected["vessel_code"],
               safe_tick: expected["safe_tick"],
               safe_phase: expected["safe_phase"],
               safe_delay: expected["safe_delay"]
             }
  end
end
