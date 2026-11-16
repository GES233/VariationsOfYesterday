package moe.chestnut.awa.voy.mandate

import moe.chestnut.awa.voy.VoyConfig
import moe.chestnut.awa.voy.inner.veins.*

/** Spatial layout & initial-field seam for the dimension's mandate lattice.
  *
  * Isolates *what chunks participate* and *how the initial field is seeded*
  * from the runtime actor and world-side channels, so future world-mode
  * layouts (story valley vs. dynamically expanding ecology) can swap without
  * touching `MandateAgent` or `overworld/` listeners.
  */
trait MandateField:
  /** Geometry fingerprint stored in persistence to detect layout mismatches. */
  def geometrySignature(cfg: VoyConfig): (Int, Int, Int, Int)

  /** Build the initial lattice (cells, transport edges, initial fields). */
  def buildInitialGrid(cfg: VoyConfig): MandateGrid

  /** Map a Minecraft chunk coordinate to its engine cell coordinate. */
  def worldToGrid(chunkX: Int, chunkZ: Int, cfg: VoyConfig): CellCoord

  /** Inverse of [[worldToGrid]]. */
  def gridToWorld(cell: CellCoord, cfg: VoyConfig): (Int, Int)

  /** Whether `(chunkX, chunkZ)` currently belongs to the active simulation. */
  def isLiveChunk(
      chunkX: Int,
      chunkZ: Int,
      snapshot: Map[(Int, Int), CellReadout],
      cfg: VoyConfig
  ): Boolean

/** Current prototype layout: fixed rectangular box centred on `[originX, originX + width)`
  * x `[originZ, originZ + height)` with the calibrated three-tier disturbance field.
  */
object PrototypeValleyField extends MandateField:
  override def geometrySignature(cfg: VoyConfig): (Int, Int, Int, Int) =
    val l = cfg.lattice
    (l.originX, l.originZ, l.width, l.height)

  override def worldToGrid(chunkX: Int, chunkZ: Int, cfg: VoyConfig): CellCoord =
    CellCoord(chunkX - cfg.lattice.originX, chunkZ - cfg.lattice.originZ)

  override def gridToWorld(cell: CellCoord, cfg: VoyConfig): (Int, Int) =
    (cell.x + cfg.lattice.originX, cell.z + cfg.lattice.originZ)

  override def isLiveChunk(
      chunkX: Int,
      chunkZ: Int,
      snapshot: Map[(Int, Int), CellReadout],
      cfg: VoyConfig
  ): Boolean =
    if snapshot.nonEmpty then snapshot.contains((chunkX, chunkZ))
    else
      val l = cfg.lattice
      val c = worldToGrid(chunkX, chunkZ, cfg)
      c.x >= 0 && c.x < l.width && c.z >= 0 && c.z < l.height

  override def buildInitialGrid(cfg: VoyConfig): MandateGrid =
    val l = cfg.lattice
    MandateGrid.rectangular(
      l.width,
      l.height,
      diffusion = l.diffusion,
      coord =>
        val localX = coord.x - l.width / 2
        val localZ = coord.z - l.height / 2
        val distance = Math.sqrt(localX * localX + localZ * localZ)
        val initialStress = Math.max(
          0.0,
          l.initialStressPeak - distance * l.initialStressFalloff
        )
        val state = MandateCurrent(0.0, 0.0, initialStress, 0.0)
        val cell = MandateState(state, cfg.fastParam, cfg.stressParam)
        // Three-tier disturbance field (design note 11 ruling 12): hot core,
        // remnant ring (the 8x8 "old-era pulse"), silent outer land.
        cell.disturbance =
          if distance < l.hotspotRadius then l.hotspotDisturbance
          else if distance < l.remnantRadius then l.remnantDisturbance
          else l.ambientDisturbance
        cell.baselineCalming =
          if distance < l.calmingRadius then l.baselineCalming else 0.0
        cell
    )
