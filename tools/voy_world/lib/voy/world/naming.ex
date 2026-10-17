defmodule Voy.World.Naming do
  @moduledoc "Late binding of display names and text/structure projections to semantic candidates."

  use Voy.Kanren

  alias Voy.World.{Projection, Schema}

  def materialize_all(spec, candidates) do
    candidates
    |> Enum.reduce_while({:ok, []}, fn candidate, {:ok, acc} ->
      case materialize(spec, candidate) do
        {:ok, record} -> {:cont, {:ok, [record | acc]}}
        {:error, _reason} = error -> {:halt, error}
      end
    end)
    |> case do
      {:ok, records} -> {:ok, Enum.reverse(records)}
      error -> error
    end
  end

  def materialize(spec, candidate) do
    topo = Keyword.fetch!(candidate, :topography)
    history = Keyword.fetch!(candidate, :history)
    era1 = Keyword.fetch!(history, :era1)
    era2a = Keyword.fetch!(history, :era2a)
    era2b = Keyword.fetch!(history, :era2b)
    era3 = Keyword.fetch!(history, :era3)
    named_topo = named_topography(spec, topo)

    projections =
      run_all(fn q ->
        fresh [structures, archives] do
          Projection.compile_structureso(named_topo, era1, era2b, era3, structures)
          Projection.compile_archiveso(named_topo, era1, era2a, era2b, era3, archives)
          eq(q, structures: structures, archives: archives)
        end
      end)
      |> terms()

    case projections do
      [projection] ->
        {:ok,
         [
           node_id: spec.node_id,
           branch_id: spec.branch_id,
           rule_set: spec.rule_set,
           names: spec.names,
           topography: named_topo,
           era1: era1,
           era2a: era2a,
           era2b: era2b,
           era3: era3,
           structures: Keyword.fetch!(projection, :structures),
           archives: Keyword.fetch!(projection, :archives)
         ]}

      _ ->
        {:error, :projection_failed}
    end
  end

  defp named_topography(spec, topo) do
    [
      branch_id: spec.branch_id,
      node_id: spec.node_id,
      up_site: Keyword.fetch!(topo, :up_site),
      pass_site: Keyword.fetch!(topo, :pass_site),
      down_site: Keyword.fetch!(topo, :down_site),
      up_name: spec.names.up,
      pass_name: spec.names.pass,
      down_name: spec.names.down
    ] ++ Schema.complete_features(topo)
  end
end
