defmodule Voy.World.Chronicle do
  @moduledoc "The bidirectional relation between branch topography and its historical slice."

  use Voy.Kanren

  alias Voy.World.{Dynamics, Projection, Schema}

  def interventions, do: Schema.interventions()

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
    fresh [access, relic, attempt_action, backlash_omen, compromise_polity, gazetteer_case] do
      propso(topo, accessibility: access)
      propso(era1, guardian_relic: relic)

      conde do
        [
          eq(access, :tractor_road_accessible),
          eq(relic, :ancestral_bronze_zheng),
          eq(attempt_action, :confiscate_zheng_for_tax_bronze),
          eq(backlash_omen, :magistrate_yamen_buried_by_rockslide),
          eq(compromise_polity, :nominal_tithing_inner_headman),
          eq(gazetteer_case, :case_1894_zheng_return_calmed_slide)
        ]

        [
          eq(access, :tractor_road_accessible),
          eq(relic, :carved_diversion_stele),
          eq(attempt_action, :dismantle_stele_for_paddy_tax),
          eq(backlash_omen, :granary_swallowed_by_karst_backflow),
          eq(compromise_polity, :nominal_tithing_inner_headman),
          eq(gazetteer_case, :case_1902_stele_restored_stopped_flood)
        ]

        [
          eq(access, :cliff_isolated),
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
        accessibility: access,
        up_geology: geology,
        up_terrain: up_type
      )

      propso(era1, guardian_relic: relic)
      propso(era2a, resulting_polity: era2a_polity)

      conde do
        [
          eq(access, :tractor_road_accessible),
          eq(era2a_polity, :nominal_tithing_inner_headman),
          eq(org_after, :state_reclamation_brigade),
          eq(headman_status, :demoted_to_warehouse_keeper),
          eq(relic_fate, {:requisitioned_as_scrap, relic}),
          eq(c0_after, :c0_collapsed)
        ]

        [
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
          eq(target_phase_rule, :delayed_at_MeasuredPulseWindow),
          eq(emergent_practice,
            social_structure: "场部干事记工分、老保管员（旧头人后裔）掌听山令旗的『场部—议事会』双轨制",
            costume: "印有垦殖场字样的褪色劳保服，外披老寨火塘麻带",
            instrument: "隘口木架悬挂、缠满红绸的东方红-54铸铁气缸体",
            liturgy: "按观测手簿标定的实测推迟格，依《交接班安全生产条例》列队唱名并击缸"
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

        [
          eq(c0, :c0_collapsed),
          eq(pulse, :heavy_kick),
          eq(post_withdrawal_polity, :deferred_evacuation_watchpost),
          eq(fate_category, :Fate_Deferred_ChronicSeep),
          eq(resulting_regime, :Strained),
          eq(target_phase_rule, :drifting_at_RelativeRefractory),
          eq(emergent_practice,
            social_structure: "场部与山官后裔共同值守的『待撤观测站』：两套账本并行、撤离方案待批，谁也不敢先走",
            phenomenon: "山鸣年年不断却从未成灾；谷地常年泛潮锈味，水位线一年比一年低",
            remnant: "逐年修订却从未上报的《累积应力台账》与一摞『暂无搬迁计划』的复写纸"
          )
        ]

        [
          eq(c0, :c0_intact),
          eq(pulse, :alpha_shock),
          eq(post_withdrawal_polity, :cliff_observation_hermitage),
          eq(fate_category, :Fate_Tolerated_FalseAlarm),
          eq(resulting_regime, :Calm),
          eq(target_phase_rule, :absorbed_at_recovery_late),
          eq(emergent_practice,
            social_structure: "绝壁孤寨的『听山轮值』：两人一组持簧片监听匣，逐次记下上游爆破回数",
            phenomenon: "每逢上游点炮，涧水泛起鱼鳞驻波又自行平息；寨里已能凭水位判断炮位深浅",
            remnant: "记满『第某次虚警、已回落』的监听簿与一架从未用过的撤离背架"
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

  def history_through_era2bo(topo, partial_history) do
    fresh [era1, era2a, era2b] do
      era1_headmano(topo, era1)
      era2a_magistrateo(topo, era1, era2a)
      era2b_reclamationo(topo, era1, era2a, era2b)
      eq(partial_history, era1: era1, era2a: era2a, era2b: era2b)
    end
  end

  def interventiono(era2b, intervention, effective_c0, effective_pulse) do
    fresh [c0, pulse] do
      propso(era2b, c0_status: c0, pulse: pulse)

      conde do
        [
          eq(intervention, :no_intervention),
          eq(effective_c0, c0),
          eq(effective_pulse, pulse)
        ]

        [
          eq(intervention, :reanchor_vessel),
          eq(c0, :c0_collapsed),
          eq(effective_c0, :c0_intact),
          eq(effective_pulse, pulse)
        ]

        [
          eq(intervention, :dampen_headwater),
          any([eq(pulse, :alpha_shock), eq(pulse, :OverwhelmingNegativeSuppression)]),
          eq(effective_c0, c0),
          eq(effective_pulse, :heavy_kick)
        ]

        [
          eq(intervention, :reanchor_and_dampen),
          eq(c0, :c0_collapsed),
          any([eq(pulse, :alpha_shock), eq(pulse, :OverwhelmingNegativeSuppression)]),
          eq(effective_c0, :c0_intact),
          eq(effective_pulse, :heavy_kick)
        ]
      end
    end
  end

  def transition_era3o(_topo, _era1, _era2a, era2b, intervention, era3) do
    fresh [effective_c0, effective_pulse] do
      interventiono(era2b, intervention, effective_c0, effective_pulse)
      era3_from_stateo(effective_c0, effective_pulse, era3)
    end
  end

  defp era3_from_stateo(c0, pulse, era3) do
    fresh [
      post_withdrawal_polity,
      fate_category,
      resulting_regime,
      target_phase_rule,
      emergent_practice
    ] do
      conde do
        [
          eq(c0, :c0_collapsed),
          eq(pulse, :alpha_shock),
          eq(post_withdrawal_polity, :brigade_elder_joint_council),
          eq(fate_category, :Fate_Assimilated_NewZhai),
          eq(resulting_regime, :Oscillating),
          eq(target_phase_rule, :delayed_at_MeasuredPulseWindow),
          eq(emergent_practice,
            social_structure: "场部干事记工分、老保管员（旧头人后裔）掌听山令旗的『场部—议事会』双轨制",
            costume: "印有垦殖场字样的褪色劳保服，外披老寨火塘麻带",
            instrument: "隘口木架悬挂、缠满红绸的东方红-54铸铁气缸体",
            liturgy: "按观测手簿标定的实测推迟格，依《交接班安全生产条例》列队唱名并击缸"
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

        [
          eq(c0, :c0_collapsed),
          eq(pulse, :heavy_kick),
          eq(post_withdrawal_polity, :deferred_evacuation_watchpost),
          eq(fate_category, :Fate_Deferred_ChronicSeep),
          eq(resulting_regime, :Strained),
          eq(target_phase_rule, :drifting_at_RelativeRefractory),
          eq(emergent_practice,
            social_structure: "场部与山官后裔共同值守的『待撤观测站』：两套账本并行、撤离方案待批，谁也不敢先走",
            phenomenon: "山鸣年年不断却从未成灾；谷地常年泛潮锈味，水位线一年比一年低",
            remnant: "逐年修订却从未上报的《累积应力台账》与一摞『暂无搬迁计划』的复写纸"
          )
        ]

        [
          eq(c0, :c0_intact),
          eq(pulse, :alpha_shock),
          eq(post_withdrawal_polity, :cliff_observation_hermitage),
          eq(fate_category, :Fate_Tolerated_FalseAlarm),
          eq(resulting_regime, :Calm),
          eq(target_phase_rule, :absorbed_at_recovery_late),
          eq(emergent_practice,
            social_structure: "绝壁孤寨的『听山轮值』：两人一组持簧片监听匣，逐次记下上游爆破回数",
            phenomenon: "每逢上游点炮，涧水泛起鱼鳞驻波又自行平息；寨里已能凭水位判断炮位深浅",
            remnant: "记满『第某次虚警、已回落』的监听簿与一架从未用过的撤离背架"
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
    fresh [branch_id, history, era1, era2a, era2b, era3, structures] do
      propso(topo, branch_id: branch_id)
      history_through_era3o(topo, history)
      propso(history, era1: era1, era2a: era2a, era2b: era2b, era3: era3)
      Projection.compile_structureso(topo, era1, era2b, era3, structures)

      eq(slice,
        branch_id: branch_id,
        topography: topo,
        era1: era1,
        era2a: era2a,
        era2b: era2b,
        era3: era3,
        structures: structures
      )
    end
  end

  @doc "Enumerate every valid `(intervention, effective_c0, effective_pulse, era3)` reachable from a topography or an `era2b` slice."
  def reachable_fates(topo_or_era2b) do
    solve_interventions(topo_or_era2b, [])
  end

  @doc """
  Forward and backward relational solver for Era 3 runtime interventions.

  Options:
    * `:intervention` - constrain to a specific intervention (`:no_intervention`, `:reanchor_vessel`, `:dampen_headwater`, `:reanchor_and_dampen`)
    * `:target_fate` / `:fate` - constrain resulting `era3.fate`
    * `:target_regime` / `:regime` - constrain resulting `era3.final_regime` (`:Calm`, `:Strained`, `:Oscillating`, `:Burnout`)
    * `:target_c0` / `:effective_c0` - constrain `effective_c0`
    * `:target_pulse` / `:effective_pulse` - constrain `effective_pulse`
  """
  def solve_interventions(topo_or_era2b, opts \\ []) do
    with {:ok, normalized_opts} <- normalize_query_opts(opts),
         {:ok, source} <- classify_source(topo_or_era2b) do
      case source do
        {:era2b, era2b} ->
          {:ok, query_era2b_transitions(era2b, normalized_opts)}

        {:topo, topo} ->
          partials =
            run_all(fn q -> history_through_era2bo(topo, q) end)
            |> terms()

          case partials do
            [] ->
              {:error, :unsupported_topography}

            _ ->
              transitions =
                partials
                |> Enum.flat_map(fn partial ->
                  query_partial_transitions(topo, partial, normalized_opts)
                end)
                |> Enum.uniq_by(
                  &{&1.intervention, &1.target_fate, &1.baseline_c0, &1.baseline_pulse}
                )

              {:ok, transitions}
          end
      end
    end
  end

  defp query_partial_transitions(topo, partial, opts) do
    era1 = partial[:era1]
    era2a = partial[:era2a]
    era2b = partial[:era2b]

    run_all(fn q ->
      fresh [intervention, effective_c0, effective_pulse, era3] do
        constrain_interventiono(intervention, opts.intervention)
        interventiono(era2b, intervention, effective_c0, effective_pulse)
        constrain_valueo(effective_c0, opts.target_c0)
        constrain_valueo(effective_pulse, opts.target_pulse)
        transition_era3o(topo, era1, era2a, era2b, intervention, era3)
        constrain_era3o(era3, opts)

        eq(q,
          intervention: intervention,
          effective_c0: effective_c0,
          effective_pulse: effective_pulse,
          era3: era3
        )
      end
    end)
    |> terms()
    |> Enum.map(&format_transition(era2b, &1))
  end

  defp query_era2b_transitions(era2b, opts) do
    run_all(fn q ->
      fresh [intervention, effective_c0, effective_pulse, era3] do
        constrain_interventiono(intervention, opts.intervention)
        interventiono(era2b, intervention, effective_c0, effective_pulse)
        constrain_valueo(effective_c0, opts.target_c0)
        constrain_valueo(effective_pulse, opts.target_pulse)
        transition_era3o(nil, nil, nil, era2b, intervention, era3)
        constrain_era3o(era3, opts)

        eq(q,
          intervention: intervention,
          effective_c0: effective_c0,
          effective_pulse: effective_pulse,
          era3: era3
        )
      end
    end)
    |> terms()
    |> Enum.map(&format_transition(era2b, &1))
  end

  defp format_transition(era2b, row) do
    era3 = row[:era3]

    %{
      intervention: row[:intervention],
      baseline_c0: era2b[:c0_status],
      baseline_pulse: era2b[:pulse],
      effective_c0: row[:effective_c0],
      effective_pulse: row[:effective_pulse],
      fate: era3[:fate],
      target_fate: era3[:fate],
      final_regime: era3[:final_regime],
      target_regime: era3[:final_regime],
      post_withdrawal_polity: era3[:post_withdrawal_polity],
      phase_rule: era3[:phase_rule],
      era3: era3
    }
  end

  defp constrain_interventiono(_intervention, :any), do: all([])
  defp constrain_interventiono(intervention, target), do: eq(intervention, target)

  defp constrain_valueo(_var, :any), do: all([])
  defp constrain_valueo(var, target), do: eq(var, target)

  defp constrain_era3o(era3, opts) do
    all([
      case opts.target_fate do
        :any -> all([])
        fate -> propso(era3, fate: fate)
      end,
      case opts.target_regime do
        :any -> all([])
        regime -> propso(era3, final_regime: regime)
      end
    ])
  end

  defp normalize_query_opts(opts) when is_list(opts) or is_map(opts) do
    with {:ok, intervention} <-
           opt_intervention(fetch_opt(opts, [:intervention], :any)),
         {:ok, target_fate} <-
           opt_fate(fetch_opt(opts, [:target_fate, :fate], :any)),
         {:ok, target_regime} <-
           opt_regime(fetch_opt(opts, [:target_regime, :regime, :final_regime], :any)),
         {:ok, target_c0} <-
           opt_c0(fetch_opt(opts, [:target_c0, :effective_c0], :any)),
         {:ok, target_pulse} <-
           opt_pulse(fetch_opt(opts, [:target_pulse, :effective_pulse], :any)) do
      {:ok,
       %{
         intervention: intervention,
         target_fate: target_fate,
         target_regime: target_regime,
         target_c0: target_c0,
         target_pulse: target_pulse
       }}
    end
  end

  defp normalize_query_opts(_opts), do: {:error, :invalid_options}

  defp opt_intervention(v) when v in [:any, "any", nil], do: {:ok, :any}
  defp opt_intervention(v), do: Schema.normalize_intervention(v)

  defp opt_fate(v) when v in [:any, "any", nil], do: {:ok, :any}
  defp opt_fate(v), do: Schema.normalize_fate(v)

  defp opt_regime(v) when v in [:any, "any", nil], do: {:ok, :any}
  defp opt_regime(v), do: Schema.normalize_regime(v)

  defp opt_c0(v) when v in [:any, "any", nil], do: {:ok, :any}
  defp opt_c0(v) when v in [:c0_collapsed, :c0_intact], do: {:ok, v}
  defp opt_c0("c0_collapsed"), do: {:ok, :c0_collapsed}
  defp opt_c0("c0_intact"), do: {:ok, :c0_intact}
  defp opt_c0(_v), do: {:error, :invalid_c0_status}

  defp opt_pulse(v) when v in [:any, "any", nil], do: {:ok, :any}

  defp opt_pulse(v)
       when v in [:alpha_shock, :OverwhelmingNegativeSuppression, :heavy_kick],
       do: {:ok, v}

  defp opt_pulse("alpha_shock"), do: {:ok, :alpha_shock}
  defp opt_pulse("OverwhelmingNegativeSuppression"), do: {:ok, :OverwhelmingNegativeSuppression}
  defp opt_pulse("heavy_kick"), do: {:ok, :heavy_kick}
  defp opt_pulse(_v), do: {:error, :invalid_pulse}

  defp classify_source(fate) when is_atom(fate) or is_binary(fate) do
    with {:ok, normalized} <- Schema.normalize_fate(fate) do
      spec = Voy.World.Pathway.fate_spec(normalized)
      {:ok, {:era2b, [c0_status: spec.c0_status, pulse: spec.pulse]}}
    end
  end

  defp classify_source(fields) when is_list(fields) or is_map(fields) do
    cond do
      has_field?(fields, :era2b) ->
        classify_source(get_field(fields, :era2b))

      has_field?(fields, :c0_status) and has_field?(fields, :pulse) ->
        with {:ok, c0} <- opt_c0(get_field(fields, :c0_status)),
             {:ok, pulse} <- opt_pulse(get_field(fields, :pulse)),
             true <- c0 != :any and pulse != :any do
          {:ok, {:era2b, [c0_status: c0, pulse: pulse]}}
        else
          _ -> {:error, :invalid_era2b}
        end

      true ->
        build_query_topo(fields)
    end
  end

  defp classify_source(_other), do: {:error, :invalid_source}

  defp build_query_topo(fields) do
    try do
      features = Schema.complete_features(fields)

      topo =
        [
          branch_id: get_field(fields, :branch_id) || :query_branch,
          up_name: get_field(fields, :up_name) || "上游",
          pass_name: get_field(fields, :pass_name) || "隘口",
          down_name: get_field(fields, :down_name) || "谷地"
        ] ++ features

      {:ok, {:topo, topo}}
    rescue
      _ in [KeyError, ArgumentError] -> {:error, :unsupported_topography}
    end
  end

  defp fetch_opt(opts, keys, default) do
    Enum.find_value(keys, default, fn key ->
      if has_field?(opts, key), do: get_field(opts, key)
    end)
  end

  defp has_field?(fields, key) when is_list(fields), do: Keyword.has_key?(fields, key)

  defp has_field?(fields, key) when is_map(fields) do
    Map.has_key?(fields, key) or Map.has_key?(fields, Atom.to_string(key))
  end

  defp get_field(fields, key) when is_list(fields), do: Keyword.get(fields, key)

  defp get_field(fields, key) when is_map(fields) do
    case Map.fetch(fields, key) do
      {:ok, v} -> v
      :error -> Map.get(fields, Atom.to_string(key))
    end
  end
end
