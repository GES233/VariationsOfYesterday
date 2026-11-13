defmodule Voy.World.Graph do
  @moduledoc """
  mediKanren-style open property-graph DSL built directly on `Voy.Kanren`.

  Supports both exact node matching and cross-era cognitive aliases
  (`aliaso/4` / `translations`), mirroring mediKanren's `same-as` expansion.
  """

  use Voy.Kanren

  @type node_term :: keyword()
  @type edge_term :: keyword()
  @type translation_term :: keyword()
  @type t :: %{nodes: [node_term()], edges: [edge_term()], translations: [translation_term()]}

  @doc "Normalize raw JSON/map world data into Kanren-friendly keyword records."
  @spec new([map() | keyword()], [map() | keyword()], [map() | keyword()]) :: t()
  def new(nodes, edges, translations \\ [])
      when is_list(nodes) and is_list(edges) and is_list(translations) do
    %{
      nodes: Enum.map(nodes, &normalize_node/1),
      edges: Enum.map(edges, &normalize_edge/1),
      translations: Enum.map(translations, &normalize_translation/1)
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
  Relational cross-era cognitive alias (`same-as` / translation).

  Unifies when `node_a` and `node_b` are identical (`:exact`), or bridged by a
  declared translation entry in `graph.translations` (`:translation`).
  """
  def aliaso(%{translations: translations}, node_a, node_b, meta) do
    fresh [tr, tr_id, gloss] do
      any([
        all([
          eq(node_a, node_b),
          eq(meta, kind: :exact, id: nil, gloss: "同节点直连")
        ]),
        all([
          membero(tr, translations),
          propso(tr, id: tr_id, from_node: node_a, to_node: node_b, gloss: gloss),
          eq(meta, kind: :translation, id: tr_id, gloss: gloss)
        ]),
        all([
          membero(tr, translations),
          propso(tr, id: tr_id, from_node: node_b, to_node: node_a, reversible: true, gloss: gloss),
          eq(meta, kind: :translation, id: tr_id, gloss: gloss)
        ])
      ])
    end
  end

  @doc """
  Relational path search from `start_id` to `target_id`.

  Traverses edges via exact endpoint equality or declared cognitive aliases
  (`aliaso/4`), returning ordered edge IDs `[edge_id, ...]`.
  """
  def patho(graph, start_id, target_id, path, opts \\ []) do
    max_depth = Keyword.get(opts, :max_depth, 6)
    do_patho(graph, start_id, target_id, [start_id], path, max_depth)
  end

  defp do_patho(graph, current, target, _visited, path, 0) do
    fresh [meta] do
      all([aliaso(graph, current, target, meta), eq(path, [])])
    end
  end

  defp do_patho(graph, current, target, visited, path, depth) when depth > 0 do
    fresh [edge, edge_from, next_node, edge_id, rel, rest, meta_end, meta_step] do
      any([
        all([aliaso(graph, current, target, meta_end), eq(path, [])]),
        all([
          aliaso(graph, current, edge_from, meta_step),
          membero(edge, graph.edges),
          propso(edge, id: edge_id, from: edge_from, rel: rel, to: next_node),
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
  with `edge_b.from` directly or via a cognitive translation (`aliaso/4`).
  """
  def bridgeo(graph, edge_a_id, edge_b_id, bridge_info) do
    fresh [from_a, rel_a, out_a, in_b, rel_b, to_b, meta] do
      neq(edge_a_id, edge_b_id)
      edgeo(graph, edge_a_id, from_a, rel_a, out_a)
      edgeo(graph, edge_b_id, in_b, rel_b, to_b)
      aliaso(graph, out_a, in_b, meta)
      eq(bridge_info, from_node: out_a, to_node: in_b, via: meta)
    end
  end

  @doc "Check whether two specific edge IDs unify directly or via a translation bridge."
  @spec check_bridge(t(), String.t(), String.t()) ::
          {:ok, map()} | {:error, map()}
  def check_bridge(graph, edge_a_id, edge_b_id) do
    matches =
      run_all(fn q ->
        bridgeo(graph, edge_a_id, edge_b_id, q)
      end)
      |> terms()

    case matches do
      [info | _] ->
        via = info[:via]

        {:ok,
         %{
           from_node: info[:from_node],
           to_node: info[:to_node],
           kind: Atom.to_string(via[:kind]),
           translation_id: via[:id],
           gloss: via[:gloss]
         }}

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
               message:
                 "端口不匹配：左卡输出「#{edge_a[:to]}」，右卡需要输入「#{edge_b[:from]}」（无同名直连，也未声明跨时代认知转译）"
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

  @doc "Look up a normalized translation by ID."
  def find_translation(%{translations: translations}, id),
    do: Enum.find(translations, &(&1[:id] == id))

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

  def normalize_translation(raw) when is_list(raw) do
    if Keyword.keyword?(raw), do: raw, else: raise(ArgumentError, "invalid translation keyword")
  end

  def normalize_translation(raw) when is_map(raw) do
    [
      id: fetch_str(raw, "id", :id),
      from_node: fetch_str(raw, "from_node", :from_node),
      to_node: fetch_str(raw, "to_node", :to_node),
      gloss: fetch_str(raw, "gloss", :gloss, ""),
      reversible: fetch_bool(raw, "reversible", :reversible, false),
      retrospective: fetch_bool(raw, "retrospective", :retrospective, false)
    ]
  end

  defp fetch_str(map, str_key, atom_key, default \\ "") do
    case Map.fetch(map, atom_key) do
      {:ok, v} when not is_nil(v) ->
        to_string(v)

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
