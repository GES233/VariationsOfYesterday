defmodule Voy.World.Web.RouterTest do
  use ExUnit.Case, async: true
  import Plug.Test
  import Plug.Conn

  alias Voy.World.{Store, Web.Router}

  @opts Router.init([])

  defp get_req(path), do: conn(:get, path) |> Router.call(@opts)

  defp post_json(path, body) do
    conn(:post, path, JSON.encode!(body))
    |> put_req_header("content-type", "application/json")
    |> Router.call(@opts)
  end

  test "GET / serves the card-wiring sandbox HTML and static assets" do
    html = get_req("/")
    assert html.status == 200
    assert String.contains?(html.resp_body, "卡片接线与时代认知推演台")

    assert get_req("/static/app.css").status == 200
    assert get_req("/static/app.js").status == 200
  end

  test "GET /api/world and POST /api/world/audit return graph, eras, and audit summary" do
    res = get_req("/api/world")
    assert res.status == 200
    data = JSON.decode!(res.resp_body)
    assert data["ok"] == true
    assert length(data["eras"]) == 4
    assert data["audit"]["summary"]["error_edges"] == 2

    audit_res =
      post_json("/api/world/audit", %{
        eras: data["eras"],
        nodes: data["nodes"],
        edges: data["edges"],
        translations: data["translations"]
      })

    assert audit_res.status == 200
    assert JSON.decode!(audit_res.resp_body)["audit"]["summary"]["nodes"] == length(data["nodes"])
    assert JSON.decode!(audit_res.resp_body)["audit"]["summary"]["translations"] == 2
  end

  test "POST /api/world/wire validates port unification, translations, and epistemic taboos" do
    world = Store.load!()

    base = %{
      eras: world.eras,
      nodes: world.raw_nodes,
      edges: world.raw_edges,
      translations: world.raw_translations
    }

    tr_wire = post_json("/api/world/wire", Map.merge(base, %{from_edge_id: "card_01", to_edge_id: "card_02"}))
    tr_data = JSON.decode!(tr_wire.resp_body)
    assert tr_data["valid"] == true
    assert tr_data["bridge"]["kind"] == "translation"
    assert tr_data["bridge"]["translation_id"] == "tr_zheng_to_tax_bronze"

    exact_wire = post_json("/api/world/wire", Map.merge(base, %{from_edge_id: "card_02", to_edge_id: "card_03"}))
    exact_data = JSON.decode!(exact_wire.resp_body)
    assert exact_data["valid"] == true
    assert exact_data["bridge"]["kind"] == "exact"
    assert exact_data["bridge"]["from_node"] == "node_omen"

    bad_port = post_json("/api/world/wire", Map.merge(base, %{from_edge_id: "card_03", to_edge_id: "decoy_iron"}))
    bad_data = JSON.decode!(bad_port.resp_body)
    assert bad_data["valid"] == false
    assert Enum.any?(bad_data["issues"], &(&1["code"] == "port_mismatch"))

    taboo_wire = post_json("/api/world/wire", Map.merge(base, %{from_edge_id: "card_04", to_edge_id: "card_hq_taboo_demo"}))
    taboo_data = JSON.decode!(taboo_wire.resp_body)
    assert taboo_data["valid"] == false
    assert Enum.any?(taboo_data["issues"], &(&1["code"] == "taboo_violation"))
  end

  test "POST /api/world/paths returns valid and rejected paths across translations" do
    world = Store.load!()

    res =
      post_json("/api/world/paths", %{
        eras: world.eras,
        nodes: world.raw_nodes,
        edges: world.raw_edges,
        translations: world.raw_translations,
        start_node: "node_ritual_origin",
        target_node: "node_defibrillation"
      })

    assert res.status == 200
    data = JSON.decode!(res.resp_body)
    assert ["card_01", "card_02", "card_03", "card_04", "card_05", "card_06"] in data["valid_paths"]
    assert data["rejected_paths"] != []
  end
end
