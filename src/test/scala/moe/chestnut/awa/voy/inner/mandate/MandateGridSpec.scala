package moe.chestnut.awa.voy.inner.mandate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import scala.collection.mutable

/** Spatial lattice behaviour: transport conservation, advection
  * directionality, and downstream ignition wavefronts (design draft
  * sections 1.2 and 2.1).
  */
class MandateGridSpec:
  private val dt = 0.05

  private def cellState(
      stressParam: StressParam = StressParam()
  ): MandateState =
    MandateState(
      state = MandateCurrent(dissonance = -1.2, entrench = -0.6),
      param = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08),
      stressParam = stressParam,
      time_step = dt
    )

  private def withStress(state: MandateState, s: Double): MandateState =
    state.state = state.state.copy(stress = s)
    state

  /** Transport only: no local stress dynamics. */
  private val transportOnly = StressParam(gamma = 0.0, delta0 = 0.0)

  @Test def `symmetric diffusion conserves and equalizes stress`(): Unit =
    val cells = Map(
      CellCoord(0, 0) -> withStress(cellState(transportOnly), 0.5),
      CellCoord(1, 0) -> cellState(transportOnly)
    )
    val grid = MandateGrid(
      cells,
      Seq(LatticeEdge(CellCoord(0, 0), CellCoord(1, 0), diffusion = 0.1))
    )

    for _ <- 1 to 10000 do grid.step(dt)

    assertEquals(0.5, grid.totalStress, 1e-9, "diffusion must conserve stress")
    val sa = cells(CellCoord(0, 0)).getState.stress
    val sb = cells(CellCoord(1, 0)).getState.stress
    assertTrue(Math.abs(sa - sb) < 0.01, s"stress should equalize, got $sa vs $sb")

  @Test def `advection carries stress downstream only`(): Unit =
    val upstream = CellCoord(0, 0)
    val downstream = CellCoord(1, 0)
    val edge = LatticeEdge(upstream, downstream, advection = 0.05)

    // Loaded upstream: stress flows downstream, total conserved.
    val cellsA = Map(
      upstream -> withStress(cellState(transportOnly), 0.5),
      downstream -> cellState(transportOnly)
    )
    val gridA = MandateGrid(cellsA, Seq(edge))
    for _ <- 1 to 20000 do gridA.step(dt)
    assertEquals(0.5, gridA.totalStress, 1e-9)
    assertTrue(cellsA(upstream).getState.stress < 0.05, "upstream should drain")
    assertTrue(cellsA(downstream).getState.stress > 0.4, "downstream should fill")

    // Loaded downstream: nothing flows back.
    val cellsB = Map(
      upstream -> cellState(transportOnly),
      downstream -> withStress(cellState(transportOnly), 0.5)
    )
    val gridB = MandateGrid(cellsB, Seq(edge))
    for _ <- 1 to 20000 do gridB.step(dt)
    assertEquals(0.0, cellsB(upstream).getState.stress, 1e-12, "no backflow")
    assertEquals(0.5, cellsB(downstream).getState.stress, 1e-12)

  @Test def `ignition wavefront propagates downstream`(): Unit =
    // Three cells in a line, river flowing c0 -> c1 -> c2.
    val cells = Map(
      CellCoord(0, 0) -> cellState(),
      CellCoord(1, 0) -> cellState(),
      CellCoord(2, 0) -> cellState()
    )
    val grid = MandateGrid(
      cells,
      Seq(
        LatticeEdge(CellCoord(0, 0), CellCoord(1, 0), advection = 0.001),
        LatticeEdge(CellCoord(1, 0), CellCoord(2, 0), advection = 0.001)
      )
    )
    // Sustained overload at the upstream cell only.
    cells(CellCoord(0, 0)).disturbance = 0.08

    val firstSpike = mutable.Map.empty[CellCoord, Double]
    val spikeCount = mutable.Map.empty[CellCoord, Int].withDefaultValue(0)
    val wasSpiking = mutable.Map.empty[CellCoord, Boolean].withDefaultValue(false)

    var t = 0.0
    val tEnd = 12000.0
    while t < tEnd do
      grid.step(dt)
      t += dt
      for (coord, cell) <- cells do
        val spiking = Math.abs(cell.getState.dissonance) > 1.9
        if spiking && !wasSpiking(coord) then
          spikeCount(coord) += 1
          if !firstSpike.contains(coord) then firstSpike(coord) = t
        wasSpiking(coord) = spiking

    for coord <- cells.keys do
      assertTrue(
        spikeCount(coord) >= 3,
        s"cell $coord should ignite and keep spiking, got ${spikeCount(coord)}"
      )
    val Seq(t0, t1, t2) = Seq(CellCoord(0, 0), CellCoord(1, 0), CellCoord(2, 0)).map(firstSpike(_))
    assertTrue(t0 < t1 && t1 < t2, s"wavefront should move downstream: $t0 < $t1 < $t2")

  @Test def `downstream ignition does not reach upstream`(): Unit =
    val cells = Map(
      CellCoord(0, 0) -> cellState(),
      CellCoord(1, 0) -> cellState()
    )
    val grid = MandateGrid(
      cells,
      Seq(LatticeEdge(CellCoord(0, 0), CellCoord(1, 0), advection = 0.001))
    )
    // Overload the DOWNSTREAM cell; the river must not carry it back.
    cells(CellCoord(1, 0)).disturbance = 0.08

    var upstreamSpiked = false
    var t = 0.0
    while t < 6000.0 do
      grid.step(dt)
      t += dt
      if Math.abs(cells(CellCoord(0, 0)).getState.dissonance) > 1.9 then
        upstreamSpiked = true

    assertTrue(!upstreamSpiked, "upstream cell must stay calm")
    assertTrue(
      cells(CellCoord(0, 0)).getState.stress < 0.01,
      s"upstream stress should stay near zero, got ${cells(CellCoord(0, 0)).getState.stress}"
    )
