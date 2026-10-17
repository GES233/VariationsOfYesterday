defmodule Voy.World.Causality do
  @moduledoc "Relational causal stages, observations, ritual synthesis, and narrative lenses."

  use Voy.Kanren

  alias Voy.World.Dynamics

  @drainage_edges [
    [up: :boar_gully, mid: :broken_stele_pass, down: :baishui_flat],
    [up: :laojun_flat, mid: :echo_brook, down: :nuoliang_zhai]
  ]

  def drainage_edgeo(up, mid, down) do
    tableo(@drainage_edges, up: up, mid: mid, down: down)
  end

  @settlement_states [
    [place: :baishui_flat, org_type: :farm_brigade_04, c0_status: :c0_collapsed],
    [place: :nuoliang_zhai, org_type: :ancestral_zhai, c0_status: :c0_intact]
  ]

  def settlement_stateo(place, org_type, c0_status) do
    tableo(@settlement_states, place: place, org_type: org_type, c0_status: c0_status)
  end

  def artifact_actiono(org_type, c0_status, actor_group, place_role, item_props) do
    conde do
      [
        eq(org_type, :ancestral_zhai),
        eq(c0_status, :c0_intact),
        eq(actor_group, :forestry_quarry_crew),
        eq(place_role, :upstream_source),
        eq(item_props,
          name: "常规林区修路黑火药",
          pulse: :heavy_kick,
          trace_left: :charcoal_powder_residue
        )
      ]

      [
        eq(org_type, :farm_brigade_04),
        eq(c0_status, :c0_collapsed),
        eq(actor_group, :survey_team_302),
        eq(place_role, :upstream_source),
        eq(item_props,
          name: "2号岩石硝铵炸药点炮",
          pulse: :alpha_shock,
          trace_left: :blasting_cap_wire
        )
      ]

      [
        eq(org_type, :ancestral_zhai),
        eq(c0_status, :c0_intact),
        eq(actor_group, :old_zhai_priest),
        eq(place_role, :midstream_pass),
        eq(item_props,
          name: "岁祭镇山铜钲三叩",
          pulse: :alpha_metal_resonance,
          trace_left: :verdigris_mallet_mark
        )
      ]

      [
        eq(org_type, :farm_brigade_04),
        eq(c0_status, :c0_collapsed),
        eq(actor_group, :assimilated_brigade_04),
        eq(place_role, :midstream_pass),
        eq(item_props,
          name: "红绸包裹的东方红-54气缸体敲击礼",
          pulse: :alpha_metal_resonance,
          trace_left: :red_cloth_and_grease
        )
      ]

      [
        eq(org_type, :farm_brigade_04),
        eq(c0_status, :c0_collapsed),
        eq(actor_group, :hq_grouting_command),
        eq(place_role, :upstream_source),
        eq(item_props,
          name: "超高压反向水泥封井注浆",
          pulse: :OverwhelmingNegativeSuppression,
          trace_left: :calcified_grout_seal
        )
      ]
    end
  end

  def action_siteo(up, mid, role, action_site) do
    conde do
      [eq(role, :upstream_source), eq(action_site, up)]
      [eq(role, :midstream_pass), eq(action_site, mid)]
    end
  end

  def causal_stageso(initial, propagation, outcome) do
    fresh [
      up,
      mid,
      down,
      org,
      c0,
      regime,
      phase,
      actor,
      role,
      action_site,
      item,
      pulse,
      next_state,
      lag,
      nat_sign,
      inst_sign
    ] do
      drainage_edgeo(up, mid, down)
      settlement_stateo(down, org, c0)
      Dynamics.phase_phenomenono(phase, nat_sign, inst_sign)
      artifact_actiono(org, c0, actor, role, item)
      action_siteo(up, mid, role, action_site)
      propso(item, pulse: pulse)
      Dynamics.phase_transitiono(c0, regime, phase, pulse, next_state, lag)

      eq(initial,
        upstream: up,
        pass: mid,
        action_site: action_site,
        action_role: role,
        phase: phase,
        natural_sign: nat_sign,
        instrument_sign: inst_sign,
        actor: actor,
        action_item: item
      )

      eq(propagation,
        from: up,
        via: mid,
        to: down,
        c0_baseline: c0,
        synapse_lag: lag,
        regime: regime
      )

      eq(outcome, settlement: down, org: org, outcome: next_state)
    end
  end

  def attribution_candidateo(regime, cause_tag, explanation_text) do
    conde do
      [
        eq(regime, :Strained),
        eq(cause_tag, :autumn_rain_natural_erosion),
        eq(explanation_text, "十月连日秋雨导致上游常规岩层疲劳滑坡（系自然损耗，与排险爆破无关）")
      ]

      [
        eq(regime, :Strained),
        eq(cause_tag, :c0_collapsed_and_vulnerable_blasting),
        eq(
          explanation_text,
          "此前下游聚落恢复变慢（临界慢化 Strained），79年收走铜钲后阻尼归零（c0=0）；302队点炮正撞在簧片蜂鸣段（易损窗），5刻后推过失稳点"
        )
      ]

      [
        eq(regime, :Strained),
        eq(cause_tag, :mountain_god_breath_disturbed),
        eq(explanation_text, "涧水起鱼鳞碎纹、雀鸟惊飞乃山脉倒换气！四队拆了寨门铜钲断香火，勘探队又在换气口动火药致山神翻身")
      ]

      [
        eq(regime, :Oscillating),
        eq(cause_tag, :secondary_ignition_in_oscillation),
        eq(explanation_text, "谷地明明已陷入周期性自激振荡（Oscillating），302队在回潮易损窗追加爆破，把原本40刻后的尖峰直接提前引爆")
      ]
    end
  end

  def lens_tabooo(lens, cause_tag) do
    conde do
      [
        eq(lens, :OfficialHQ),
        neq(cause_tag, :c0_collapsed_and_vulnerable_blasting),
        neq(cause_tag, :secondary_ignition_in_oscillation),
        neq(cause_tag, :mountain_god_breath_disturbed)
      ]

      [
        eq(lens, :TechInner),
        neq(cause_tag, :autumn_rain_natural_erosion),
        neq(cause_tag, :mountain_god_breath_disturbed)
      ]

      [
        eq(lens, :OldZhai),
        neq(cause_tag, :autumn_rain_natural_erosion),
        neq(cause_tag, :c0_collapsed_and_vulnerable_blasting),
        neq(cause_tag, :secondary_ignition_in_oscillation)
      ]
    end
  end

  def lens_projectiono(lens, initial, propagation, outcome, narrative_record) do
    fresh [phase, outcome_tag, c0, regime, item, item_name, cause_tag, explanation] do
      propso(initial, phase: phase, action_item: item)
      propso(item, name: item_name)
      propso(propagation, c0_baseline: c0, regime: regime)
      propso(outcome, outcome: outcome_tag)
      eq(outcome_tag, :premature_excitation_spike)
      eq(phase, :RelativeRefractory)
      eq(c0, :c0_collapsed)
      membero(lens, [:OfficialHQ, :TechInner, :OldZhai])
      lens_tabooo(lens, cause_tag)
      attribution_candidateo(regime, cause_tag, explanation)

      eq(narrative_record,
        lens: lens,
        regime_context: regime,
        trigger_artifact: item_name,
        adopted_cause_tag: cause_tag,
        record_text: explanation
      )
    end
  end
end
