defmodule Voy.World.Epistemic do
  @moduledoc """
  Human-editable Epoch & Epistemic Model layer over `Voy.Kanren` and `Voy.World.Graph`.

  Each Era defines how humans/institutions in that period modeled the mountain:
    * `order` — chronological rank (1, 2, 3...)
    * `granularity` — modeling granularity description
    * `allowed_concepts` — concepts observable/expressible in this era
    * `forbidden_concepts` — anachronistic concepts that must NOT appear
    * `taboos` — institutional `neq/2` firewalls (`observer + rel + target_kind`)
  """

  use Voy.Kanren

  alias Voy.World.Graph

  @type issue :: %{
          code: String.t(),
          severity: String.t(),
          message: String.t(),
          edge_id: String.t() | nil
        }

  @doc "Relational concept firewall: succeeds iff `concept` unifies with none of `forbidden`."
  def concept_not_forbiddeno(concept, forbidden_list) do
    all(Enum.map(forbidden_list, fn bad -> neq(concept, bad) end))
  end

  @doc """
  Relational institutional taboo (`neq/2` firewall): succeeds iff `(observer, rel, target_kind)`
  does not violate any declared taboo in the era.
  """
  def taboo_clearo(observer, rel, target_kind, taboos) do
    goals =
      Enum.map(taboos, fn taboo ->
        t_obs = taboo_field(taboo, :observer)
        t_rel = taboo_field(taboo, :rel)
        t_kind = taboo_field(taboo, :target_kind)

        fn state ->
          # Succeeds unless observer, rel, and target_kind ALL match the taboo tuple.
          case Voy.Kanren.Core.unify({observer, rel, target_kind}, {t_obs, t_rel, t_kind}, state) do
            nil -> unit(state)
            _matched -> mzero()
          end
        end
      end)

    all(goals)
  end

  @doc "Check a single edge/card against its era's vocabulary, body text, and observer taboos."
  @spec check_edge([map()], Graph.t(), keyword()) :: :ok | {:error, [issue()]}
  def check_edge(eras, graph, edge) do
    era_id = edge[:era]
    edge_id = edge[:id]

    case find_era(eras, era_id) do
      nil ->
        {:error,
         [
           %{
             code: "unknown_era",
             severity: "error",
             edge_id: edge_id,
             message: "未定义时代「#{era_id}」"
           }
         ]}

      era ->
        issues =
          check_forbidden_concepts(era, edge) ++
            check_body_anachronisms(era, edge) ++
            check_taboos(era, graph, edge) ++
            check_unlisted_concepts(era, edge)

        case Enum.filter(issues, &(&1.severity == "error")) do
          [] ->
            case issues do
              [] -> :ok
              warnings -> {:ok_with_warnings, warnings}
            end

          _ ->
            {:error, issues}
        end
    end
  end

  @doc """
  Validate wiring Card A (`edge_a_id`) -> Card B (`edge_b_id`):
    1. Port unification (`exact` or via declared cross-era `translation`) via `Voy.World.Graph`
    2. Chronological consistency (`era_a.order <= era_b.order`, unless retrospective)
    3. Both cards pass their own era epistemic checks
  """
  @spec check_wire([map()], Graph.t(), String.t(), String.t()) ::
          {:ok, %{bridge: map(), warnings: [issue()]}} | {:error, [issue()]}
  def check_wire(eras, graph, edge_a_id, edge_b_id) do
    case Graph.check_bridge(graph, edge_a_id, edge_b_id) do
      {:error, err} ->
        {:error, [%{code: err.code, severity: "error", edge_id: edge_a_id, message: err.message}]}

      {:ok, bridge} ->
        edge_a = Graph.find_edge(graph, edge_a_id)
        edge_b = Graph.find_edge(graph, edge_b_id)

        chrono_issues = check_chronology(eras, graph, edge_a, edge_b, bridge)
        {err_a, warn_a} = split_edge_result(check_edge(eras, graph, edge_a))
        {err_b, warn_b} = split_edge_result(check_edge(eras, graph, edge_b))

        errors = chrono_issues ++ err_a ++ err_b
        warnings = warn_a ++ warn_b

        if errors == [] do
          {:ok, %{bridge: bridge, warnings: warnings}}
        else
          {:error, errors}
        end
    end
  end

  @doc """
  Check if a sequence of edge IDs forms a completely valid historical pathway
  under both graph connectivity and era epistemic constraints.
  """
  @spec valid_pathway?([map()], Graph.t(), [String.t()]) :: boolean()
  def valid_pathway?(_eras, _graph, []), do: false

  def valid_pathway?(eras, graph, [single]) do
    case Graph.find_edge(graph, single) do
      nil -> false
      edge -> match?({[], _}, split_edge_result(check_edge(eras, graph, edge)))
    end
  end

  def valid_pathway?(eras, graph, [a, b | rest]) do
    case check_wire(eras, graph, a, b) do
      {:ok, _} -> valid_pathway?(eras, graph, [b | rest])
      {:error, _} -> false
    end
  end

  @doc """
  Find all paths from `start_node` to `target_node` via `Graph.find_paths/4`
  and partition them into epistemically valid vs blocked paths.
  """
  @spec query_paths([map()], Graph.t(), String.t(), String.t(), keyword()) :: %{
          valid_paths: [[String.t()]],
          rejected_paths: [%{path: [String.t()], reasons: [issue()]}]
        }
  def query_paths(eras, graph, start_node, target_node, opts \\ []) do
    raw_paths = Graph.find_paths(graph, start_node, target_node, opts)

    Enum.reduce(raw_paths, %{valid_paths: [], rejected_paths: []}, fn path, acc ->
      case path_issues(eras, graph, path) do
        [] ->
          %{acc | valid_paths: acc.valid_paths ++ [path]}

        reasons ->
          %{acc | rejected_paths: acc.rejected_paths ++ [%{path: path, reasons: reasons}]}
      end
    end)
  end

  @doc "Audit every card and every potential wire in the world."
  @spec audit_world([map()], Graph.t()) :: map()
  def audit_world(eras, graph) do
    edge_reports =
      Map.new(graph.edges, fn edge ->
        {errors, warnings} = split_edge_result(check_edge(eras, graph, edge))

        status =
          cond do
            errors != [] -> "error"
            warnings != [] -> "warn"
            true -> "ok"
          end

        {edge[:id], %{status: status, errors: errors, warnings: warnings}}
      end)

    valid_wire_pairs =
      for a <- graph.edges,
          b <- graph.edges,
          a[:id] != b[:id],
          match?({:ok, _}, Graph.check_bridge(graph, a[:id], b[:id])),
          reduce: [] do
        acc ->
          case check_wire(eras, graph, a[:id], b[:id]) do
            {:ok, info} ->
              [
                %{
                  from_edge: a[:id],
                  to_edge: b[:id],
                  bridge: info.bridge,
                  ok: true,
                  issues: info.warnings
                }
                | acc
              ]

            {:error, issues} ->
              {:ok, raw_bridge} = Graph.check_bridge(graph, a[:id], b[:id])

              [
                %{
                  from_edge: a[:id],
                  to_edge: b[:id],
                  bridge: raw_bridge,
                  ok: false,
                  issues: issues
                }
                | acc
              ]
          end
      end
      |> Enum.reverse()

    error_edges = Enum.count(edge_reports, fn {_id, r} -> r.status == "error" end)
    warn_edges = Enum.count(edge_reports, fn {_id, r} -> r.status == "warn" end)

    %{
      summary: %{
        nodes: length(graph.nodes),
        edges: length(graph.edges),
        translations: length(graph.translations),
        eras: length(eras),
        ok_edges: length(graph.edges) - error_edges - warn_edges,
        warn_edges: warn_edges,
        error_edges: error_edges,
        valid_bridges: Enum.count(valid_wire_pairs, & &1.ok),
        blocked_bridges: Enum.count(valid_wire_pairs, &(not &1.ok))
      },
      edge_reports: edge_reports,
      bridges: valid_wire_pairs
    }
  end

  # ==== internals ==== //

  defp path_issues(eras, graph, [single]) do
    case Graph.find_edge(graph, single) do
      nil ->
        [%{code: "missing_edge", severity: "error", edge_id: single, message: "缺失边 #{single}"}]

      edge ->
        elem(split_edge_result(check_edge(eras, graph, edge)), 0)
    end
  end

  defp path_issues(eras, graph, [a, b | rest]) do
    here =
      case check_wire(eras, graph, a, b) do
        {:ok, _} -> []
        {:error, issues} -> issues
      end

    Enum.uniq_by(here ++ path_issues(eras, graph, [b | rest]), &{&1.code, &1.edge_id, &1.message})
  end

  defp check_forbidden_concepts(era, edge) do
    forbidden = get_list(era, "forbidden_concepts", :forbidden_concepts)

    for concept <- edge[:concepts] || [],
        run_all(fn q -> all([eq(q, concept), concept_not_forbiddeno(q, forbidden)]) end) == [] do
      %{
        code: "anachronistic_concept",
        severity: "error",
        edge_id: edge[:id],
        message: "时代越界：时代「#{era_name(era)}」禁止出现概念「#{concept}」"
      }
    end
  end

  defp check_body_anachronisms(era, edge) do
    forbidden = get_list(era, "forbidden_concepts", :forbidden_concepts)
    body = edge[:body] || ""
    listed = MapSet.new(edge[:concepts] || [])

    for term <- forbidden,
        term != "",
        not MapSet.member?(listed, term),
        String.contains?(body, term) do
      %{
        code: "anachronistic_text",
        severity: "error",
        edge_id: edge[:id],
        message: "正文穿帮：卡片正文直接提及该时代尚未出现的概念「#{term}」"
      }
    end
  end

  defp check_taboos(era, graph, edge) do
    taboos = get_list(era, "taboos", :taboos)
    observer = edge[:observer] || "unspecified"
    rel = edge[:rel]
    target_node = Graph.find_node(graph, edge[:to])
    target_kind = if target_node, do: target_node[:kind], else: "unknown"

    case run_all(fn q ->
           all([
             taboo_clearo(observer, rel, target_kind, taboos),
             eq(q, :ok)
           ])
         end) do
      [_ | _] ->
        []

      [] ->
        matched =
          Enum.find(taboos, fn t ->
            taboo_field(t, :observer) == observer and
              taboo_field(t, :rel) == rel and
              taboo_field(t, :target_kind) == target_kind
          end)

        reason = if matched, do: taboo_field(matched, :reason), else: "触发机构认知防火墙 (neq)"

        [
          %{
            code: "taboo_violation",
            severity: "error",
            edge_id: edge[:id],
            message: "认知防火墙冲突（#{observer}）：#{reason}"
          }
        ]
    end
  end

  defp check_unlisted_concepts(era, edge) do
    allowed = MapSet.new(get_list(era, "allowed_concepts", :allowed_concepts))
    forbidden = MapSet.new(get_list(era, "forbidden_concepts", :forbidden_concepts))

    if MapSet.size(allowed) == 0 do
      []
    else
      for concept <- edge[:concepts] || [],
          not MapSet.member?(allowed, concept),
          not MapSet.member?(forbidden, concept) do
        %{
          code: "unlisted_concept",
          severity: "warn",
          edge_id: edge[:id],
          message: "时代词表外概念：「#{concept}」尚未收入「#{era_name(era)}」的认知词表（可补充或视为超前词汇）"
        }
      end
    end
  end

  defp check_chronology(eras, graph, edge_a, edge_b, bridge) do
    era_a = find_era(eras, edge_a[:era])
    era_b = find_era(eras, edge_b[:era])

    tr_retro =
      case bridge[:translation_id] do
        nil ->
          false

        id ->
          tr = Graph.find_translation(graph, id)
          tr != nil and tr[:retrospective] == true
      end

    cond do
      era_a == nil or era_b == nil ->
        []

      edge_b[:retrospective] or tr_retro ->
        []

      era_order(era_a) > era_order(era_b) ->
        [
          %{
            code: "chronology_inversion",
            severity: "error",
            edge_id: edge_b[:id],
            message:
              "时间倒错：前卡属于「#{era_name(era_a)}」(序#{era_order(era_a)})，不能直接因果连向更早时代的「#{era_name(era_b)}」(序#{era_order(era_b)})（除非标记 retrospective）"
          }
        ]

      true ->
        []
    end
  end

  defp split_edge_result(:ok), do: {[], []}
  defp split_edge_result({:ok_with_warnings, warnings}), do: {[], warnings}

  defp split_edge_result({:error, issues}),
    do:
      {Enum.filter(issues, &(&1.severity == "error")),
       Enum.filter(issues, &(&1.severity == "warn"))}

  defp find_era(eras, id), do: Enum.find(eras, &(era_id(&1) == id))
  defp era_id(era), do: Map.get(era, :id) || Map.get(era, "id")
  defp era_name(era), do: Map.get(era, :name) || Map.get(era, "name") || era_id(era)
  defp era_order(era), do: Map.get(era, :order) || Map.get(era, "order") || 0

  defp get_list(map, str_key, atom_key) do
    Map.get(map, atom_key) || Map.get(map, str_key) || []
  end

  defp taboo_field(taboo, key) do
    Map.get(taboo, key) || Map.get(taboo, Atom.to_string(key))
  end
end
