defmodule Voy.World do
  @moduledoc "Typed, JSON-safe entry points for the relational world model."

  use Voy.Kanren

  alias Voy.World.{Chronicle, Schema, Witness}

  def compile_branch(topo_fields) do
    topo = Schema.topology(topo_fields)
    slices = run_all(fn q -> Chronicle.compile_branch_sliceo(topo, q) end) |> terms()

    case slices do
      [] -> {:error, :unsupported_topography}
      slices -> {:ok, Enum.map(slices, &Schema.to_public/1)}
    end
  end

  def solve_interventions(topo_or_era2b, opts \\ []) do
    with {:ok, transitions} <- Chronicle.solve_interventions(topo_or_era2b, opts) do
      {:ok, Enum.map(transitions, &Schema.to_public/1)}
    end
  end

  def settle_open_dossier(dossier, intervention, opts \\ []) do
    with {:ok, settled} <- Witness.settle_open_dossier(dossier, intervention, opts) do
      {:ok, Schema.to_public(settled)}
    end
  end

  def reverse_worldgen(fate) do
    with {:ok, fate} <- Schema.normalize_fate(fate) do
      slices =
        run_all(fn q ->
          fresh [topo, slice, era3, up_type, pass_type, down_type, geology, access] do
            eq(topo,
              branch_id: :mystery_branch,
              up_name: "未知源头",
              pass_name: "未知隘口",
              down_name: "未知聚落",
              up_terrain: up_type,
              pass_terrain: pass_type,
              down_terrain: down_type,
              up_geology: geology,
              accessibility: access
            )

            Chronicle.compile_branch_sliceo(topo, slice)
            propso(slice, era3: era3)
            propso(era3, fate: fate)
            eq(q, slice)
          end
        end)
        |> terms()

      {:ok, Enum.map(slices, &Schema.to_public/1)}
    end
  end
end
