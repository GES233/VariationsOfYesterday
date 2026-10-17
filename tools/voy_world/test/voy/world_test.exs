defmodule Voy.WorldTest do
  use ExUnit.Case, async: true

  alias Voy.World.TestFixtures

  test "public compilation returns JSON-safe maps with stable keys" do
    assert {:ok, [slice]} = Voy.World.compile_branch(TestFixtures.baishui_topo())
    assert slice["branch_id"] == "branch_01_baishui"
    assert is_map(slice["era3"])
    assert is_map(slice["archives"])
    assert Map.has_key?(slice["archives"], "official_report")
    assert JSON.encode!(slice)
  end

  test "public reverse query validates the requested fate" do
    assert {:error, :unknown_fate} = Voy.World.reverse_worldgen(:not_a_fate)
    assert {:error, :unknown_fate} = Voy.World.reverse_worldgen("not_a_fate")
  end
end
