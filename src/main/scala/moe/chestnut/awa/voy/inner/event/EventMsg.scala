package moe.chestnut.awa.voy.inner.event

import moe.chestnut.awa.voy.inner.veins.{
  CellCoord,
  LatticeEdge,
  MandateCurrent,
  MandateParam,
  PulseLink,
  PulseTransmitter
}

/** Event means something the model receives from outside: the world, some
  * entities, or the player.
  *
  * `EventDTO` is the inbound protocol of the mandate engine. Design rules
  * (2026-09-30):
  *
  *  - DTOs speak CHANNEL language (stress injection, channel levels,
  *    pulses — the input channels of `MandateState`), not world semantics:
  *    there is no "explosion" or "bell" here. One game occurrence maps to
  *    one or MORE channel events; that 1:N mapping lives on the Minecraft
  *    side, so the simulation core stays free of game vocabulary.
  *  - DTOs are pure data (no Minecraft types) so the event stream can be
  *    logged and replayed for determinism checks (§1.4).
  *  - Events carry no timestamp: causality is their position relative to
  *    `TickArrived` events in the mailbox's total order.
  *  - Events carry no dimension id: engines are per-dimension and routing
  *    happens on the game side.
  *
  * All events live in this file.
  */
sealed trait EventDTO

/** What a physical pulse does to the gameplay pulse ledger (design note 08).
  *
  * This axis is NOT the physics: a pulse enters the fast-variable equations
  * either way. It says whether the same arrival also counts as a *counted
  * pulse* for the discrete pulse mechanisms — shelter window, tolerance book
  * and acute overload lockout (`inner.veins.PulseLedger`).
  *
  * It deliberately does not name the emitter or the reason: the emitter kind
  * already travels on `FirePulse.transmitter`, and the reason a strike does
  * not count belongs to the verdict that refused it (the bell dispatch's
  * `SpikeSwallowed`). The core only needs to know whether the ledger sees
  * this arrival.
  */
enum PulseBookkeeping:
  /** The arrival is counted: it may open a shelter window, charge tolerance,
    * or be refused into an overload lockout.
    */
  case Counted

  /** The pulse only enters the equations: it sounds, but it is not a
    * maintenance act (design note 08 §3).
    */
  case PhysicsOnly

  def counted: Boolean = this == PulseBookkeeping.Counted

object EventMsg:
  // ==== Timer ==== //

  /** One game tick delivered to the engine. Carries the FIXED simulation
    * step plus the server's current nominal tick rate (design draft §1.4,
    * 2026-09-30: tick-driven, simulation time is identical to game time; no
    * wall-clock measurement). `tickRate` stamps the scheduling context into
    * the event stream so the actor stays a pure function of it — it is the
    * trigger input for step coarsening ("weathering"); the actor ignores it
    * until that mechanism lands. */
  final case class TickArrived(deltaTime: Double, tickRate: Float) extends EventDTO

  // ==== Input channels (design draft §1.1) ==== //
  // Game occurrences are translated into these on the Minecraft side, 1:N.

  /** One-shot stress injection: raise the cell's stress immediately,
    * clamped to [0, stressMax]. */
  final case class StressInject(cell: CellCoord, amount: Double) extends EventDTO

  /** Set the sustained disturbance level (u channel). */
  final case class SetDisturbance(cell: CellCoord, rate: Double) extends EventDTO

  /** Set the active calming level (c channel). */
  final case class SetCalming(cell: CellCoord, level: Double) extends EventDTO

  /** Set the baseline calming level (c0 channel). */
  final case class SetBaselineCalming(cell: CellCoord, level: Double) extends EventDTO

  /** Hold the fast-variable impulse at a constant level (p channel,
    * sustained form). */
  final case class HoldImpulse(cell: CellCoord, strength: Double) extends EventDTO

  /** Fire one alpha-synapse pulse on the fast variable (p channel, discrete
    * form). Carries the kernel AREA and the transmitter kind.
    *
    * The kernel time constant is the transmitter's own constant
    * (`PulseTransmitter.tau`), never a free number on the wire: a bell
    * strike keeps the long `tau = 5.0` alpha kernel (`Bell`), a crystal
    * link (`PulseCoupling`, design note 07) fires narrow `tau = 2.0`
    * pulses (`CrystalLink`). Naming the transmitter keeps the event stream
    * the single description of what the fast variable received — that is
    * what makes a coupled replay deterministic — while giving emitters no
    * numeric knob to reshape the kernel. Applying the event sets the
    * cell's `synapseTau` to the transmitter's constant before firing,
    * exactly as the prototype does.
    *
    * `bookkeeping` separates *physics* from *ledger*: the pulse always
    * enters the equations (the bell always sounds), but only a strike the
    * six-way bell dispatch allows to count may enter the pulse ledger, which
    * opens the shelter window, charges tolerance and can be refused into an
    * acute overload lockout. `SpikeSwallowed` fires the pulse with
    * `bookkeeping = PulseBookkeeping.PhysicsOnly` (design note 08 §3);
    * crystal-link pulses, which have no dispatch, are always counted.
    * Keeping the emitter kind on `transmitter` is what lets a later
    * mechanism tell an endogenous/link arrival from an external strike
    * without re-reading the ledger.
    */
  final case class FirePulse(
      cell: CellCoord,
      area: Double,
      transmitter: PulseTransmitter = PulseTransmitter.Bell,
      bookkeeping: PulseBookkeeping = PulseBookkeeping.Counted
  ) extends EventDTO

  /** Set the sustenance level `M ∈ [0, 1]` of a cell — the endogenous
    * supply (mycelial integrity) that holds the fast oscillation inside its
    * window (design note 06, §7.1). `M = 1` is the native state; lowering it
    * collapses the recovery offset
    * `a = alpha - sustenanceDepth * (1 - M) - k * s` toward the burnout
    * knee, so a fully withdrawn cell has no cycle phase no matter how calm it
    * is.
    *
    * This event is an instantaneous SET — the narrative hand (blight,
    * re-inoculation). Between events the engine runs its own Fisher-KPP
    * dynamics (logistic regrowth, acid burn above a stress threshold,
    * lattice diffusion; see `StressParam`), so the level set here is the
    * initial condition, not a held value: a re-inoculation seed grows back,
    * a dead patch inside a large dead region does not. `M = 0` is
    * absorbing — no spontaneous regrowth. */
  final case class SetSustenance(cell: CellCoord, level: Double) extends EventDTO

  // ==== Topology (loading-invariant lattice and wiring changes) ====
  // Topology events share the same mailbox total order as channel events, so
  // replaying the event stream rebuilds the lattice itself, not just its
  // inputs. Worldgen anchors cells and terrain-derived edges to the real
  // world through these; crystal wiring comes and goes through them too.

  /** Register (or fully reset) one lattice cell. Absent `initial` /
    * `fastParam` default to a zero state and the frozen 0.1 parameter set.
    * Re-registering an existing coordinate resets its dynamics but keeps its
    * transport edges. */
  final case class RegisterCell(
      cell: CellCoord,
      initial: Option[MandateCurrent] = None,
      fastParam: Option[MandateParam] = None
  ) extends EventDTO

  /** Remove one cell together with every transport edge that touches it.
    * Pulse links are NOT removed: they are wiring, and may legitimately
    * outlive (or predate) the cells they connect. */
  final case class RemoveCell(cell: CellCoord) extends EventDTO

  /** Insert or replace the transport edge `from -> to` (diffusion and/or
    * advection — worldgen's terrain-derived coupling). Dropped
    * deterministically if either endpoint is not a live cell. */
  final case class UpsertEdge(edge: LatticeEdge) extends EventDTO

  /** Remove the transport edge `from -> to`, if present. */
  final case class RemoveEdge(from: CellCoord, to: CellCoord) extends EventDTO

  /** Register a crystal pulse link (design note 07). Links are event-level
    * wiring, not lattice: they may be registered before their endpoint cells
    * exist, and removing a cell never removes its links. Re-registering a
    * `(src, dst)` pair replaces its parameters. */
  final case class AddLink(link: PulseLink) extends EventDTO

  /** Remove the pulse link `src -> dst`, if present. Pulses it already
    * scheduled are in-flight events and are not retracted. */
  final case class RemoveLink(src: CellCoord, dst: CellCoord) extends EventDTO

  // ==== Persistence control (no dynamical effect) ====

  /** Publish a lattice snapshot NOW, without advancing the clock.
    *
    * This is a control message rather than a world event: `applyEvent`
    * ignores it (it mutates nothing), and the actor only forwards it to the
    * persistence sink. It exists so the save-time handler (`BEFORE_SAVE`)
    * can make the carrier current instead of saving a snapshot up to one
    * persist period old. Safe to drop in a replay: losing it costs the save
    * cadence, never the dynamics. */
  case object FlushNow extends EventDTO
