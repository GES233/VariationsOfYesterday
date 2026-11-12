package moe.chestnut.awa.voy.inner.veins

/** Serializable snapshot of the mandate lattice: the complete dynamical
  * memory of the land, as pure data (no Minecraft types), so the engine
  * core stays replayable and unit-testable while the NBT codec lives on
  * the game side.
  *
  * What is captured (and why):
  *
  *  - per-cell fast/slow state (`current`) and `sustenance` M — the core
  *    memory: `M = 0` is absorbing, so a dead patch must still be dead
  *    after a restart (design note 06 §7.1: the land remembers);
  *  - channel levels (`disturbance`, `calming`, `baselineCalming`,
  *    `impulse`) — sustained game-side settings, not derivable from the
  *    dynamics;
  *  - per-cell parameters (`fastParam`, `stressParam`) — `RegisterCell`
  *    allows per-cell parameter sets, so the snapshot may not assume the
  *    global defaults;
  *  - transport `edges` and crystal `links` — topology is data-driven via
  *    topology events, and links are player-built wiring;
  *  - `simTime` — the actor's integration clock, so pulse delays and
  *    phase readouts continue across a restart;
  *  - per-cell pulse ledger (`PulseLedger`: shelter window, tolerance book,
  *    acute overload lockout) — the discrete pulse mechanisms are closed-form
  *    on `simTime`, so they must be saved or a restart would re-open a
  *    shelter window, forget accumulated tolerance, or lift a lockout early
  *    (design note 08 §2.4). One ledger per cell, four doubles on the wire.
  *
  * What is deliberately NOT captured (transient, self-reconstructing):
  * the alpha-synapse kernel state (decays within ~5 s), the flux scratch
  * fields (recomputed every step), spike/phase detectors and pending
  * link pulses (all re-derive within one fast cycle), and the regime
  * bookkeeping (a restored cell adopts its current regime as baseline,
  * the same semantics as a cell registered after startup — no spurious
  * transitions).
  */
final case class CellSnapshot(
    coord: CellCoord,
    current: MandateCurrent,
    sustenance: Double,
    disturbance: Double,
    calming: Double,
    baselineCalming: Double,
    impulse: Double,
    fastParam: MandateParam,
    stressParam: StressParam,
    pulseLedger: PulseLedger = PulseLedger.zero
)

/** The full snapshot: cells, edges, links, and the integration clock. */
final case class MandateSnapshot(
    simTime: Double,
    cells: Seq[CellSnapshot],
    edges: Seq[LatticeEdge],
    links: Seq[PulseLink]
)

object MandateSnapshot:
  /** Capture the live lattice, in deterministic coordinate order. */
  def capture(
      grid: MandateGrid,
      links: Seq[PulseLink],
      simTime: Double,
      pulseLedgers: Map[CellCoord, PulseLedger] = Map.empty
  ): MandateSnapshot =
    val cells = grid.cells.iterator.map { case (coord, state) =>
      CellSnapshot(
        coord,
        state.getState,
        state.sustenance,
        state.disturbance,
        state.calming,
        state.baselineCalming,
        state.impulse,
        state.getParam,
        state.stressParam,
        pulseLedgers.getOrElse(coord, PulseLedger.zero)
      )
    }.toSeq.sortBy(c => (c.coord.x, c.coord.z))
    MandateSnapshot(simTime, cells, grid.edges, links)

  /** Extract the per-cell pulse ledger from a captured snapshot. */
  def restorePulseLedgers(
      snapshot: MandateSnapshot
  ): Map[CellCoord, PulseLedger] =
    snapshot.cells.iterator
      .filter(_.pulseLedger != PulseLedger.zero)
      .map(cs => cs.coord -> cs.pulseLedger)
      .toMap

  /** Rebuild a lattice from a snapshot. Edges whose endpoints are not
    * both present are dropped, the same determinism the mailbox applies
    * to `UpsertEdge` outside the live lattice. Channel levels are set
    * directly (the snapshot bypasses the event protocol: it is injected
    * at construction, before the actor starts, exactly like the initial
    * links).
    */
  def restoreGrid(snapshot: MandateSnapshot): MandateGrid =
    val cells = snapshot.cells.map { cs =>
      val state = MandateState(cs.current, cs.fastParam, cs.stressParam)
      state.disturbance = cs.disturbance
      state.calming = cs.calming
      state.baselineCalming = cs.baselineCalming
      state.impulse = cs.impulse
      state.sustenance = cs.sustenance
      cs.coord -> state
    }.toMap
    val edges = snapshot.edges.filter(e =>
      cells.contains(e.from) && cells.contains(e.to)
    )
    MandateGrid(cells, edges)

  /** Distinct `(fastParam, stressParam)` pairs carried by the snapshot, most
    * used first with a deterministic tie-break. This is what the save
    * actually holds; the wiring layer logs it on load so a tuned config
    * that does *not* apply to an existing world is visible instead of
    * silent (precedence on restore: save > config > core default).
    */
  def parameterSets(
      snapshot: MandateSnapshot
  ): Seq[(MandateParam, StressParam, Int)] =
    snapshot.cells
      .groupBy(cs => (cs.fastParam, cs.stressParam))
      .toSeq
      .map { case ((fast, stress), cells) =>
        (fast, stress, cells.size)
      }
      .sortBy { case (fast, stress, count) =>
        (
          -count,
          fast.alpha,
          fast.beta,
          fast.epsilon,
          stress.gamma,
          stress.coupling
        )
      }
