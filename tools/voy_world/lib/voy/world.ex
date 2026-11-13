defmodule Voy.World do
  @moduledoc """
  Clean entry point for the `Voy.World` relational graph and epistemic sandbox.
  """

  alias Voy.World.{Epistemic, Graph, Store}

  def load_world(dir \\ Store.default_dir()), do: Store.load(dir)
  def save_world(payload, dir \\ Store.default_dir()), do: Store.save(payload, dir)

  def build_graph(nodes, edges, translations \\ []),
    do: Graph.new(nodes, edges, translations)

  def audit(eras, nodes, edges, translations \\ []) do
    graph = Graph.new(nodes, edges, translations)
    Epistemic.audit_world(eras, graph)
  end

  def check_wire(eras, nodes, edges, edge_a_id, edge_b_id, translations \\ []) do
    graph = Graph.new(nodes, edges, translations)
    Epistemic.check_wire(eras, graph, edge_a_id, edge_b_id)
  end

  def query_paths(eras, nodes, edges, start_node, target_node, opts \\ []) do
    {translations, path_opts} = Keyword.pop(opts, :translations, [])
    graph = Graph.new(nodes, edges, translations)
    Epistemic.query_paths(eras, graph, start_node, target_node, path_opts)
  end
end
