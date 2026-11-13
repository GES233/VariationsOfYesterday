defmodule Voy.World.GraphTest do
  use ExUnit.Case, async: true
  use Voy.Kanren

  alias Voy.World.Graph

  setup do
    graph =
      Graph.new(
        [
          %{id: "a", kind: "origin", era: "e1", label: "起点"},
          %{id: "b", kind: "relic", era: "e1", label: "铜钲"},
          %{id: "b_alias", kind: "tax_item", era: "e2", label: "旧铜器"},
          %{id: "c", kind: "vessel", era: "e2", label: "气缸"},
          %{id: "x", kind: "deadend", era: "e2", label: "死胡同"}
        ],
        [
          %{id: "e1", from: "a", rel: "becomes", to: "b", era: "e1", observer: "headman"},
          %{
            id: "e2",
            from: "b_alias",
            rel: "cast_into",
            to: "c",
            era: "e2",
            observer: "workshop"
          },
          %{id: "e_dead", from: "b", rel: "shipped_to", to: "x", era: "e2", observer: "logistics"}
        ],
        [
          %{
            id: "tr_1",
            from_node: "b",
            to_node: "b_alias",
            gloss: "铜钲在税册上叫旧铜器",
            reversible: false
          }
        ]
      )

    {:ok, graph: graph}
  end

  test "nodeo and edgeo match open attributes via propso", %{graph: graph} do
    assert ["b"] ==
             run_all(fn q -> Graph.nodeo(graph, q, "relic", "e1") end) |> terms()

    assert ["e2"] ==
             run_all(fn q ->
               Graph.edgeo(graph, q, "b_alias", "cast_into", "c", observer: "workshop")
             end)
             |> terms()
  end

  test "patho and bridgeo traverse cross-era cognitive translations via aliaso", %{graph: graph} do
    assert [["e1", "e2"]] == Graph.find_paths(graph, "a", "c")
    assert [] == Graph.find_paths(graph, "a", "missing")

    assert {:ok, %{kind: "translation", translation_id: "tr_1"}} =
             Graph.check_bridge(graph, "e1", "e2")

    assert {:error, %{code: "port_mismatch"}} = Graph.check_bridge(graph, "e_dead", "e2")
    assert {:error, %{code: "self_loop"}} = Graph.check_bridge(graph, "e1", "e1")
  end
end
