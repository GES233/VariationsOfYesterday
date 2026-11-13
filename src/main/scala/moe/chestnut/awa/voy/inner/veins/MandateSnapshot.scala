package moe.chestnut.awa.voy.inner.veins

/** Serializable snapshot of the mandate lattice: the complete dynamical
  * memory of the land, as pure data (no Minecraft types), so the engine
  * core stays replayable and unit-testable while the NBT codec lives on
  * the game side.
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
    pulseStamp: PulseStamp = PulseStamp.zero
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
      pulseStamps: Map[CellCoord, PulseStamp] = Map.empty
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
        pulseStamps.getOrElse(coord, PulseStamp.zero)
      )
    }.toSeq.sortBy(c => (c.coord.x, c.coord.z))
    MandateSnapshot(simTime, cells, grid.edges, links)

  /** Extract the per-cell pulse stamps from a captured snapshot. */
  def restorePulseStamps(
      snapshot: MandateSnapshot
  ): Map[CellCoord, PulseStamp] =
    snapshot.cells.iterator
      .filter(_.pulseStamp != PulseStamp.zero)
      .map(cs => cs.coord -> cs.pulseStamp)
      .toMap

  /** Rebuild a lattice from a snapshot. Edges whose endpoints are not
    * both present are dropped.
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
    * used first with a deterministic tie-break.
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
