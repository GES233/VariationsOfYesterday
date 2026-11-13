defmodule Voy.World.StoreTest do
  use ExUnit.Case, async: true

  alias Voy.World.Store

  test "save and reload roundtrip in a temp directory" do
    tmp = Path.join(System.tmp_dir!(), "voy_world_store_#{System.unique_integer([:positive])}")
    File.mkdir_p!(tmp)

    on_exit(fn -> File.rm_rf!(tmp) end)

    world = Store.load!()

    assert :ok =
             Store.save(
               %{
                 "eras" => world.eras,
                 "nodes" => world.raw_nodes,
                 "edges" => world.raw_edges
               },
               tmp
             )

    assert {:ok, reloaded} = Store.load(tmp)
    assert length(reloaded.eras) == length(world.eras)
    assert length(reloaded.raw_nodes) == length(world.raw_nodes)
    assert length(reloaded.raw_edges) == length(world.raw_edges)
  end
end
