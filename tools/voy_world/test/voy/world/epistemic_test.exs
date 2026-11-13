defmodule Voy.World.EpistemicTest do
  use ExUnit.Case, async: true

  alias Voy.World.{Epistemic, Store}

  setup do
    {:ok, world: Store.load!()}
  end

  test "main chain cards pass epistemic checks and form a valid pathway", %{world: world} do
    assert Epistemic.valid_pathway?(world.eras, world.graph, [
             "card_01",
             "card_02",
             "card_03",
             "card_04",
             "card_05",
             "card_06"
           ])

    res = Epistemic.query_paths(world.eras, world.graph, "node_ritual_origin", "node_defibrillation")
    assert ["card_01", "card_02", "card_03", "card_04", "card_05", "card_06"] in res.valid_paths
  end

  test "anachronistic concept and text are caught against the era model", %{world: world} do
    bad_card = Enum.find(world.graph.edges, &(&1[:id] == "card_anachronism_demo"))
    assert {:error, issues} = Epistemic.check_edge(world.eras, world.graph, bad_card)
    assert Enum.any?(issues, &(&1.code == "anachronistic_concept"))
  end

  test "official_hq taboo firewall (neq) rejects engineering attribution", %{world: world} do
    taboo_card = Enum.find(world.graph.edges, &(&1[:id] == "card_hq_taboo_demo"))
    assert {:error, issues} = Epistemic.check_edge(world.eras, world.graph, taboo_card)
    assert Enum.any?(issues, &(&1.code == "taboo_violation"))

    coverup = Enum.find(world.graph.edges, &(&1[:id] == "card_hq_coverup"))
    assert :ok == Epistemic.check_edge(world.eras, world.graph, coverup)
  end

  test "decoy cards fail port unification and rejected paths explain why", %{world: world} do
    assert {:error, issues} = Epistemic.check_wire(world.eras, world.graph, "card_03", "decoy_iron")
    assert Enum.any?(issues, &(&1.code == "port_mismatch"))

    res = Epistemic.query_paths(world.eras, world.graph, "node_ritual_origin", "node_defibrillation")
    assert Enum.any?(res.rejected_paths, fn r -> "card_anachronism_demo" in r.path end)
  end

  test "modifying an era's forbidden_concepts immediately changes wire validity", %{world: world} do
    relaxed_eras =
      Enum.map(world.eras, fn era ->
        if era["id"] == "era1_ancestral" do
          %{era | "forbidden_concepts" => []}
        else
          era
        end
      end)

    assert {:ok, _} = Epistemic.check_wire(relaxed_eras, world.graph, "card_01", "card_anachronism_demo")
  end
end
