defmodule Voy.World.Projection do
  @moduledoc "Structure and archive projections derived from a shared historical relation."

  use Voy.Kanren

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
          title: "《旧州府抚夷清丈残卷·铜钲案》",
          recorded_settlement: down_name,
          precedent: :case_1894_zheng_return_calmed_slide,
          excerpt: "光绪二十年清丈局强收寨门铜钲入库铸钱，是秋山鸣石崩掩没局署；流官惊惧，发还铜钲任山官依古法岁祭叩击，山患遂平（外甲内寨）"
        )
      ]

      [
        eq(gazetteer_case, :case_1902_stele_restored_stopped_flood),
        eq(record,
          title: "《旧州府抚夷清丈残卷·石堰案》",
          recorded_settlement: down_name,
          precedent: :case_1902_stele_restored_stopped_flood,
          excerpt: "光绪二十八年委员毁峡口分水石碑改辟官田，越旬落水洞暗河倒灌吞没官仓；遂准山官复立石堰，按候泄洪乃安（外甲内寨）"
        )
      ]

      [
        eq(gazetteer_case, :case_mountain_too_steep_for_tithing),
        eq(record,
          title: "《旧州府抚夷清丈残卷·立碑案》",
          recorded_settlement: down_name,
          precedent: :case_mountain_too_steep_for_tithing,
          excerpt: "其寨悬居绝壁，栈道断绝，丈量胥吏望谷生畏，仅于山口立『归化碑』敷衍上峰，实未入寨门半步，听任山官自治"
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
        eq(text, "对照清丈残卷所载拆钟旧案，79年收缴铜钲再度断绝阻尼（c0=0）；302队点炮又撞上簧片蜂鸣段，致全谷陷入自持振荡")
      ]

      [
        eq(fate, :Fate_Assimilated_NewZhai),
        eq(relic, :carved_diversion_stele),
        eq(gazetteer_case, :case_1902_stele_restored_stopped_flood),
        eq(disturbance, :periodic_shallow_blasting),
        eq(cause_tag, :c0_removed_and_vulnerable_window_blasting),
        eq(text, "对照清丈残卷所载毁堰旧案，79年拆毁分水石碑再度断绝阻尼（c0=0）；302队点炮又撞上簧片蜂鸣段，致全谷陷入自持振荡")
      ]

      [
        eq(fate, :Fate_Assimilated_NewZhai),
        eq(disturbance, :periodic_shallow_blasting),
        eq(cause_tag, :seasonal_rain_mudslide),
        eq(text, "归咎于连日秋雨导致常规岩移滑坡，绝口不提收缴镇山古器与302队违规点炮")
      ]

      [
        eq(fate, :Fate_Annihilated_BurnoutZone),
        eq(relic, :ancestral_bronze_zheng),
        eq(gazetteer_case, :case_1894_zheng_return_calmed_slide),
        eq(disturbance, :overwhelming_reverse_suppression),
        eq(cause_tag, :overwhelming_suppression_attractor_burnout),
        eq(text, "清丈残卷早戒『不可强拆铜钲、绝山阻尼』，总指挥部偏以超高压水泥封井强压山息，应力冲穿截断阈值，整条沟进入 Burnout 吸引子")
      ]

      [
        eq(fate, :Fate_Annihilated_BurnoutZone),
        eq(relic, :carved_diversion_stele),
        eq(gazetteer_case, :case_1902_stele_restored_stopped_flood),
        eq(disturbance, :overwhelming_reverse_suppression),
        eq(cause_tag, :overwhelming_suppression_attractor_burnout),
        eq(text, "清丈残卷早戒『不可强堵暗河』，总指挥部偏用超高压反向水泥封井，应力冲穿截断阈值，整条沟进入 Burnout 吸引子")
      ]

      [
        eq(fate, :Fate_Annihilated_BurnoutZone),
        eq(disturbance, :overwhelming_reverse_suppression),
        eq(cause_tag, :planned_relocation_and_mine_closure),
        eq(text, "宣称矿区资源正常枯竭、垦殖分场全员已按计划整建制平稳搬迁")
      ]

      [
        eq(fate, :Fate_Ascended_SpatialRift),
        eq(disturbance, :overwhelming_reverse_suppression),
        eq(cause_tag, :excitation_zenith_spatial_exile),
        eq(text, "依靠半山老寨完好的 c0 缓冲，源头未直接烧毁，但核心组在起峰极值（Excitation）强开谐振腔，全员化为搬石头的末影黑影")
      ]

      [
        eq(fate, :Fate_Ascended_SpatialRift),
        eq(disturbance, :overwhelming_reverse_suppression),
        eq(cause_tag, :equipment_retrieval_completed),
        eq(text, "深孔观测站科研任务圆满结束，设备回收完毕，人员调离备案")
      ]

      [
        eq(fate, :Fate_Ancestral_ControlGroup),
        eq(disturbance, :low_level_logging_shock),
        eq(cause_tag, :intact_c0_basin_absorption),
        eq(text, "镇山古器从未离位（c0 完好），林场小震皆被宽容区吸收；莫在鱼鳞水纹时动土，要在万籁俱寂时叩钟")
      ]

      [
        eq(fate, :Fate_Ancestral_ControlGroup),
        eq(disturbance, :low_level_logging_shock),
        eq(cause_tag, :backward_folk_custom_pending_reform),
        eq(text, "山高路远暂设挂靠林场，旧式山官鸣钟习俗待后续条件成熟再行改组")
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
          title: "《场部—议事会联席保命规约》",
          settlement: down_name,
          governance: :brigade_elder_joint_council,
          decree: "老保管员（原山官后裔）看水纹听虫鸣掌令旗，场部司务长按《交接班制度》点名记工分；凡死寂回吸窗至，立敲红绸气缸三声"
        )
      ]

      [
        eq(fate, :Fate_Annihilated_BurnoutZone),
        eq(council_doc,
          title: "《封山隔离水泥墙警示铁牌》",
          settlement: down_name,
          governance: :depopulated_concrete_sealed_zone,
          decree: "灰白死寂区内无声无息，场部与议事会筹备组全体失踪，严禁任何人员越过水泥封锁线"
        )
      ]

      [
        eq(fate, :Fate_Ascended_SpatialRift),
        eq(council_doc,
          title: "《山官火塘封山铁誓》",
          settlement: down_name,
          governance: :rift_guarding_cliff_compact,
          decree: "源头掩体已化为黑影裂隙，见末影搬石切勿直视，镇山古器日夜须有人值守"
        )
      ]

      [
        eq(fate, :Fate_Ancestral_ControlGroup),
        eq(council_doc,
          title: "《岁祭账房木刻借阅约》",
          settlement: down_name,
          governance: :restored_ancestral_compact,
          decree: "林场撤牌后岁祭如仪，外客若以干粮铁器相赠，可借阅旧州府清丈卷与水文台账"
        )
      ]
    end
  end
end
