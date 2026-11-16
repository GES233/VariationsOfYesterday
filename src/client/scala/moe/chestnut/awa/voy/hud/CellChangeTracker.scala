package moe.chestnut.awa.voy.hud

import scala.collection.mutable

import moe.chestnut.awa.voy.inner.veins.Regime
import moe.chestnut.awa.voy.mandate.SyncedCellEntry

/** Client-side tracker that turns the 1 Hz sync stream of the player-centred
  * cell into a short human-readable change history ("近期变化") for the dev
  * HUD: category transitions of heartbeat / regime / mycelium / shelter, plus
  * spike rising edges (the beat made visible). Purely observational; nothing
  * here feeds back into gameplay.
  */
object CellChangeTracker:
  private val Cap = 6

  private final case class Sig(
      regime: Regime,
      myceliumWord: String,
      sheltered: Boolean,
      heartbeat: Int
  )

  private var lastChunk: Option[(Int, Int)] = None
  private var lastSig: Option[Sig] = None
  private var wasSpiking = false
  private val entries = mutable.ArrayDeque.empty[String]

  def reset(): Unit =
    lastChunk = None
    lastSig = None
    wasSpiking = false
    entries.clear()

  /** Visible history lines, oldest first, capped at [[Cap]]. */
  def history: List[String] = entries.toList

  /** Record one observation of the chunk (x, z); safe to call every frame —
    * only category transitions and spike rising edges append lines.
    */
  def record(x: Int, z: Int, entry: Option[SyncedCellEntry]): Unit =
    entry match
      case None => ()
      case Some(e) =>
        if !lastChunk.contains((x, z)) then
          lastChunk = Some((x, z))
          lastSig = None
          wasSpiking = e.view.isSpiking
          push(s"§8— 移至 [$x, $z] —")
        val v = e.view
        // Heartbeat category rides on the debug telemetry (dev/OP only);
        // -1 = unknown when telemetry is withheld.
        val hbOrd = e.debug.map(_.heartbeatOrdinal).getOrElse(-1)
        val sig = Sig(v.regime, v.mycelium.word, v.isSheltered, hbOrd)
        lastSig.foreach { prev =>
          if prev.heartbeat != sig.heartbeat then
            push(s"心跳: ${hbWord(prev.heartbeat)} §7→§r ${hbWord(sig.heartbeat)}${beatNote(e)}")
          if prev.regime != sig.regime then
            push(s"地脉: ${regimeWord(prev.regime)} §7→§r ${regimeWord(sig.regime)}")
          if prev.myceliumWord != sig.myceliumWord then
            push(s"菌网: ${prev.myceliumWord} §7→§r ${sig.myceliumWord}")
          if prev.sheltered != sig.sheltered then
            push(
              s"庇护: ${if prev.sheltered then "庇护" else "沉滞"} §7→§r ${if sig.sheltered then "庇护" else "沉滞"}"
            )
        }
        lastSig = Some(sig)
        if v.isSpiking && !wasSpiking then push(s"§c地鸣尖峰${beatNote(e)}")
        wasSpiking = v.isSpiking

  private def push(line: String): Unit =
    entries.append(line)
    while entries.size > Cap do entries.removeHead()

  private def beatNote(e: SyncedCellEntry): String =
    e.debug.flatMap(_.beatPeriod).map(per => f" §8(周期 $per%.1f s)").getOrElse("")

  private def hbWord(ordinal: Int): String = ordinal match
    case 0 => "预热"
    case 1 => "§a起搏"
    case 2 => "§e过速"
    case 3 => "§e过缓"
    case 4 => "§c失律"
    case _ => "§8?"

  private def regimeWord(r: Regime): String = r match
    case Regime.Calm        => "平静"
    case Regime.Strained    => "绷紧"
    case Regime.Oscillating => "起振"
    case Regime.Burnout     => "饱和失能"
