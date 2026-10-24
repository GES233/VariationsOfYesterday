defmodule Voy.World.Projection do
  @moduledoc """
  Structure and archive projections derived from a shared historical relation.

  `Projection` holds no prose of its own:

    * place-attached structure descriptions come from `Voy.World.Texts`;
    * archive documents and attribution taboos come from the authoring pack
      loaded by `Voy.World.Content`, translated into relational goals so both
      forward compilation and reverse clue-reconciliation queries work through
      the same relation.
  """

  use Voy.Kanren

  alias Voy.World.{Content, Texts}

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

  def compile_archiveso(topo, era1, era2a, era2b, era3, archives, pack \\ nil) do
    pack = pack || Content.default_pack()

    fresh [
      up_name,
      pass_name,
      down_name,
      relic,
      disturbance,
      gazetteer_case,
      gazetteer_record,
      fate,
      official_claim,
      official_text,
      inner_truth,
      inner_text,
      tech_corroborated,
      council_doc
    ] do
      propso(topo, up_name: up_name, pass_name: pass_name, down_name: down_name)
      propso(era1, guardian_relic: relic)
      propso(era2a, gazetteer_precedent: gazetteer_case)
      propso(era2b, disturbance_mode: disturbance)
      propso(era3, fate: fate)

      gazetteer_recordo(
        pack,
        %{up: up_name, pass: pass_name, down: down_name},
        gazetteer_case,
        down_name,
        gazetteer_record
      )

      lens_tabooo(pack, :official, official_claim)

      official_recordo(
        pack,
        %{up: up_name, pass: pass_name, down: down_name},
        fate,
        relic,
        gazetteer_case,
        official_claim,
        official_text
      )

      lens_tabooo(pack, :tech, inner_truth)
      neq(inner_truth, official_claim)

      tech_recordo(
        pack,
        %{up: up_name, pass: pass_name, down: down_name},
        fate,
        relic,
        gazetteer_case,
        inner_truth,
        inner_text,
        tech_corroborated
      )

      council_recordo(
        pack,
        %{up: up_name, pass: pass_name, down: down_name},
        fate,
        relic,
        down_name,
        council_doc
      )

      eq(archives,
        gazetteer: gazetteer_record,
        official_report: [
          target_settlement: down_name,
          upstream_site: up_name,
          claimed_cause: official_claim,
          summary: official_text
        ],
        tech_note: [
          true_source: up_name,
          settlement: down_name,
          corroborated_gazetteer: tech_corroborated,
          true_cause: inner_truth,
          note: inner_text
        ],
        council_document: council_doc
      )
    end
  end

  defp gazetteer_recordo(pack, names, gazetteer_case, down_name, record) do
    pack
    |> Content.documents_for(:gazetteer)
    |> Enum.map(fn doc ->
      fresh [excerpt] do
        matches_dimo(gazetteer_case, doc.gazetteer_cases)
        render_bodyo(doc.body, names, excerpt)

        eq(record,
          title: doc.title,
          recorded_settlement: down_name,
          precedent: gazetteer_case,
          excerpt: excerpt
        )
      end
    end)
    |> any()
  end

  defp official_recordo(pack, names, fate, relic, gazetteer_case, claim_tag, summary) do
    pack
    |> Content.documents_for(:official_report)
    |> Enum.map(fn doc ->
      all([
        matches_dimo(fate, doc.fates),
        matches_dimo(relic, doc.relics),
        matches_dimo(gazetteer_case, doc.gazetteer_cases),
        eq(claim_tag, Content.tag_atom(doc.claim)),
        render_bodyo(doc.body, names, summary)
      ])
    end)
    |> any()
  end

  defp tech_recordo(pack, names, fate, relic, gazetteer_case, claim_tag, note, corroborated) do
    pack
    |> Content.documents_for(:tech_note)
    |> Enum.map(fn doc ->
      all([
        matches_dimo(fate, doc.fates),
        matches_dimo(relic, doc.relics),
        matches_dimo(gazetteer_case, doc.gazetteer_cases),
        corroborated_goal(doc.gazetteer_cases, gazetteer_case, corroborated),
        eq(claim_tag, Content.tag_atom(doc.claim)),
        render_bodyo(doc.body, names, note)
      ])
    end)
    |> any()
  end

  # A notebook that declares which precedent it cites reports that precedent;
  # a wildcard notebook reports the one the history selected.
  defp corroborated_goal([], declared_case, corroborated), do: eq(corroborated, declared_case)
  defp corroborated_goal(cases, _declared_case, corroborated), do: membero(corroborated, cases)

  defp council_recordo(pack, names, fate, relic, down_name, council_doc) do
    pack
    |> Content.documents_for(:council_document)
    |> Enum.map(fn doc ->
      fresh [decree] do
        matches_dimo(fate, doc.fates)
        matches_dimo(relic, doc.relics)
        render_bodyo(doc.body, names, decree)

        eq(council_doc,
          title: doc.title,
          settlement: down_name,
          governance: Content.governance_atom(doc.governance),
          decree: decree
        )
      end
    end)
    |> any()
  end

  defp lens_tabooo(pack, lens, claim_tag) do
    pack
    |> Content.forbidden_claims_for(lens)
    |> Enum.map(&neq(claim_tag, &1))
    |> all()
  end

  defp matches_dimo(_var, []), do: all([])
  defp matches_dimo(var, [value]), do: eq(var, value)
  defp matches_dimo(var, values), do: membero(var, values)

  defp render_bodyo(body, names, rendered) do
    fn state ->
      resolved =
        Map.new(names, fn {role, term} ->
          {role, walk_star(term, state.subst)}
        end)

      eq(rendered, Content.render_body(body, resolved)).(state)
    end
  end
end
