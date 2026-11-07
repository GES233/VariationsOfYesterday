package moe.chestnut.awa.voy.inner.mandate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Snapshot capture/restore round-trip (world persistence, 2026-11-07):
  * the restored lattice must carry the full dynamical memory — state,
  * sustenance, channels, parameters, edges — and nothing else must leak
  * in (orphan edges dropped, dead land stays dead).
  */
class MandateSnapshotSpec:
  private val fast = MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08)
  private val mycelial =
    StressParam(sustenanceGrowth = 1e-3, sustenanceBurn = 4e-3)

  private def mkGrid(): MandateGrid =
    MandateGrid.rectangular(
      3,
      3,
      diffusion = 0.002,
      coord =>
        val cell = MandateState(
          MandateCurrent(
            0.1 * coord.x,
            -0.05 * coord.z,
            0.02 * (coord.x + coord.z),
            0.001 * coord.x
          ),
          fast,
          mycelial
        )
        cell.sustenance = 1.0 - 0.1 * coord.x
        cell.disturbance = 0.01 * coord.x
        cell.calming = 0.005 * coord.z
        cell.baselineCalming = 0.002 * coord.x
        cell.impulse = -0.1
        cell
    )

  @Test def `capture and restore reproduce the lattice exactly`(): Unit =
    val grid = mkGrid()
    val links = Seq(
      PulseLink(CellCoord(0, 0), CellCoord(1, 0)),
      PulseLink(
        CellCoord(2, 2),
        CellCoord(0, 2),
        area = 0.7,
        transmitter = PulseTransmitter.Bell,
        delay = 1.5
      )
    )
    val snap = MandateSnapshot.capture(grid, links, simTime = 12345.6)

    val restored = MandateSnapshot.restoreGrid(snap)
    val reSnap = MandateSnapshot.capture(restored, links, snap.simTime)

    assertEquals(snap, reSnap, "capture -> restore -> capture must be a fixed point")

  @Test def `restore drops edges whose endpoints are not live cells`(): Unit =
    val snap = MandateSnapshot(
      simTime = 10.0,
      cells = Seq(
        CellSnapshot(
          CellCoord(0, 0),
          MandateCurrent(0.0, 0.0, 0.1, 0.0),
          sustenance = 1.0,
          disturbance = 0.0,
          calming = 0.0,
          baselineCalming = 0.0,
          impulse = 0.0,
          fastParam = fast,
          stressParam = mycelial
        )
      ),
      edges = Seq(
        LatticeEdge(CellCoord(0, 0), CellCoord(1, 0), diffusion = 0.002)
      ),
      links = Nil
    )
    val grid = MandateSnapshot.restoreGrid(snap)
    assertTrue(
      grid.edges.isEmpty,
      "an edge touching a missing cell must be dropped, not kept dangling"
    )

  @Test def `a restored dead cell stays dead (M = 0 is absorbing)`(): Unit =
    val snap = MandateSnapshot(
      simTime = 0.0,
      cells = Seq(
        CellSnapshot(
          CellCoord(0, 0),
          MandateCurrent(0.0, 0.0, 0.0, 0.0),
          sustenance = 0.0, // burned out before the save
          disturbance = 0.0,
          calming = 0.0,
          baselineCalming = 0.0,
          impulse = 0.0,
          fastParam = fast,
          stressParam = mycelial
        )
      ),
      edges = Nil,
      links = Nil
    )
    val grid = MandateSnapshot.restoreGrid(snap)
    val cell = grid.cells(CellCoord(0, 0))
    for _ <- 1 to 1000 do cell.step(0.05)
    assertEquals(
      0.0,
      cell.sustenance,
      1e-12,
      "the land must remember a dead patch across a restart"
    )

  @Test def `parameter sets report what the save actually carries`(): Unit =
    val snap = MandateSnapshot.capture(mkGrid(), Nil, 0.0)
    assertEquals(
      Seq((fast, mycelial, 9)),
      MandateSnapshot.parameterSets(snap),
      "a uniform snapshot is one set covering every cell"
    )

    val odd = snap.cells.head.copy(
      fastParam = MandateParam(alpha = 0.65, beta = 0.75, epsilon = 0.1)
    )
    val mixed = snap.copy(cells = snap.cells.updated(0, odd))
    val sets = MandateSnapshot.parameterSets(mixed)

    assertEquals(2, sets.size, "one overridden cell means two sets")
    assertEquals(8, sets.head._3, "the dominant set comes first")
    assertEquals(1, sets.last._3)
    assertEquals((fast, mycelial), (sets.head._1, sets.head._2))
    assertEquals(
      odd.fastParam,
      sets.last._1,
      "the save's own parameters are reported verbatim (save > config)"
    )

