package moe.chestnut.awa.voy.helpers

trait NumericTuple:
  def toArray(): Array
  def fromArray(nums: Array): NumericTuple

class ODESolver(equation: (Array[Double], Map[String, Any]) => Array[Double]):
  val useEular: Symbol = Symbol("USE_EULAR")
  val useRK4: Symbol = Symbol("USE_RK4")

  def update(
      currentState: NumericTuple,
      dt: Double,
      method: Symbol = useRK4,
      extra: Map[String, Any] = Map.empty()
  ): Array[Double] =
    val res = method match
      case useEular => useEular_(currentState.toArray(), dt, extra)
      case useRK4   => useRK4_(currentState.toArray(), dt, extra)
      // case _: Any => new `throws`

    getClass(currentState).fromArray(res)

  // ignore t.
  private def useEular_(
      currentState: Array[Double],
      dt: Double,
      extra: Map[String, Any]
  ): Array[Double] =
    val dot_var = equation(currentState, extra)
    currentState.zip(dot_var).map { case (yi, dydti) => yi + dt * dydti }

  private def useRK4_(
      currentState: Array[Double],
      dt: Double,
      extra: Map[String, Any]
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
