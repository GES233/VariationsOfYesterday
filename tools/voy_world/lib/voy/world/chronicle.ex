defmodule Voy.World.Chronicle do
  @moduledoc "The bidirectional relation between branch topography and its historical slice."

  use Voy.Kanren

  alias Voy.World.{Dynamics, Projection}

  def era1_headmano(topo, era1) do
    fresh [branch_id, up_type, pass_type, down_type, guardian_relic, damping_mode] do
      propso(topo,
        branch_id: branch_id,
        up_terrain: up_type,
        pass_terrain: pass_type,
        down_terrain: down_type
      )

      any([eq(down_type, :alluvial_flat), eq(down_type, :mid_terrace)])

      conde do
        [
          eq(pass_type, :canyon_pass),
          eq(guardian_relic, :ancestral_bronze_zheng),
          eq(damping_mode, :acoustic_bronze_damping)
        ]

        [
          eq(pass_type, :karst_gorge),
          eq(guardian_relic, :carved_diversion_stele),
          eq(damping_mode, :karst_siphon_relief)
        ]
      end

      eq(era1,
        branch_id: branch_id,
        polity: :headman_priest_compact,
        guardian_relic: guardian_relic,
        damping_mode: damping_mode,
        initial_c0: :c0_intact,
        initial_regime: :Calm
      )
    end
  end

  def era2a_magistrateo(topo, era1, era2a) do
    fresh [down_type, relic, attempt_action, backlash_omen, compromise_polity, gazetteer_case] do
      propso(topo, down_terrain: down_type)
      propso(era1, guardian_relic: relic)

      conde do
        [
          eq(down_type, :alluvial_flat),
          eq(relic, :ancestral_bronze_zheng),
          eq(attempt_action, :confiscate_zheng_for_tax_bronze),
          eq(backlash_omen, :magistrate_yamen_buried_by_rockslide),
          eq(compromise_polity, :nominal_tithing_inner_headman),
          eq(gazetteer_case, :case_1894_zheng_return_calmed_slide)
        ]

        [
          eq(down_type, :alluvial_flat),
          eq(relic, :carved_diversion_stele),
          eq(attempt_action, :dismantle_stele_for_paddy_tax),
          eq(backlash_omen, :granary_swallowed_by_karst_backflow),
          eq(compromise_polity, :nominal_tithing_inner_headman),
          eq(gazetteer_case, :case_1902_stele_restored_stopped_flood)
        ]

        [
          eq(down_type, :mid_terrace),
          eq(attempt_action, :clerk_halted_at_cliff_foot),
          eq(backlash_omen, :no_disturbance_triggered),
          eq(compromise_polity, :unreached_autonomous_zhai),
          eq(gazetteer_case, :case_mountain_too_steep_for_tithing)
        ]
      end

      eq(era2a,
        attempt: attempt_action,
        historical_backlash: backlash_omen,
        resulting_polity: compromise_polity,
        c0_after_era2a: :c0_intact,
        gazetteer_precedent: gazetteer_case
      )
    end
  end

  def era2b_reclamationo(topo, era1, era2a, era2b) do
    fresh [
      down_type,
      access,
      geology,
      up_type,
      relic,
      era2a_polity,
      org_after,
      headman_status,
      c0_after,
      relic_fate,
      eng_team,
      disturbance_mode,
      pulse
    ] do
      propso(topo,
        down_terrain: down_type,
        accessibility: access,
        up_geology: geology,
        up_terrain: up_type
      )

      propso(era1, guardian_relic: relic)
      propso(era2a, resulting_polity: era2a_polity)

      conde do
        [
          eq(down_type, :alluvial_flat),
          eq(access, :tractor_road_accessible),
          eq(era2a_polity, :nominal_tithing_inner_headman),
          eq(org_after, :state_reclamation_brigade),
          eq(headman_status, :demoted_to_warehouse_keeper),
          eq(relic_fate, {:requisitioned_as_scrap, relic}),
          eq(c0_after, :c0_collapsed)
        ]

        [
          eq(down_type, :mid_terrace),
          eq(access, :cliff_isolated),
          eq(era2a_polity, :unreached_autonomous_zhai),
          eq(org_after, :nominal_forestry_joint_zhai),
          eq(headman_status, :retained_ritual_authority),
          eq(relic_fate, {:preserved_in_situ, relic}),
          eq(c0_after, :c0_intact)
        ]
      end

      conde do
        [
          eq(up_type, :steep_fissure_headwater),
          eq(geology, :active_fault_vein),
          eq(eng_team, :survey_blasting_team_302),
          eq(disturbance_mode, :periodic_shallow_blasting)
        ]

        [
          eq(up_type, :karst_sinkhole_headwater),
          eq(geology, :deep_stress_cavity),
          eq(eng_team, :hq_high_pressure_grouting_corps),
          eq(disturbance_mode, :overwhelming_reverse_suppression)
        ]

        [
          eq(up_type, :steep_fissure_headwater),
          eq(geology, :minor_fracture),
          eq(eng_team, :forestry_hydro_post),
          eq(disturbance_mode, :low_level_logging_shock)
        ]
      end

      Dynamics.disturbance_pulseo(disturbance_mode, pulse)

      eq(era2b,
        org_after_reorg: org_after,
        headman_lineage_role: headman_status,
        relic_fate: relic_fate,
        c0_status: c0_after,
        stationed_team: eng_team,
        disturbance_mode: disturbance_mode,
        pulse: pulse
      )
    end
  end

  def history_through_era2bo(topo, history) do
    fresh [era1, era2a, era2b] do
      era1_headmano(topo, era1)
      era2a_magistrateo(topo, era1, era2a)
      era2b_reclamationo(topo, era1, era2a, era2b)
      eq(history, era1: era1, era2a: era2a, era2b: era2b)
    end
  end

  def era3_catastropheo(_topo, _era1, _era2a, era2b, era3) do
    fresh [
      org,
      headman_role,
      c0,
      pulse,
      post_withdrawal_polity,
      fate_category,
      resulting_regime,
      target_phase_rule,
      emergent_practice
    ] do
      propso(era2b,
        org_after_reorg: org,
        headman_lineage_role: headman_role,
        c0_status: c0,
        pulse: pulse
      )

      conde do
        [
          eq(org, :state_reclamation_brigade),
          eq(headman_role, :demoted_to_warehouse_keeper),
          eq(c0, :c0_collapsed),
          eq(pulse, :alpha_shock),
          eq(post_withdrawal_polity, :brigade_elder_joint_council),
          eq(fate_category, :Fate_Assimilated_NewZhai),
          eq(resulting_regime, :Oscillating),
          eq(target_phase_rule, :must_strike_at_AbsoluteRefractory),
          eq(emergent_practice,
            social_structure: "场部干事记工分、老保管员（旧头人后裔）掌听山令旗的『场部—议事会』双轨制",
            costume: "印有垦殖场字样的褪色劳保服，外披老寨火塘麻带",
            instrument: "隘口木架悬挂、缠满红绸的东方红-54铸铁气缸体",
            liturgy: "待林中虫鸣骤停（20%-30%死寂回吸窗），按《交接班安全生产条例》列队唱名三声、重锤击缸"
          )
        ]

        [
          eq(c0, :c0_collapsed),
          eq(pulse, :OverwhelmingNegativeSuppression),
          eq(post_withdrawal_polity, :depopulated_concrete_sealed_zone),
          eq(fate_category, :Fate_Annihilated_BurnoutZone),
          eq(resulting_regime, :Burnout),
          eq(target_phase_rule, :phase_frozen_in_white_silence),
          eq(emergent_practice,
            social_structure: "建制彻底抹除，仅剩场部封山水泥墙与虚假的『整建制搬迁』档案",
            phenomenon: "灰白钙化地表、绝对死寂无声、验山垂架被焊死在最大偏角",
            remnant: "被水泥彻底封死的超高压注浆井口与未寄出的家属抚恤底册"
          )
        ]

        [
          eq(c0, :c0_intact),
          eq(headman_role, :retained_ritual_authority),
          eq(pulse, :OverwhelmingNegativeSuppression),
          eq(post_withdrawal_polity, :rift_guarding_cliff_compact),
          eq(fate_category, :Fate_Ascended_SpatialRift),
          eq(resulting_regime, :Strained),
          eq(target_phase_rule, :triggered_at_Excitation_zenith),
          eq(emergent_practice,
            social_structure: "彻底断绝外部挂靠、恢复山官火塘严酷戒律以封锁源头裂隙的守山孤寨",
            phenomenon: "源头游荡着永恒搬运方块的黑影，指南针在掩体旁疯狂打转",
            remnant: "写满高频相位坐标与『去往对岸』字样的盘式磁带"
          )
        ]

        [
          eq(org, :nominal_forestry_joint_zhai),
          eq(headman_role, :retained_ritual_authority),
          eq(c0, :c0_intact),
          eq(pulse, :heavy_kick),
          eq(post_withdrawal_polity, :restored_ancestral_compact),
          eq(fate_category, :Fate_Ancestral_ControlGroup),
          eq(resulting_regime, :Calm),
          eq(target_phase_rule, :absorbed_in_Resting_basin),
          eq(emergent_practice,
            social_structure: "挂靠林场撤牌后全面回流的山官—祭司共同体（实行礼物经济，保管旧州府与林场文书）",
            phenomenon: "涧水偶有微澜但不过三息自平，维持源（c0）覆盖下万物生机盎然",
            remnant: "完好运转的镇山古器与世代相传的听山口诀"
          )
        ]
      end

      eq(era3,
        post_withdrawal_polity: post_withdrawal_polity,
        fate: fate_category,
        final_regime: resulting_regime,
        phase_rule: target_phase_rule,
        manifestation: emergent_practice
      )
    end
  end

  def history_through_era3o(topo, history) do
    fresh [era1, era2a, era2b, era3] do
      era1_headmano(topo, era1)
      era2a_magistrateo(topo, era1, era2a)
      era2b_reclamationo(topo, era1, era2a, era2b)
      era3_catastropheo(topo, era1, era2a, era2b, era3)
      eq(history, era1: era1, era2a: era2a, era2b: era2b, era3: era3)
    end
  end

  def compile_branch_sliceo(topo, slice) do
    fresh [branch_id, history, era1, era2a, era2b, era3, structures, archives] do
      propso(topo, branch_id: branch_id)
      history_through_era3o(topo, history)
      propso(history, era1: era1, era2a: era2a, era2b: era2b, era3: era3)
      Projection.compile_structureso(topo, era1, era2b, era3, structures)
      Projection.compile_archiveso(topo, era1, era2a, era2b, era3, archives)

      eq(slice,
        branch_id: branch_id,
        topography: topo,
        era1: era1,
        era2a: era2a,
        era2b: era2b,
        era3: era3,
        structures: structures,
        archives: archives
      )
    end
  end
end
