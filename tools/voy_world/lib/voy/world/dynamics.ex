defmodule Voy.World.Dynamics do
  @moduledoc "Shared phase, regime, and pulse vocabulary for causal and historical relations."

  use Voy.Kanren

  @regimes [:Calm, :Strained, :Oscillating, :Burnout]
  @phases [:Resting, :Excitation, :AbsoluteRefractory, :RelativeRefractory]

  def regimes, do: @regimes
  def phases, do: @phases

  @phase_signs [
    [
      phase: :Excitation,
      natural_sign: "地鸣落石、涧水翻涌成浑汤",
      instrument_sign: "验山垂架铅锤剧烈撞击木框"
    ],
    [
      phase: :AbsoluteRefractory,
      natural_sign: "林中虫鸣骤停、谷雾贴地倒吸（死寂回吸）",
      instrument_sign: "改装舌簧监听匣陷入绝对静默、挂钟摆锤紧绷"
    ],
    [
      phase: :RelativeRefractory,
      natural_sign: "水生鱼鳞高频驻波、惊鸟乱飞（蓄势躁动）",
      instrument_sign: "验山簧片高频蜂鸣、验裂纸条微颤"
    ],
    [
      phase: :Resting,
      natural_sign: "山涧清澈、薄雾缓行",
      instrument_sign: "垂架铅锤居中不动"
    ]
  ]

  def phase_phenomenono(phase, natural_sign, instrument_sign) do
    tableo(@phase_signs,
      phase: phase,
      natural_sign: natural_sign,
      instrument_sign: instrument_sign
    )
  end

  @disturbance_pulses [
    [disturbance: :periodic_shallow_blasting, pulse: :alpha_shock],
    [
      disturbance: :overwhelming_reverse_suppression,
      pulse: :OverwhelmingNegativeSuppression
    ],
    [disturbance: :low_level_logging_shock, pulse: :heavy_kick]
  ]

  def disturbance_pulseo(disturbance, pulse) do
    tableo(@disturbance_pulses, disturbance: disturbance, pulse: pulse)
  end

  def phase_transitiono(c0_status, regime, current_phase, pulse_type, next_state, lag_tau) do
    conde do
      [
        eq(c0_status, :c0_intact),
        eq(regime, :Calm),
        eq(pulse_type, :heavy_kick),
        eq(next_state, :subthreshold_omen_then_calm),
        eq(lag_tau, :tau_immediate)
      ]

      [
        eq(c0_status, :c0_collapsed),
        any([eq(regime, :Strained), eq(regime, :Oscillating)]),
        eq(current_phase, :RelativeRefractory),
        eq(pulse_type, :alpha_shock),
        eq(next_state, :premature_excitation_spike),
        eq(lag_tau, :tau_5)
      ]

      [
        eq(regime, :Oscillating),
        eq(current_phase, :AbsoluteRefractory),
        eq(pulse_type, :alpha_metal_resonance),
        eq(next_state, :spike_delayed_by_8_6),
        eq(lag_tau, :tau_5)
      ]

      [
        eq(regime, :Oscillating),
        eq(pulse_type, :OverwhelmingNegativeSuppression),
        eq(next_state, :attractor_burnout_annihilation),
        eq(lag_tau, :tau_10)
      ]
    end
  end
end
