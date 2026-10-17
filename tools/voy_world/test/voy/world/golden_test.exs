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
      assert slice["archives"]["official_report"]["claimed_cause"] == expected["official_cause"]
      assert slice["archives"]["tech_note"]["true_cause"] == expected["true_cause"]
    end
  end

  test "reverse golden cases preserve the required terrain signatures" do
    for %{"fate" => fate, "expected_topographies" => expected} <- @cases["reverse"] do
      assert {:ok, slices} = Voy.World.reverse_worldgen(fate)

      actual = Enum.map(slices, &TestFixtures.public_feature_signature(&1["topography"]))
      assert MapSet.new(actual) == MapSet.new(expected)
    end
  end
end
