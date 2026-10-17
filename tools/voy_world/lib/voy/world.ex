defmodule Voy.World do
  @moduledoc "Typed, JSON-safe entry points for the relational world model."

  use Voy.Kanren

  alias Voy.World.{Chronicle, Schema}

  def compile_branch(topo_fields) do
    topo = Schema.topology(topo_fields)
    slices = run_all(fn q -> Chronicle.compile_branch_sliceo(topo, q) end) |> terms()

    cond do
      slices != [] -> {:ok, Enum.map(slices, &Schema.to_public/1)}
      era2b_defined?(topo) -> {:error, :unresolved_history}
      true -> {:error, :unsupported_topography}
    end
  end

  def reverse_worldgen(fate) do
    with {:ok, fate} <- normalize_fate(fate) do
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

  defp era2b_defined?(topo) do
    run(1, fn q -> Chronicle.history_through_era2bo(topo, q) end) != []
  end

  defp normalize_fate(fate)
       when fate in [
              :Fate_Assimilated_NewZhai,
              :Fate_Annihilated_BurnoutZone,
              :Fate_Ascended_SpatialRift,
              :Fate_Ancestral_ControlGroup
            ],
       do: {:ok, fate}

  defp normalize_fate(fate) when is_binary(fate) do
    case Enum.find(Schema.fates(), &(Atom.to_string(&1) == fate)) do
      nil -> {:error, :unknown_fate}
      normalized -> {:ok, normalized}
    end
  end

  defp normalize_fate(_fate), do: {:error, :unknown_fate}
end
