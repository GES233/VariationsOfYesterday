package moe.chestnut.awa.voy.inner.mandate

import moe.chestnut.awa.voy.helpers.{NumericTuple, ODESolver}

/** Fast-subsystem parameters (FitzHugh–Nagumo).
  *
  * `alpha` is the resting offset a0 of the recovery nullcline; the effective
  * value under land stress `s` is `a(s) = alpha - coupling * s`.
  */
case class MandateParam(alpha: Double, beta: Double, epsilon: Double)
    extends NumericTuple[MandateParam]:
  def toArray: Array[Double] =
    Array(alpha, beta, epsilon)

  def fromArray(numbers: Array[Double]): MandateParam =
    MandateParam(numbers(0), numbers(1), numbers(2))

/** Slow-subsystem (land stress) parameters.
  *
  * Candidate values verified numerically (see design draft section 1.1);
  * rescale against real gameplay time when wiring into the game loop.
  */
case class StressParam(
    gamma: Double = 0.01, // stress gain (global pacing knob)
    delta0: Double = 0.3, // peak natural recovery rate
    sHalf: Double = 0.15, // damage scale at which recovery halves
    rho: Double = 1.0, // calming efficiency
    eta: Double = 1.0, // catastrophe self-excitation gain
    lambda: Double = 0.2, // activity tracer rate
    vSpike: Double = 1.8, // |v| beyond this counts as catastrophe excursion
    coupling: Double = 1.0, // k in a(s) = alpha - k * s
    stressMax: Double = 1.0 // stress is a bounded index; above sMax the fixed
  // point would leave the oscillatory window (a < -0.467) and the system
  // would saturate into a silent "burnout" state instead of raging
)

/** Full state of the mandate system.
  *
  * Fast: `dissonance` (v), `entrench` (w).
  * Slow: `stress` (s, land stress, clamped >= 0), `activity` (q, low-passed
  * catastrophe activity tracer feeding back into stress).
  */
case class MandateCurrent(
    dissonance: Double,
    entrench: Double,
    stress: Double = 0.0,
    activity: Double = 0.0
) extends NumericTuple[MandateCurrent]:
  def toArray: Array[Double] =
    Array(dissonance, entrench, stress, activity)

  def fromArray(numbers: Array[Double]): MandateCurrent =
    MandateCurrent(numbers(0), numbers(1), numbers(2), numbers(3))

/** The mandate system state holder.
  *
  * Equations (design draft section 1.1):
  * {{{
  * dv/dt = v - v^3/3 - w + p
  * dw/dt = epsilon * (v + a(s) - beta * w),  a(s) = alpha - coupling * s
  * ds/dt = gamma * (u - delta(s) * s - rho * (c0 + c) + eta * q)
  * dq/dt = lambda * (max(|v| - vSpike, 0) - q)
  * delta(s) = delta0 / (1 + (s / sHalf)^2)
  * }}}
  */
class MandateState(
    var state: MandateCurrent,
    var param: MandateParam,
    var stressParam: StressParam = StressParam(),
    var time_step: Double = 0.01,
    var timeDeltaAccumulator: Double = 0.0,
):
  // External forcing channels, fed from EventDTO via EventMapper later.
  /** u: disturbance input (blasting, mining, ...). */
  var disturbance: Double = 0.0

  /** c: active calming from player-built modulators. */
  var calming: Double = 0.0

  /** c0: baseline calming from residual rituals (village bells, ...). */
  var baselineCalming: Double = 0.0

  /** p: phase-modulation impulse applied directly to the fast variable
    * (ritual bells, modulator devices). The effect depends on the excitable
    * phase it lands in: quenching a surge in progress, advancing the next
    * one in the vulnerable window, or fizzling during the refractory phase.
    */
  var impulse: Double = 0.0

  /** Alpha-synapse kernel on the fast variable (synaptic-pulse channel for
    * discrete game events: one event = one (area, tau) pulse, see design
    * draft section 1.1.4). Dual-exponential cascade dz/dt = -z/tau,
    * dp/dt = (z - p)/tau driven by delta firings; integrated in closed
    * form between steps, so it stays outside the RK4 state vector.
    * `firePulse` takes the kernel AREA; the peak lands at t = tau with
    * height area / (e * tau).
    */
  var synapseTau: Double = 5.0
  private var synapseZ: Double = 0.0
  private var synapseP: Double = 0.0

  /** Fire one alpha-kernel pulse of total area `area` on the fast variable. */
  def firePulse(area: Double): Unit =
    synapseZ += area / synapseTau

  /** Current kernel output (what dv/dt receives right now). */
  def synapseOutput: Double = synapseP

  /** Net stress influx from spatial coupling (diffusion/advection across
    * the lattice), set by the grid before each step. Raw addition to ds/dt
    * (already carries the transport coefficient).
    */
  var stressFlux: Double = 0.0

  private def equ(
      fullState: Array[Double],
      _extra: Option[Map[String, Any]]
  ): Array[Double] =
    val dis = fullState(0)
    val en = fullState(1)
    val stress = fullState(2)
    val activity = fullState(3)

    val sp = stressParam
    val a = param.alpha - sp.coupling * stress

    val delta_dis = dis - Math.pow(dis, 3) / 3 - en + impulse + synapseP
    val delta_en = param.epsilon * (dis + a - param.beta * en)

    val recovery = sp.delta0 / (1.0 + Math.pow(stress / sp.sHalf, 2)) * stress
    val delta_stress = sp.gamma * (disturbance - recovery -
      sp.rho * (baselineCalming + calming) + sp.eta * activity) + stressFlux
    val delta_activity =
      sp.lambda * (Math.max(Math.abs(dis) - sp.vSpike, 0.0) - activity)

    Array(delta_dis, delta_en, delta_stress, delta_activity)

  val solver = ODESolver(equation = this.equ)

  /** Advance one fixed step; stress is clamped to stay within [0, stressMax]. */
  def step(dt: Double = time_step): Unit =
    // Closed-form decay of the alpha-kernel cascade over this step:
    // z(t) = z0 * e^(-t/tau); p(t) = (p0 + z0 * t/tau) * e^(-t/tau).
    // Updated before the solver so dv/dt sees the fresh kernel output.
    val decay = Math.exp(-dt / synapseTau)
    synapseP = (synapseP + synapseZ * dt / synapseTau) * decay
    synapseZ *= decay
    state = solver.update(state, dt)
    val clamped =
      Math.min(Math.max(state.stress, 0.0), stressParam.stressMax)
    if clamped != state.stress then state = state.copy(stress = clamped)

  /** One-shot stress injection (u channel): raise stress immediately,
    * clamped to [0, stressMax]. */
  def injectStress(amount: Double): Unit =
    val raised = Math.min(Math.max(state.stress + amount, 0.0), stressParam.stressMax)
    state = state.copy(stress = raised)

  def getState: MandateCurrent = state
  def getParam: MandateParam = param
