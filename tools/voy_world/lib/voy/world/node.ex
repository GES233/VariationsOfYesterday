defmodule Voy.World.Node do
  @moduledoc "Node specifications and bidirectional, name-independent semantic compilation."

  use Voy.Kanren

  alias Voy.World.{Chronicle, Naming, Schema}

  @name_keys [:node, :up, :pass, :down]

  def solve(spec_fields, feature_fields) do
    with {:ok, spec} <- normalize_spec(spec_fields) do
      solve_normalized(spec, Schema.features(feature_fields))
    end
  end

  def compile(spec_fields, feature_fields) do
    with {:ok, spec} <- normalize_spec(spec_fields),
         :ok <- validate_names(spec),
         {:ok, candidates} <- solve_normalized(spec, Schema.complete_features(feature_fields)),
         {:ok, records} <- Naming.materialize_all(spec, candidates) do
      {:ok, records}
    end
  end

  def materialize(spec_fields, candidate) do
    with {:ok, topo} <- candidate_topography(candidate),
         {:ok, records} <- compile(spec_fields, topo) do
      case records do
        [record] -> {:ok, record}
        _ -> {:error, :ambiguous_node_candidate}
      end
    end
  end

  def normalize_spec(fields) do
    with {:ok, node_id} <- required_id(fields, :node_id),
         {:ok, branch_id} <- required_id(fields, :branch_id),
         {:ok, rule_set} <- normalize_rule_set(value(fields, :rule_set, :default_branch_v1)),
         {:ok, fate_constraint} <-
           normalize_fate_constraint(value(fields, :fate_constraint, :any)),
         {:ok, names} <- normalize_names(fields) do
      {:ok,
       %{
         node_id: node_id,
         branch_id: branch_id,
         rule_set: rule_set,
         fate_constraint: fate_constraint,
         names: names
       }}
    end
  end

  defp solve_normalized(spec, features) do
    candidates =
      run_all(fn q ->
        fresh [topo, history] do
          semantic_topoo(spec, features, topo)
          historyo(spec.rule_set, topo, history)
          fate_constraintgo(spec.fate_constraint, history)

          eq(q,
            node_id: spec.node_id,
            branch_id: spec.branch_id,
            rule_set: spec.rule_set,
            topography: topo,
            history: history
          )
        end
      end)
      |> terms()

    if candidates == [] do
      classify_failure(spec, features)
    else
      {:ok, candidates}
    end
  end

  defp semantic_topoo(spec, features, topo) do
    fresh [up_type, pass_type, down_type, geology, access] do
      eq(topo,
        branch_id: spec.branch_id,
        node_id: spec.node_id,
        up_site: "#{spec.node_id}/up",
        pass_site: "#{spec.node_id}/pass",
        down_site: "#{spec.node_id}/down",
        up_terrain: up_type,
        pass_terrain: pass_type,
        down_terrain: down_type,
        up_geology: geology,
        accessibility: access
      )

      propso(topo, features)
    end
  end

  defp historyo(:default_branch_v1, topo, history) do
    Chronicle.history_through_era3o(topo, history)
  end

  defp fate_constraintgo(:any, _history), do: all([])

  defp fate_constraintgo({:require, fate}, history) do
    fresh [era3] do
      propso(history, era3: era3)
      propso(era3, fate: fate)
    end
  end

  defp fate_constraintgo({:one_of, fates}, history) do
    fresh [era3, fate] do
      propso(history, era3: era3)
      membero(fate, fates)
      propso(era3, fate: fate)
    end
  end

  defp classify_failure(spec, features) do
    history_exists? =
      run(1, fn q ->
        fresh [topo, history] do
          semantic_topoo(spec, features, topo)
          historyo(spec.rule_set, topo, history)
          eq(q, history)
        end
      end) != []

    era2b_exists? =
      run(1, fn q ->
        fresh [topo, history] do
          semantic_topoo(spec, features, topo)
          Chronicle.history_through_era2bo(topo, history)
          eq(q, history)
        end
      end) != []

    cond do
      history_exists? -> {:error, :fate_constraint_unsatisfied}
      era2b_exists? -> {:error, :unresolved_history}
      true -> {:error, :unsupported_topography}
    end
  end

  defp candidate_topography(candidate) do
    case fetch_field(candidate, :topography) do
      {:ok, topo} when is_list(topo) or is_map(topo) -> {:ok, topo}
      _ -> {:error, :invalid_node_candidate}
    end
  end

  defp required_id(fields, key) do
    case fetch_field(fields, key) do
      {:ok, value} when is_binary(value) and value != "" -> {:ok, value}
      {:ok, value} when is_atom(value) -> {:ok, Atom.to_string(value)}
      _ -> {:error, {:missing_or_invalid_id, key}}
    end
  end

  defp normalize_rule_set(value) when value in [:default_branch_v1, "default_branch_v1"] do
    {:ok, :default_branch_v1}
  end

  defp normalize_rule_set(_value), do: {:error, :unknown_rule_set}

  defp normalize_fate_constraint(value) when value in [:any, "any"], do: {:ok, :any}

  defp normalize_fate_constraint({:require, fate}) do
    with {:ok, normalized} <- Schema.normalize_fate(fate) do
      {:ok, {:require, normalized}}
    end
  end

  defp normalize_fate_constraint({:one_of, fates}) when is_list(fates) and fates != [] do
    Enum.reduce_while(fates, {:ok, []}, fn fate, {:ok, acc} ->
      case Schema.normalize_fate(fate) do
        {:ok, normalized} -> {:cont, {:ok, [normalized | acc]}}
        {:error, _reason} = error -> {:halt, error}
      end
    end)
    |> case do
      {:ok, normalized} -> {:ok, {:one_of, Enum.reverse(normalized)}}
      error -> error
    end
  end

  defp normalize_fate_constraint(_value), do: {:error, :invalid_fate_constraint}

  defp normalize_names(fields) do
    case fetch_field(fields, :names) do
      :error -> {:ok, nil}
      {:ok, nil} -> {:ok, nil}
      {:ok, names} when is_list(names) or is_map(names) -> normalize_name_book(names)
      _ -> {:error, :invalid_name_book}
    end
  end

  defp normalize_name_book(names) do
    Enum.reduce_while(@name_keys, {:ok, %{}}, fn key, {:ok, acc} ->
      case fetch_field(names, key) do
        :error ->
          {:cont, {:ok, acc}}

        {:ok, value} when is_binary(value) and value != "" ->
          {:cont, {:ok, Map.put(acc, key, value)}}

        _ ->
          {:halt, {:error, :invalid_name_book}}
      end
    end)
  end

  defp validate_names(%{names: names}) when is_map(names) do
    if Enum.all?(@name_keys, &Map.has_key?(names, &1)) do
      :ok
    else
      {:error, :incomplete_name_book}
    end
  end

  defp validate_names(_spec), do: {:error, :incomplete_name_book}

  defp value(fields, key, default) do
    case fetch_field(fields, key) do
      {:ok, value} -> value
      :error -> default
    end
  end

  defp fetch_field(fields, key) when is_list(fields), do: Keyword.fetch(fields, key)

  defp fetch_field(fields, key) when is_map(fields) do
    case Map.fetch(fields, key) do
      {:ok, value} -> {:ok, value}
      :error -> Map.fetch(fields, Atom.to_string(key))
    end
  end

  defp fetch_field(_fields, _key), do: :error
end
