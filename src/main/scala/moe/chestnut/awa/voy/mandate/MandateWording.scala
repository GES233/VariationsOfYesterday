package moe.chestnut.awa.voy.mandate

import moe.chestnut.awa.voy.inner.plant.GastrodiaBedVerdict
import moe.chestnut.awa.voy.inner.veins.*
import moe.chestnut.awa.voy.mandate.BellRitual.BellOutcome

/** All player-facing wording of the mandate wiring layer.
  *
  * Pure string builders over semantic readouts: what the land did, never raw
  * continuous floats.
  */
object MandateWording:

  def formatReInoculation(
      chunkX: Int,
      chunkZ: Int,
      r: CellReadout,
      outcome: MandatePhenomena.ReInoculationOutcome,
      richTimber: Boolean = false
  ): String =
    val netWord = r.mycelium.word
    outcome match
      case MandatePhenomena.ReInoculationOutcome.Seeded =>
        if richTimber then
          f"§a[段木发菌] [$chunkX%d,$chunkZ%d] 熟化菌材埋入土层，琥珀色菌索迅速扎根结网（菌网:$netWord，起步更稳）"
        else
          f"§a[接种成菌] [$chunkX%d,$chunkZ%d] 菌种落入土层，菌丝开始重新结网（菌网:$netWord，养复需时）"
      case MandatePhenomena.ReInoculationOutcome.AcidTooToxic =>
        f"§c[接种失败] [$chunkX%d,$chunkZ%d] 土层酸毒未退，菌种落土即死——须先泄压降酸再接种（菌网:$netWord）"
      case MandatePhenomena.ReInoculationOutcome.AlreadyViable =>
        f"§7[接种无益] [$chunkX%d,$chunkZ%d] 此区块菌网尚存活丝（$netWord），可自行养复"

  def formatGastrodiaInspection(
      age: Int,
      maxAge: Int,
      verdict: GastrodiaBedVerdict,
      hasCompanionTimber: Boolean,
      r: CellReadout
  ): String =
    val stageWord = age match
      case 0 => "初下种·菌丝搭桥"
      case 1 => "幼根结珠·菌丝缠结"
      case 2 => "块根饱浆·将熟"
      case _ => "块根丰熟·可起挖"
    val timberNote = if hasCompanionTimber then "段木伴栽" else "无伴栽菌材"
    val netWord = r.mycelium.word
    verdict match
      case GastrodiaBedVerdict.Growing =>
        f"§a[翻看菌塘] $stageWord ($age%d/$maxAge%d · $timberNote) — 地脉平静、菌网$netWord，荧丝正持续供养伏根"
      case GastrodiaBedVerdict.Stalled =>
        f"§e[翻看菌塘] $stageWord ($age%d/$maxAge%d · $timberNote) — 地脉${regimeWord(r.regime)}、菌网$netWord，菌丝失水停滞，须安抚地脉或养复菌网"
      case GastrodiaBedVerdict.Withered =>
        f"§4[翻看菌塘] 菌塘已枯 (菌网:$netWord) — 荧丝黑腐断绝，须待菌网恢复、酸毒减退后，用菌材重铺；重铺后若地脉仍失调，菌塘暂缓生长"

  def formatGastrodiaHarvest(count: Int, hasCompanionTimber: Boolean): String =
    if hasCompanionTimber then
      f"§a[掘根入篓] 伴栽菌材菌丝肥厚，起出鲜伏根 $count%d 枚，菌塘留种重发"
    else
      f"§a[掘根入篓] 拨开腐殖土起出鲜伏根 $count%d 枚，菌塘留种重发（旁置菌材可增产提速）"

  def formatGastrodiaRefurbish(success: Boolean, r: CellReadout): String =
    val netWord = r.mycelium.word
    if success then
      f"§a[重铺菌塘] 剔去黑腐死丝、换入新菌材，菌塘重新下种（菌网:$netWord）"
    else
      f"§c[重铺无果] 菌网枯绝或酸毒未退（态:${regimeWord(r.regime)} 菌网:$netWord），此时换入菌材亦会黑腐"

  def formatTimberFieldInoculation(success: Boolean, r: CellReadout): String =
    val netWord = r.mycelium.word
    if success then
      f"§a[段木发菌] 借土层完好菌网引菌入木，去皮原木已发成荧丝菌材"
    else
      f"§e[发菌未成] 野外段木发菌需地脉平静且菌网完好（当前 态:${regimeWord(r.regime)} 菌网:$netWord；亦可由工作台合制）"

  def formatRoastedGastrodiaRelief(clearedEffects: Boolean): String =
    if clearedEffects then
      "§b[煨根定风] 温热药气散入四肢，地脉震麻与迟滞消退了"
    else
      "§7[煨根入腹] 焦香温润，神思安定了些许"

  def regimeHerald(regime: Regime): String = regime match
    case Regime.Burnout => "这一带的地脉饱和失能了，得花大力气泄压才能缓回来"
    case Regime.Oscillating => "这一带的地脉开始跳，压不住了"
    case Regime.Strained => "这一带的地脉绷紧了，像要出事"
    case Regime.Calm => "这一带的地脉松下来了"

  def formatInspection(
      chunkX: Int,
      chunkZ: Int,
      r: CellReadout,
      debugNumbers: Boolean
  ): String =
    val shelter =
      if r.isSheltered then "§a心跳庇护(节律在线)"
      else "§c沉滞生效(无心跳/过缓/失律)"
    val base =
      f"§e[验山读数] [$chunkX%d,$chunkZ%d] 态:${regimeWord(r.regime)} 相:${formatPhaseDial(r.phase)} 菌网:${r.mycelium.word} | $shelter"
    if debugNumbers then
      val fracText = r.phase match
        case PhaseReading.Cycle(_, frac) => f" frac=$frac%.3f"
        case _ => ""
      // Heartbeat axis observability (design note 11).
      val hbText = r.heartbeat match
        case Heartbeat.Beating(per, duty, src) =>
          f" hb=Beating($src) per=$per%.1f duty=$duty%.3f"
        case Heartbeat.Fast(per) => f" hb=Fast per=$per%.1f"
        case Heartbeat.Slow(per) =>
          if per.isNaN then " hb=Slow(--)" else f" hb=Slow per=$per%.1f"
        case Heartbeat.Arrhythmic(per, duty) =>
          f" hb=Arrhythmic per=$per%.1f duty=$duty%.3f"
        case Heartbeat.Warming => " hb=Warming"
      base +
        f" §8[s=${r.current.stress}%.3f c0=${r.baselineCalming}%.3f q=${r.current.activity}%.3f M=${r.sustenance}%.2f v=${r.current.dissonance}%.2f p=${r.pulseDrive}%.2f$fracText$hbText]"
    else base

  def regimeWord(regime: Regime): String = regime match
    case Regime.Calm => "平静"
    case Regime.Strained => "绷紧"
    case Regime.Oscillating => "起振"
    case Regime.Burnout => "饱和失能"

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
          case UndefinedReason.SlowOffsetBelowKnee => "静默·深度失能"
          case UndefinedReason.TransientNoCycle => "瞬态未定"
          case UndefinedReason.NoCycleReference => "无周期参照"

  def formatBellPulse(
      chunkX: Int,
      chunkZ: Int,
      outcome: BellOutcome
  ): String =
    outcome match
      case BellOutcome.NoAnswer =>
        f"§4[叩钟无应] [$chunkX%d,$chunkZ%d] 菌网枯绝或地脉失能，钟声如击死肉，连片回音都没有——须先泄压降酸、再接种菌丝"

      case BellOutcome.Overdriven =>
        f"§c[叩钟过密] [$chunkX%d,$chunkZ%d] 钟声未息又起一记，过驱动反震沿铜钲回撞上臂——稍候片刻再叩"

      // One wording for every landed strike (note 12 §4.2, ruling 16): the bell
      // no longer claims to know where the cycle was, so 校拍/静息/失锚 leave
      // the verdict path. They survive, if at all, as a delayed echo
      // (`PulseOutcome`, note 12 §4.3).
      case BellOutcome.Struck =>
        f"§b[叩钟] [$chunkX%d,$chunkZ%d] 一记脉动沉进地脉，维持源已锚定——守住节拍，山便醒着"

  def formatPhaseShort(phase: PhaseReading): String =
    phase match
      case PhaseReading.Cycle(label, frac) =>
        val tick = Readout.dialTick(phase).getOrElse(0)
        f"$label($frac%.2f/第$tick%02d格)"
      case PhaseReading.Basin(label) =>
        s"Basin($label)"
      case PhaseReading.Undefined(reason) =>
        s"Undef($reason)"
