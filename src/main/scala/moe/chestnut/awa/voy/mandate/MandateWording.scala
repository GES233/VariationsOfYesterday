package moe.chestnut.awa.voy.mandate

import moe.chestnut.awa.voy.VoyConfig
import moe.chestnut.awa.voy.inner.veins.*
import moe.chestnut.awa.voy.mandate.BellRitual.{BellBaselineCalming, BellStrikeOutcome}

/** All player-facing wording of the mandate wiring layer.
  *
  * Pure string builders over semantic readouts: what the land did, never raw
  * continuous floats (design note 06 §1 constraint 5). Keeping every message
  * in one place makes the voice auditable and gives the future content
  * pipeline a single surface to localize.
  */
object MandateWording:

  /** Player-facing wording for a mycelial re-inoculation attempt (note 06
    * §7.1): what the soil did with the inoculum, never raw `M`/`s` numbers
    * (note 06 §1 constraint 5).
    */
  def formatReInoculation(
      chunkX: Int,
      chunkZ: Int,
      r: CellReadout,
      outcome: MandatePhenomena.ReInoculationOutcome
  ): String =
    outcome match
      case MandatePhenomena.ReInoculationOutcome.Seeded =>
        f"§a[接种成菌] [$chunkX%d,$chunkZ%d] 菌种落入土层，菌丝开始重新结网（菌网:${MandatePhenomena.sustenanceWord(r.sustenance, r.stressParam.sustenanceFloor)}，养复需时）"
      case MandatePhenomena.ReInoculationOutcome.AcidTooToxic =>
        f"§c[接种失败] [$chunkX%d,$chunkZ%d] 土层酸毒未退，菌种落土即死——须先泄压降酸再接种（菌网:${MandatePhenomena.sustenanceWord(r.sustenance, r.stressParam.sustenanceFloor)}）"
      case MandatePhenomena.ReInoculationOutcome.AlreadyViable =>
        f"§7[接种无益] [$chunkX%d,$chunkZ%d] 此区块菌网尚存活丝（${MandatePhenomena.sustenanceWord(r.sustenance, r.stressParam.sustenanceFloor)}），可自行养复"

  /** Player-facing wording for a slow-variable regime change: what the land is
    * doing, not which enum member it moved to.
    */
  def regimeHerald(regime: Regime): String = regime match
    case Regime.Burnout => "这一带的地脉饱和失能了，得花大力气泄压才能缓回来"
    case Regime.Oscillating => "这一带的地脉开始跳，压不住了"
    case Regime.Strained => "这一带的地脉绷紧了，像要出事"
    case Regime.Calm => "这一带的地脉松下来了"

  /** Clock/compass readout (验山): the institutional dial plus the 3-tier
    * mycelial integrity word, never raw continuous numbers (design note 06
    * §1 constraint 5). Raw values ride the `debugNumbers` gate only. */
  def formatInspection(
      chunkX: Int,
      chunkZ: Int,
      r: CellReadout,
      debugNumbers: Boolean
  ): String =
    val shelter =
      if r.isMaintained then "§a脉动庇护(免除沉滞)"
      else "§c沉滞生效(太久无脉冲/尖峰/烧毁)"
    val base =
      f"§e[验山读数] [$chunkX%d,$chunkZ%d] 态:${regimeWord(r.regime)} 相:${formatPhaseDial(r.phase)} 菌网:${MandatePhenomena.sustenanceWord(r.sustenance, r.stressParam.sustenanceFloor)} | $shelter"
    if debugNumbers then
      val fracText = r.phase match
        case PhaseReading.Cycle(_, frac) => f" frac=$frac%.3f"
        case _ => ""
      val windowLeft = r.pulseState.remainingSuppressionAt(r.simTime)
      base +
        f" §8[s=${r.current.stress}%.3f c0=${r.baselineCalming}%.3f q=${r.current.activity}%.3f M=${r.sustenance}%.2f v=${r.current.dissonance}%.2f p=${r.pulseDrive}%.2f tol=${r.pulseTolerance}%.2f win=${windowLeft}%.1f$fracText]"
    else base

  /** Qualitative regime word for player-facing surfaces; enum names are
    * debug vocabulary. */
  def regimeWord(regime: Regime): String = regime match
    case Regime.Calm => "平静"
    case Regime.Strained => "绷紧"
    case Regime.Oscillating => "起振"
    case Regime.Burnout => "饱和失能"

  /** The institutional dial: 20 ticks per cycle, resolution-limited by
    * design (note 06 §1 constraint 5). Enum labels and fractions stay in
    * `formatPhaseShort` for the dev HUD. */
  def formatPhaseDial(phase: PhaseReading): String =
    phase match
      case PhaseReading.Cycle(label, _) =>
        val tick = Readout.dialTick(phase).getOrElse(0)
        val word = label match
          case PhaseLabel.Excitation => "激发"
          case PhaseLabel.AbsoluteRefractory => "绝对不应"
          case PhaseLabel.RelativeRefractory => "相对不应"
          case PhaseLabel.Resting => "静息"
        f"${word}·第$tick%02d/20格"
      case PhaseReading.Basin(_) => "静息域"
      case PhaseReading.Undefined(reason) =>
        reason match
          case UndefinedReason.BurnoutNoCycle => "静默·深度失能"
          case UndefinedReason.TransientNoCycle => "瞬态未定"
          case UndefinedReason.NoCycleReference => "无周期参照"

  def formatBellPulse(
      chunkX: Int,
      chunkZ: Int,
      r: CellReadout,
      outcome: BellStrikeOutcome
  ): String =
    outcome match
      case BellStrikeOutcome.DeadMyceliumBurnout =>
        f"§4[叩钟无应] [$chunkX%d,$chunkZ%d] 菌网枯绝，钟声如击死肉，连片回音都没有——须先泄压降酸、再接种菌丝"

      case BellStrikeOutcome.PulseOverloadBurnout =>
        f"§c[脉冲过载] [$chunkX%d,$chunkZ%d] 脉冲过密，地脉被顶入烧毁阻滞！本次未压住沉滞，且后续脉冲效力大减——让地脉缓一缓"

      case BellStrikeOutcome.SaturatedNoAnchor =>
        val win = r.pulseState.effectiveWindowAt(r.simTime, VoyConfig.current.pulseParam)
        f"§4[叩钟无锚] [$chunkX%d,$chunkZ%d] 地脉饱和失能 (Burnout)，无法重锚 c0；但本次脉冲仍压住沉滞约 ${win}%.0f 息——根治须持续泄压"

      case BellStrikeOutcome.SpikeSwallowed =>
        val tick = r.dialTick.getOrElse(0)
        f"§c[叩钟失锚] [$chunkX%d,$chunkZ%d] 刻度:第$tick%02d/20格 (Excitation) -> 正处尖峰爆发期，钟声被吞没：c0 未重锚、本次不计心跳(需候不应期推迟窗口)"

      case BellStrikeOutcome.RefractoryReanchored =>
        val tick = r.dialTick.getOrElse(0)
        val label = r.phase match
          case PhaseReading.Cycle(l, _) => l.toString
          case _ => "Refractory"
        val win = r.pulseState.effectiveWindowAt(r.simTime, VoyConfig.current.pulseParam)
        f"§a[叩钟重锚] [$chunkX%d,$chunkZ%d] 刻度:第$tick%02d/20格 ($label) -> 命中推迟刻度，c0已重锚 ($BellBaselineCalming%.2f)，下一次尖峰延后，沉滞退去约 ${win}%.0f 息"

      case BellStrikeOutcome.AnchoredOrMaintained =>
        val win = r.pulseState.effectiveWindowAt(r.simTime, VoyConfig.current.pulseParam)
        r.phase match
          case PhaseReading.Cycle(label, _) =>
            val tick = r.dialTick.getOrElse(0)
            val effectHint = label match
              case PhaseLabel.AbsoluteRefractory | PhaseLabel.RelativeRefractory =>
                "§a命中推迟刻度，下一次地鸣尖峰延后"
              case PhaseLabel.Excitation =>
                "§c正处尖峰爆发期，钟声被地鸣吞没"
              case PhaseLabel.Resting =>
                "§7处于恢复晚期，脉冲被平缓吸收"
            f"§b[叩钟谐振] [$chunkX%d,$chunkZ%d] 刻度:第$tick%02d/20格 ($label) -> $effectHint，沉滞退去约 ${win}%.0f 息"
          case _ =>
            f"§b[叩钟镇山] [$chunkX%d,$chunkZ%d] 维持源已锚定 (c0=$BellBaselineCalming%.2f)，一记脉动荡开，沉滞退去约 ${win}%.0f 息"

  /** Dev rendering (HUD, `debugNumbers`): enum labels and the raw fraction.
    * Player-facing surfaces use `formatPhaseDial` instead. */
  def formatPhaseShort(phase: PhaseReading): String =
    phase match
      case PhaseReading.Cycle(label, frac) =>
        val tick = Readout.dialTick(phase).getOrElse(0)
        f"$label($frac%.2f/第$tick%02d格)"
      case PhaseReading.Basin(label) =>
        s"Basin($label)"
      case PhaseReading.Undefined(reason) =>
        s"Undef($reason)"
