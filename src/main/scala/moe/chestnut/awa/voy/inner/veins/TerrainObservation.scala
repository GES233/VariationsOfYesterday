package moe.chestnut.awa.voy.inner.veins

/** Immutable column samples, indexed z * 16 + x; heights are block Y.
  * A missing ground column is represented by bottomY - 1.
  */
case class TerrainObservation(
    stage: String,
    generator: String,
    bottomY: Int,
    topY: Int,
    surface: Vector[Int],
    ground: Vector[Int],
    fluidDepth: Vector[Int]
):
  require(Set("surface_stage", "first_full_observation").contains(stage))
  require(topY >= bottomY && bottomY > Int.MinValue)
  require(surface.size == 256 && ground.size == 256 && fluidDepth.size == 256)
  require(surface.forall(y => y >= bottomY - 1 && y <= topY))
  require(ground.forall(y => y >= bottomY - 1 && y <= topY))
  require(surface.zip(ground).forall((s, g) => s >= g))
  require(fluidDepth.zip(surface.zip(ground)).forall { case (depth, (s, g)) =>
    depth >= 0 && depth <= s.toLong - g.toLong
  })

  def medianGround: Option[Double] =
    val valid = ground.filter(_ >= bottomY).sorted
    Option.when(valid.nonEmpty)((valid((valid.size - 1) / 2).toDouble + valid(valid.size / 2)) / 2.0)

  def changedColumns(other: TerrainObservation): Option[Int] =
    Option.when(bottomY == other.bottomY && topY == other.topY)(
      ground.indices.count(i => ground(i) != other.ground(i) || surface(i) != other.surface(i))
    )

object TerrainObservation:
  /** The adapter classifies each block as (ground, fluid); ignored blocks
    * and air are (false, false). Waterlogged ground remains ground.
    */
  def scanColumn(bottom: Int, top: Int)(material: Int => (Boolean, Boolean)): (Int, Int, Int) =
    var y = top
    var surface = bottom - 1
    var ground = bottom - 1
    var fluidOnTop = false
    while y >= bottom && ground < bottom do
      val (solid, fluid) = material(y)
      if surface < bottom && (solid || fluid) then
        surface = y
        fluidOnTop = fluid && !solid
      if solid then ground = y
      y -= 1
    (surface, ground, if fluidOnTop && ground >= bottom then surface - ground else 0)
