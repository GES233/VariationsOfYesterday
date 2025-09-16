package moe.chestnut.awa.voy.helpers

trait NumericTuple[T <: NumericTuple[T]]:
  def toArray: Array[Double]
  def fromArray(interval_numbers: Array[Double]): T

class ODESolver(equation: (Array[Double], Option[Map[String, Any]]) => Array[Double]):
  val useEular: Symbol = Symbol("USE_EULAR")
  val useRK4: Symbol = Symbol("USE_RK4")

  def update[T <: NumericTuple[T]](
      currentState: T,
      dt: Double,
      method: Symbol = this.useRK4,
      extra: Option[Map[String, Any]] = None
  ): T =
    val res: Array[Double] = method match
      case this.useEular => useEular_(currentState.toArray, dt, extra)
      case this.useRK4 => useRK4_(currentState.toArray, dt, extra)
      // case _: Any => new `throws`

    currentState.fromArray(res)

  // ignore absolutely `t`.
  private def useEular_(
      currentState: Array[Double],
      dt: Double,
      extra: Option[Map[String, Any]]
  ): Array[Double] =
    val dot_var = equation(currentState, extra)
    currentState.zip(dot_var).map { case (y_i, y_dot_i) => y_i + dt * y_dot_i }

  private def useRK4_(
      currentState: Array[Double],
      dt: Double,
      extra: Option[Map[String, Any]]
  ): Array[Double] =
    val k1 = equation(currentState, extra)
    val k2 = equation(
      currentState.zip(k1).map { case (yi, k1i) => yi + dt * k1i / 2 },
      extra
    )
    val k3 = equation(
      currentState.zip(k2).map { case (yi, k2i) => yi + dt * k2i / 2 },
      extra
    )
    val k4 = equation(
      currentState.zip(k3).map { case (yi, k3i) => yi + dt * k3i },
      extra
    )

    currentState.zip(k1.zip(k2.zip(k3.zip(k4)))).map {
      case (yi, (k1i, (k2i, (k3i, k4i)))) =>
        yi + dt * (k1i + 2 * k2i + 2 * k3i + k4i) / 6
    }
