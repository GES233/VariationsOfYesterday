package moe.chestnut.awa.voy

import moe.chestnut.awa.voy.inner.mandate.*
import net.minecraft.server.MinecraftServer

/** Small development runtime that bridges the mandate model to game ticks.
  *
  * This is deliberately deterministic and local for the first HUD prototype.
  * A later implementation can replace the fixed lattice with a persisted,
  * world-backed grid without changing the client-facing snapshot API.
  *
  * TODO: the prototype lattice is fixed to world chunks [-16, 15] in both
  * axes, with its disturbance hotspot around chunk (0, 0). Future iterations
  * should anchor it to the spawn point, follow the player's local region, or
  * derive cells from real world topology and persist them per dimension.
  */
object MandateRuntime:
  private val width = 32
  private val height = 32
  private val originX = -width / 2
  private val originZ = -height / 2

  private val grid: MandateGrid = MandateGrid.rectangular(
    width,
    height,
    diffusion = 0.002,
    coord =>
      val localX = coord.x - width / 2
      val localZ = coord.z - height / 2
      val distance = Math.sqrt(localX * localX + localZ * localZ)
      val initialStress = Math.max(0.0, 0.18 - distance * 0.004)
      val state = MandateCurrent(0.0, 0.0, initialStress, 0.0)
      val cell = MandateState(
        state,
        MandateParam(alpha = 0.7, beta = 0.8, epsilon = 0.08),
        time_step = 0.01
      )
      cell.disturbance = if distance < 2.0 then 0.08 else 0.002
      cell.baselineCalming = if distance < 5.0 then 0.03 else 0.0
      cell
  )

  @volatile private var snapshot: Map[(Int, Int), MandateCurrent] = Map.empty

  def tick(server: MinecraftServer): Unit =
    // Keep this prototype active only while a world has a player. It avoids
    // simulating an unused lattice on a dedicated server sitting at the menu.
    if !server.getPlayerManager.getPlayerList.isEmpty then
      grid.step(0.05)
      snapshot = grid.cells.iterator.map { case (coord, state) =>
        (coord.x + originX, coord.z + originZ) -> state.getState
      }.toMap

  def stateAt(chunkX: Int, chunkZ: Int): MandateCurrent =
    snapshot.getOrElse((chunkX, chunkZ), MandateCurrent(0.0, 0.0, 0.0, 0.0))
