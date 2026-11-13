defmodule Voy.World.Graph do
  @moduledoc """
  mediKanren-style open property-graph DSL built directly on `Voy.Kanren`.

  Entities are represented as keyword lists so `propso/2` can unify any subset
  of attributes without rigid schemas.
  """

  use Voy.Kanren

  @type node_term :: keyword()
  @type edge_term :: keyword()
  @type t :: %{nodes: [node_term()], edges: [edge_term()]}

  @doc "Normalize raw JSON/map world data into Kanren-friendly keyword records."
  @spec new([map() | keyword()], [map() | keyword()]) :: t()
  def new(nodes, edges) when is_list(nodes) and is_list(edges) do
    %{
      nodes: Enum.map(nodes, &normalize_node/1),
      edges: Enum.map(edges, &normalize_edge/1)
    }
  end

  @doc "Relational node lookup: unifies `id`, `kind`, `era`, and open `props`."
  def nodeo(%{nodes: nodes}, id, kind, era, props_pattern \\ []) do
    fresh [node] do
      membero(node, nodes)
      propso(node, [id: id, kind: kind, era: era] ++ props_pattern)
    end
  end

  @doc "Relational edge lookup: unifies `id`, `from`, `rel`, `to`, and open `props`."
  def edgeo(%{edges: edges}, id, from, rel, to, props_pattern \\ []) do
    fresh [edge] do
      membero(edge, edges)
      propso(edge, [id: id, from: from, rel: rel, to: to] ++ props_pattern)
    end
  end

  @doc """
  Relational path search from `start_id` to `target_id`.

  Unifies `path` with the ordered list of edge IDs (`[edge_id, ...]`).
  Cycle-free via `visited` and bounded by `max_depth` (default `6`).
  """
  def patho(graph, start_id, target_id, path, opts \\ []) do
    max_depth = Keyword.get(opts, :max_depth, 6)
    do_patho(graph, start_id, target_id, [start_id], path, max_depth)
  end

  defp do_patho(_graph, current, target, _visited, path, 0) do
    all([eq(current, target), eq(path, [])])
  end

  defp do_patho(graph, current, target, visited, path, depth) when depth > 0 do
    fresh [edge, next_node, edge_id, rel, rest] do
      any([
        all([eq(current, target), eq(path, [])]),
        all([
          membero(edge, graph.edges),
          propso(edge, id: edge_id, from: current, rel: rel, to: next_node),
          not_visitedo(next_node, visited),
          eq(path, [edge_id | rest]),
          delay(fn ->
            do_patho(graph, next_node, target, [next_node | visited], rest, depth - 1)
          end)
        ])
      ])
    end
  end

  defp not_visitedo(_node, []), do: fn state -> unit(state) end

  defp not_visitedo(node, [seen | rest]) do
    all([neq(node, seen), not_visitedo(node, rest)])
  end

  @doc """
  Relational bridge between two cards (`edge_a -> edge_b`) unifying `edge_a.to`
  with `edge_b.from` at `shared_node`.
  """
  def bridgeo(graph, edge_a_id, edge_b_id, shared_node) do
    fresh [from_a, rel_a, rel_b, to_b] do
      neq(edge_a_id, edge_b_id)
      edgeo(graph, edge_a_id, from_a, rel_a, shared_node)
      edgeo(graph, edge_b_id, shared_node, rel_b, to_b)
    end
  end

  @doc "Check whether two specific edge IDs unify at a shared port node."
  @spec check_bridge(t(), String.t(), String.t()) ::
          {:ok, String.t()} | {:error, map()}
  def check_bridge(graph, edge_a_id, edge_b_id) do
    matches =
      run_all(fn q ->
        bridgeo(graph, edge_a_id, edge_b_id, q)
      end)
      |> terms()

    case matches do
      [shared | _] ->
        {:ok, shared}

      [] ->
        edge_a = find_edge(graph, edge_a_id)
        edge_b = find_edge(graph, edge_b_id)

        cond do
          edge_a_id == edge_b_id ->
            {:error, %{code: "self_loop", message: "卡片不能连接到自身"}}

          edge_a == nil or edge_b == nil ->
            {:error, %{code: "missing_edge", message: "找不到指定的卡片"}}

          true ->
            {:error,
             %{
               code: "port_mismatch",
               expected: edge_b[:from],
               actual: edge_a[:to],
               message: "端口不匹配：左卡输出「#{edge_a[:to]}」，右卡需要输入「#{edge_b[:from]}」"
             }}
        end
    end
  end

  @doc "Return all simple paths of edge IDs from `start_id` to `target_id`."
  @spec find_paths(t(), String.t(), String.t(), keyword()) :: [[String.t()]]
  def find_paths(graph, start_id, target_id, opts \\ []) do
    run_all(fn q -> patho(graph, start_id, target_id, q, opts) end)
    |> terms()
    |> Enum.reject(&(&1 == []))
    |> Enum.uniq()
  end

  @doc "Look up a normalized node by ID."
  def find_node(%{nodes: nodes}, id), do: Enum.find(nodes, &(&1[:id] == id))

  @doc "Look up a normalized edge by ID."
  def find_edge(%{edges: edges}, id), do: Enum.find(edges, &(&1[:id] == id))

  def normalize_node(raw) when is_list(raw) do
    if Keyword.keyword?(raw), do: raw, else: raise(ArgumentError, "invalid node keyword")
  end

  def normalize_node(raw) when is_map(raw) do
    [
      id: fetch_str(raw, "id", :id),
      kind: fetch_str(raw, "kind", :kind, "entity"),
      era: fetch_str(raw, "era", :era, ""),
      label: fetch_str(raw, "label", :label, fetch_str(raw, "id", :id)),
      note: fetch_str(raw, "note", :note, "")
    ]
  end

  def normalize_edge(raw) when is_list(raw) do
    if Keyword.keyword?(raw), do: raw, else: raise(ArgumentError, "invalid edge keyword")
  end

  def normalize_edge(raw) when is_map(raw) do
    [
      id: fetch_str(raw, "id", :id),
      from: fetch_str(raw, "from", :from),
      rel: fetch_str(raw, "rel", :rel),
      to: fetch_str(raw, "to", :to),
      era: fetch_str(raw, "era", :era),
      observer: fetch_str(raw, "observer", :observer, "unspecified"),
      title: fetch_str(raw, "title", :title, ""),
      body: fetch_str(raw, "body", :body, ""),
      concepts: fetch_list(raw, "concepts", :concepts),
      retrospective: fetch_bool(raw, "retrospective", :retrospective, false),
      on_board: fetch_bool(raw, "on_board", :on_board, true),
      x: fetch_num(raw, "x", :x, 80),
      y: fetch_num(raw, "y", :y, 80)
    ]
  end

  defp fetch_str(map, str_key, atom_key, default \\ "") do
    case Map.fetch(map, atom_key) do
      {:ok, v} when not is_nil(v) -> to_string(v)
      _ ->
        case Map.fetch(map, str_key) do
          {:ok, v} when not is_nil(v) -> to_string(v)
          _ -> default
        end
    end
  end

  defp fetch_list(map, str_key, atom_key) do
    val = Map.get(map, atom_key) || Map.get(map, str_key) || []
    Enum.map(val, &to_string/1)
  end

  defp fetch_bool(map, str_key, atom_key, default) do
    cond do
      Map.has_key?(map, atom_key) -> truthy?(Map.get(map, atom_key))
      Map.has_key?(map, str_key) -> truthy?(Map.get(map, str_key))
      true -> default
    end
  end

  defp fetch_num(map, str_key, atom_key, default) do
    val = Map.get(map, atom_key) || Map.get(map, str_key)
    if is_number(val), do: val, else: default
  end

  defp truthy?(true), do: true
  defp truthy?("true"), do: true
  defp truthy?(_), do: false
end
