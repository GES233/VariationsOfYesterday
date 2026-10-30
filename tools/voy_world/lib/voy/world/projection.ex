defmodule Voy.World.Projection do
  @moduledoc """
  Structure projection derived from a shared historical relation.

  Player-facing documents are no longer global summaries attached here. They
  are local five-hop cards rendered by `Voy.World.Witness`; this module only
  places structures in the world.
  """

  use Voy.Kanren

  alias Voy.World.Texts

  @structure_keys [
    {:Fate_Assimilated_NewZhai, :structure_assimilated},
    {:Fate_Annihilated_BurnoutZone, :structure_annihilated},
    {:Fate_Ascended_SpatialRift, :structure_ascended},
    {:Fate_Ancestral_ControlGroup, :structure_ancestral},
    {:Fate_Deferred_ChronicSeep, :structure_chronic_seep},
    {:Fate_Tolerated_FalseAlarm, :structure_false_alarm}
  ]

  @relic_fields [
    {:ancestral_bronze_zheng, :pass_zheng},
    {:carved_diversion_stele, :pass_stele}
  ]

  def compile_structureso(topo, era1, era2b, era3, structures) do
    fresh [up_name, pass_name, down_name, relic, fate, team, up_desc, pass_desc, down_desc] do
      propso(topo, up_name: up_name, pass_name: pass_name, down_name: down_name)
      propso(era1, guardian_relic: relic)
      propso(era2b, stationed_team: team)
      propso(era3, fate: fate)
      structure_descriptionso(fate, relic, up_desc, pass_desc, down_desc)

      eq(structures,
        upstream_site: {up_name, up_desc},
        pass_site: {pass_name, pass_desc},
        downstream_site: {down_name, down_desc}
      )
    end
  end

  defp structure_descriptionso(fate, relic, up_desc, pass_desc, down_desc) do
    branches =
      for {fate_tag, text_key} <- @structure_keys,
          {relic_tag, pass_field} <- @relic_fields do
        all([
          eq(fate, fate_tag),
          eq(relic, relic_tag),
          eq(up_desc, Texts.render!(text_key, :upstream)),
          eq(pass_desc, Texts.render!(text_key, pass_field)),
          eq(down_desc, Texts.render!(text_key, :downstream))
        ])
      end

    any(branches)
  end
end
