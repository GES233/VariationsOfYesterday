defmodule Voy.World.Naming do
  @moduledoc "Late binding of names, structures, and the seeded five-hop dossier."

  use Voy.Kanren

  alias Voy.World.{Projection, Schema, Witness}

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

    structures =
      run_all(fn q ->
        Projection.compile_structureso(named_topo, era1, era2b, era3, q)
      end)
      |> terms()

    case structures do
      [structure_set] ->
        horizon = Map.get(spec, :horizon, :locked)

        {:ok,
         [
           node_id: spec.node_id,
           branch_id: spec.branch_id,
           rule_set: spec.rule_set,
           horizon: horizon,
           names: spec.names,
           topography: named_topo,
           era1: era1,
           era2a: era2a,
           era2b: era2b,
           era3: era3,
           structures: structure_set,
           dossier:
             Witness.render_dossier(
               spec.seed,
               spec.branch_id,
               Keyword.fetch!(era1, :guardian_relic),
               Keyword.fetch!(era3, :fate),
               spec.names,
               horizon: horizon
             )
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
