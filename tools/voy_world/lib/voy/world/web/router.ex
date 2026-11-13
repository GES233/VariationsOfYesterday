defmodule Voy.World.Web.Router do
  @moduledoc """
  Plug router for the `Voy.World` card-wiring and epistemic sandbox WebUI.
  """

  use Plug.Router

  alias Voy.World.{Epistemic, Graph, Store}

  @static_dir Path.expand("../../../../priv/static", __DIR__)

  plug Plug.Static,
    at: "/static",
    from: @static_dir

  plug :match

  plug Plug.Parsers,
    parsers: [:json],
    pass: ["application/json"],
    json_decoder: JSON

  plug :dispatch

  get "/" do
    index_path = Path.join(@static_dir, "index.html")

    conn
    |> put_resp_content_type("text/html; charset=utf-8")
    |> send_resp(200, File.read!(index_path))
  end

  get "/api/world" do
    world = Store.load!()
    audit = Epistemic.audit_world(world.eras, world.graph)

    send_json(conn, 200, %{
      ok: true,
      eras: world.eras,
      nodes: world.raw_nodes,
      edges: world.raw_edges,
      audit: audit
    })
  end

  post "/api/world/audit" do
    {eras, raw_nodes, raw_edges} = extract_world(conn.body_params)
    graph = Graph.new(raw_nodes, raw_edges)
    audit = Epistemic.audit_world(eras, graph)
    send_json(conn, 200, %{ok: true, audit: audit})
  end

  post "/api/world/wire" do
    params = conn.body_params
    {eras, raw_nodes, raw_edges} = extract_world(params)
    graph = Graph.new(raw_nodes, raw_edges)
    from_id = Map.get(params, "from_edge_id", "")
    to_id = Map.get(params, "to_edge_id", "")

    case Epistemic.check_wire(eras, graph, from_id, to_id) do
      {:ok, %{bridge: bridge, warnings: warnings}} ->
        send_json(conn, 200, %{
          ok: true,
          valid: true,
          bridge: bridge,
          warnings: warnings,
          issues: []
        })

      {:error, issues} ->
        send_json(conn, 200, %{
          ok: true,
          valid: false,
          bridge: nil,
          warnings: [],
          issues: issues
        })
    end
  end

  post "/api/world/paths" do
    params = conn.body_params
    {eras, raw_nodes, raw_edges} = extract_world(params)
    graph = Graph.new(raw_nodes, raw_edges)
    start_node = Map.get(params, "start_node", "")
    target_node = Map.get(params, "target_node", "")

    result = Epistemic.query_paths(eras, graph, start_node, target_node)

    send_json(conn, 200, %{
      ok: true,
      start_node: start_node,
      target_node: target_node,
      valid_paths: result.valid_paths,
      rejected_paths: result.rejected_paths
    })
  end

  post "/api/world/save" do
    {eras, raw_nodes, raw_edges} = extract_world(conn.body_params)
    payload = %{"eras" => eras, "nodes" => raw_nodes, "edges" => raw_edges}

    case Store.save(payload) do
      :ok ->
        graph = Graph.new(raw_nodes, raw_edges)
        audit = Epistemic.audit_world(eras, graph)
        send_json(conn, 200, %{ok: true, audit: audit})

      {:error, reason} ->
        send_json(conn, 500, %{ok: false, error: inspect(reason)})
    end
  end

  post "/api/world/reset" do
    world = Store.load!()
    audit = Epistemic.audit_world(world.eras, world.graph)

    send_json(conn, 200, %{
      ok: true,
      eras: world.eras,
      nodes: world.raw_nodes,
      edges: world.raw_edges,
      audit: audit
    })
  end

  match _ do
    send_json(conn, 404, %{ok: false, error: "not_found"})
  end

  defp extract_world(params) do
    eras = Map.get(params, "eras") || []
    nodes = Map.get(params, "nodes") || []
    edges = Map.get(params, "edges") || []
    {eras, nodes, edges}
  end

  defp send_json(conn, status, data) do
    conn
    |> put_resp_content_type("application/json; charset=utf-8")
    |> send_resp(status, JSON.encode!(data))
  end
end
