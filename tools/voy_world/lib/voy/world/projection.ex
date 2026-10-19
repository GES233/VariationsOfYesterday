defmodule Voy.World.Projection do
  @moduledoc "Structure and archive projections derived from a shared historical relation."

  use Voy.Kanren

  alias Voy.World.Texts

  def compile_structureso(topo, era1, era2b, era3, structures) do
    fresh [up_name, pass_name, down_name, relic, fate, team, pass_desc] do
      propso(topo, up_name: up_name, pass_name: pass_name, down_name: down_name)
      propso(era1, guardian_relic: relic)
      propso(era2b, stationed_team: team)
      propso(era3, fate: fate)
      pass_relic_structureo(fate, relic, pass_desc)

      conde do
        [
          eq(fate, :Fate_Assimilated_NewZhai),
          eq(structures,
            upstream_site: {up_name, "302队废弃爆破钻探台（常驻脉冲源）"},
            pass_site: {pass_name, pass_desc},
            downstream_site:
              {down_name, "场部—议事会驻地：贴有火塘符纸的农场砖混宿舍（variations-of-yesterday:farm_dorm_unit_*）"}
          )
        ]

        [
          eq(fate, :Fate_Annihilated_BurnoutZone),
          eq(structures,
            upstream_site: {up_name, "水泥封死的超高压注浆井群（灰白死寂核心）"},
            pass_site: {pass_name, pass_desc},
            downstream_site: {down_name, "空无一人的钙化垦殖分场废墟（Burnout 绝对静默区）"}
          )
        ]

        [
          eq(fate, :Fate_Ascended_SpatialRift),
          eq(structures,
            upstream_site: {up_name, "深渊谐振掩体与黑曜石畸变裂隙（末影人游荡点）"},
            pass_site: {pass_name, pass_desc},
            downstream_site: {down_name, "严守封山戒律的半山绝壁孤寨（偶受空间裂隙微扰）"}
          )
        ]

        [
          eq(fate, :Fate_Ancestral_ControlGroup),
          eq(structures,
            upstream_site: {up_name, "撤牌废弃的林场水文观测哨"},
            pass_site: {pass_name, pass_desc},
            downstream_site: {down_name, "恢复山官岁祭的原生老寨（以物易物交换旧档案）"}
          )
        ]
      end
    end
  end

  defp pass_relic_structureo(fate, relic, pass_desc) do
    conde do
      [
        eq(fate, :Fate_Assimilated_NewZhai),
        eq(relic, :ancestral_bronze_zheng),
        eq(pass_desc, "峡谷隘口·原铜钲基座改挂红绸东方红气缸体的木架 + 验山垂架")
      ]

      [
        eq(fate, :Fate_Assimilated_NewZhai),
        eq(relic, :carved_diversion_stele),
        eq(pass_desc, "溶洞峡口·原分水碑残座改挂红绸东方红气缸体的铁架 + 验山垂架")
      ]

      [
        eq(fate, :Fate_Annihilated_BurnoutZone),
        eq(relic, :ancestral_bronze_zheng),
        eq(pass_desc, "被水泥封堵的古铜钲亭废墟与焊死在极值的验山垂架")
      ]

      [
        eq(fate, :Fate_Annihilated_BurnoutZone),
        eq(relic, :carved_diversion_stele),
        eq(pass_desc, "坍塌钙化的石雕分水堰与焊死在极值的验山垂架")
      ]

      [
        eq(fate, :Fate_Ascended_SpatialRift),
        eq(relic, :ancestral_bronze_zheng),
        eq(pass_desc, "尚存岁祭镇山铜钲与流官归化断碑的险峻峡口")
      ]

      [
        eq(fate, :Fate_Ascended_SpatialRift),
        eq(relic, :carved_diversion_stele),
        eq(pass_desc, "尚存石雕分水古碑与流官归化断碑的溶洞峡口")
      ]

      [
        eq(fate, :Fate_Ancestral_ControlGroup),
        eq(relic, :ancestral_bronze_zheng),
        eq(pass_desc, "完好如初的古寨镇山铜钲亭（c0 维持源完好）")
      ]

      [
        eq(fate, :Fate_Ancestral_ControlGroup),
        eq(relic, :carved_diversion_stele),
        eq(pass_desc, "完好如初的古寨石雕分水堰（c0 维持源完好）")
      ]
    end
  end

  def compile_archiveso(topo, era1, era2a, era2b, era3, archives) do
    fresh [
      up_name,
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
      council_doc
    ] do
      propso(topo, up_name: up_name, down_name: down_name)
      propso(era1, guardian_relic: relic)
      propso(era2a, gazetteer_precedent: gazetteer_case)
      propso(era2b, disturbance_mode: disturbance)
      propso(era3, fate: fate)
      era2a_gazetteer_recordo(gazetteer_case, down_name, gazetteer_record)
      official_tabooo(official_claim)

      branch_cause_candidateo(
        fate,
        relic,
        gazetteer_case,
        disturbance,
        official_claim,
        official_text
      )

      neq(inner_truth, official_claim)

      branch_cause_candidateo(
        fate,
        relic,
        gazetteer_case,
        disturbance,
        inner_truth,
        inner_text
      )

      era3_council_recordo(fate, down_name, council_doc)

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
          corroborated_gazetteer: gazetteer_case,
          true_cause: inner_truth,
          note: inner_text
        ],
        council_document: council_doc
      )
    end
  end

  defp era2a_gazetteer_recordo(gazetteer_case, down_name, record) do
    conde do
      [
        eq(gazetteer_case, :case_1894_zheng_return_calmed_slide),
        eq(record,
          title: Texts.render!(:archive_gazetteer_zheng, :title),
          recorded_settlement: down_name,
          precedent: :case_1894_zheng_return_calmed_slide,
          excerpt: Texts.render!(:archive_gazetteer_zheng, :excerpt)
        )
      ]

      [
        eq(gazetteer_case, :case_1902_stele_restored_stopped_flood),
        eq(record,
          title: Texts.render!(:archive_gazetteer_stele, :title),
          recorded_settlement: down_name,
          precedent: :case_1902_stele_restored_stopped_flood,
          excerpt: Texts.render!(:archive_gazetteer_stele, :excerpt)
        )
      ]

      [
        eq(gazetteer_case, :case_mountain_too_steep_for_tithing),
        eq(record,
          title: Texts.render!(:archive_gazetteer_cliff, :title),
          recorded_settlement: down_name,
          precedent: :case_mountain_too_steep_for_tithing,
          excerpt: Texts.render!(:archive_gazetteer_cliff, :excerpt)
        )
      ]
    end
  end

  defp branch_cause_candidateo(fate, relic, gazetteer_case, disturbance, cause_tag, text) do
    conde do
      [
        eq(fate, :Fate_Assimilated_NewZhai),
        eq(relic, :ancestral_bronze_zheng),
        eq(gazetteer_case, :case_1894_zheng_return_calmed_slide),
        eq(disturbance, :periodic_shallow_blasting),
        eq(cause_tag, :c0_removed_and_vulnerable_window_blasting),
        eq(text, Texts.render!(:archive_cause_c0_blasting_zheng, :text))
      ]

      [
        eq(fate, :Fate_Assimilated_NewZhai),
        eq(relic, :carved_diversion_stele),
        eq(gazetteer_case, :case_1902_stele_restored_stopped_flood),
        eq(disturbance, :periodic_shallow_blasting),
        eq(cause_tag, :c0_removed_and_vulnerable_window_blasting),
        eq(text, Texts.render!(:archive_cause_c0_blasting_stele, :text))
      ]

      [
        eq(fate, :Fate_Assimilated_NewZhai),
        eq(disturbance, :periodic_shallow_blasting),
        eq(cause_tag, :seasonal_rain_mudslide),
        eq(text, Texts.render!(:archive_cause_rain_excuse, :text))
      ]

      [
        eq(fate, :Fate_Annihilated_BurnoutZone),
        eq(relic, :ancestral_bronze_zheng),
        eq(gazetteer_case, :case_1894_zheng_return_calmed_slide),
        eq(disturbance, :overwhelming_reverse_suppression),
        eq(cause_tag, :overwhelming_suppression_attractor_burnout),
        eq(text, Texts.render!(:archive_cause_overwhelming_zheng, :text))
      ]

      [
        eq(fate, :Fate_Annihilated_BurnoutZone),
        eq(relic, :carved_diversion_stele),
        eq(gazetteer_case, :case_1902_stele_restored_stopped_flood),
        eq(disturbance, :overwhelming_reverse_suppression),
        eq(cause_tag, :overwhelming_suppression_attractor_burnout),
        eq(text, Texts.render!(:archive_cause_overwhelming_stele, :text))
      ]

      [
        eq(fate, :Fate_Annihilated_BurnoutZone),
        eq(disturbance, :overwhelming_reverse_suppression),
        eq(cause_tag, :planned_relocation_and_mine_closure),
        eq(text, Texts.render!(:archive_cause_relocation, :text))
      ]

      [
        eq(fate, :Fate_Ascended_SpatialRift),
        eq(disturbance, :overwhelming_reverse_suppression),
        eq(cause_tag, :excitation_zenith_spatial_exile),
        eq(text, Texts.render!(:archive_cause_rift_exile, :text))
      ]

      [
        eq(fate, :Fate_Ascended_SpatialRift),
        eq(disturbance, :overwhelming_reverse_suppression),
        eq(cause_tag, :equipment_retrieval_completed),
        eq(text, Texts.render!(:archive_cause_equipment_retrieval, :text))
      ]

      [
        eq(fate, :Fate_Ancestral_ControlGroup),
        eq(disturbance, :low_level_logging_shock),
        eq(cause_tag, :intact_c0_basin_absorption),
        eq(text, Texts.render!(:archive_cause_c0_intact, :text))
      ]

      [
        eq(fate, :Fate_Ancestral_ControlGroup),
        eq(disturbance, :low_level_logging_shock),
        eq(cause_tag, :backward_folk_custom_pending_reform),
        eq(text, Texts.render!(:archive_cause_backward_custom, :text))
      ]
    end
  end

  defp official_tabooo(cause_tag) do
    all([
      neq(cause_tag, :c0_removed_and_vulnerable_window_blasting),
      neq(cause_tag, :overwhelming_suppression_attractor_burnout),
      neq(cause_tag, :excitation_zenith_spatial_exile),
      neq(cause_tag, :intact_c0_basin_absorption)
    ])
  end

  defp era3_council_recordo(fate, down_name, council_doc) do
    conde do
      [
        eq(fate, :Fate_Assimilated_NewZhai),
        eq(council_doc,
          title: Texts.render!(:archive_council_joint_council, :title),
          settlement: down_name,
          governance: :brigade_elder_joint_council,
          decree: Texts.render!(:archive_council_joint_council, :decree)
        )
      ]

      [
        eq(fate, :Fate_Annihilated_BurnoutZone),
        eq(council_doc,
          title: Texts.render!(:archive_council_sealed_zone, :title),
          settlement: down_name,
          governance: :depopulated_concrete_sealed_zone,
          decree: Texts.render!(:archive_council_sealed_zone, :decree)
        )
      ]

      [
        eq(fate, :Fate_Ascended_SpatialRift),
        eq(council_doc,
          title: Texts.render!(:archive_council_rift_oath, :title),
          settlement: down_name,
          governance: :rift_guarding_cliff_compact,
          decree: Texts.render!(:archive_council_rift_oath, :decree)
        )
      ]

      [
        eq(fate, :Fate_Ancestral_ControlGroup),
        eq(council_doc,
          title: Texts.render!(:archive_council_ancestral_ledger, :title),
          settlement: down_name,
          governance: :restored_ancestral_compact,
          decree: Texts.render!(:archive_council_ancestral_ledger, :decree)
        )
      ]
    end
  end
end
