defmodule Voy.World.Store do
  @moduledoc """
  Human-editable JSON persistence for `world/eras.json` and `world/graph.json`.
  """

  alias Voy.World.Graph

  @default_dir Path.expand("../../../world", __DIR__)

  def default_dir, do: System.get_env("VOY_WORLD_DIR") || @default_dir

  @doc "Load `{eras, graph}` from `dir`."
  @spec load(Path.t()) :: {:ok, %{eras: [map()], graph: Graph.t(), raw_nodes: [map()], raw_edges: [map()]}} | {:error, term()}
  def load(dir \\ default_dir()) do
    eras_path = Path.join(dir, "eras.json")
    graph_path = Path.join(dir, "graph.json")

    with {:ok, eras_bin} <- File.read(eras_path),
         {:ok, graph_bin} <- File.read(graph_path),
         {:ok, eras_json} <- decode_json(eras_bin),
         {:ok, graph_json} <- decode_json(graph_bin) do
      eras = Map.get(eras_json, "eras", [])
      raw_nodes = Map.get(graph_json, "nodes", [])
      raw_edges = Map.get(graph_json, "edges", [])
      graph = Graph.new(raw_nodes, raw_edges)

      {:ok, %{eras: eras, graph: graph, raw_nodes: raw_nodes, raw_edges: raw_edges}}
    end
  end

  def load!(dir \\ default_dir()) do
    case load(dir) do
      {:ok, world} -> world
      {:error, reason} -> raise "cannot load world from #{dir}: #{inspect(reason)}"
    end
  end

  @doc "Persist `{eras, nodes, edges}` cleanly back to `eras.json` and `graph.json`."
  @spec save(map(), Path.t()) :: :ok | {:error, term()}
  def save(payload, dir \\ default_dir()) when is_map(payload) do
    File.mkdir_p!(dir)
    eras = Map.get(payload, "eras") || Map.get(payload, :eras) || []
    nodes = Map.get(payload, "nodes") || Map.get(payload, :nodes) || []
    edges = Map.get(payload, "edges") || Map.get(payload, :edges) || []

    eras_json = encode_pretty(%{"eras" => eras})
    graph_json = encode_pretty(%{"nodes" => nodes, "edges" => edges})

    with :ok <- File.write(Path.join(dir, "eras.json"), eras_json <> "\n"),
         :ok <- File.write(Path.join(dir, "graph.json"), graph_json <> "\n") do
      :ok
    end
  end

  defp decode_json(bin) do
    {:ok, JSON.decode!(bin)}
  rescue
    e -> {:error, {:invalid_json, Exception.message(e)}}
  end

  defp encode_pretty(term) do
    term
    |> JSON.encode_to_iodata!()
    |> IO.iodata_to_binary()
    |> format_json_pretty()
  end

  # Lightweight readable JSON formatter with zero external deps.
  defp format_json_pretty(compact) do
    chars = String.graphemes(compact)
    do_format(chars, 0, false, false, [])
  end

  defp do_format([], _indent, _in_str, _escaped, acc),
    do: acc |> Enum.reverse() |> IO.iodata_to_binary()

  defp do_format([c | rest], indent, true, true, acc),
    do: do_format(rest, indent, true, false, [c | acc])

  defp do_format(["\\" | rest], indent, true, false, acc),
    do: do_format(rest, indent, true, true, ["\\" | acc])

  defp do_format(["\"" | rest], indent, true, false, acc),
    do: do_format(rest, indent, false, false, ["\"" | acc])

  defp do_format([c | rest], indent, true, false, acc),
    do: do_format(rest, indent, true, false, [c | acc])

  defp do_format(["\"" | rest], indent, false, false, acc),
    do: do_format(rest, indent, true, false, ["\"" | acc])

  defp do_format([open | [close | rest]], indent, false, false, acc)
       when (open == "{" and close == "}") or (open == "[" and close == "]"),
       do: do_format(rest, indent, false, false, [close, open | acc])

  defp do_format([open | rest], indent, false, false, acc) when open in ["{", "["] do
    next = indent + 2
    do_format(rest, next, false, false, [pad(next), "\n", open | acc])
  end

  defp do_format([close | rest], indent, false, false, acc) when close in ["}", "]"] do
    next = max(indent - 2, 0)
    do_format(rest, next, false, false, [close, pad(next), "\n" | acc])
  end

  defp do_format(["," | rest], indent, false, false, acc),
    do: do_format(rest, indent, false, false, [pad(indent), "\n", "," | acc])

  defp do_format([":" | rest], indent, false, false, acc),
    do: do_format(rest, indent, false, false, [" ", ":" | acc])

  defp do_format([c | rest], indent, false, false, acc),
    do: do_format(rest, indent, false, false, [c | acc])

  defp pad(n), do: String.duplicate(" ", n)
end
