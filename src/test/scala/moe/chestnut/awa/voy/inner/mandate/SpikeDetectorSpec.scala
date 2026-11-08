package moe.chestnut.awa.voy.inner.mandate

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** The shared spike state machine (audit B15): the same candidate rule, with
  * confirmation policy and crossing-time convention made explicit.
  */
class SpikeDetectorSpec:
  private def feed(
      detector: SpikeDetector,
      trace: Seq[(Double, Double)]
  ): Seq[DetectedSpike] =
    trace.flatMap { case (t, v) => detector.observe(t, v) }

  /** Upcrosses 1.2 between t=0.10 and t=0.15 (interpolated at 0.12), peaks at
    * 1.9 at t=0.20, then falls through zero at t=0.35. */
  private val riseAndFall: Seq[(Double, Double)] = Seq(
    0.00 -> -1.2,
    0.05 -> -0.4,
    0.10 -> 1.0,
    0.15 -> 1.5,
    0.20 -> 1.9,
    0.25 -> 1.1,
    0.30 -> 0.2,
    0.35 -> -0.4
  )

  @Test def `section-crossing mode emits the interpolated crossing immediately`(): Unit =
    val detector = new SpikeDetector(confirm = SpikeConfirm.AtSectionCrossing)
    val spikes = feed(detector, riseAndFall)
    assertEquals(1, spikes.size)
    assertEquals(0.12, spikes.head.at, 1e-12)
    assertEquals(1.5, spikes.head.peak, 1e-12)

  @Test def `window mode confirms once the positive peak is reached`(): Unit =
    val detector = new SpikeDetector(confirm = SpikeConfirm.WithinWindow(5.0))
    val spikes = feed(detector, riseAndFall)
    assertEquals(1, spikes.size, "a genuine excursion confirms exactly once")
    assertEquals(0.12, spikes.head.at, 1e-12)
    assertEquals(1.9, spikes.head.peak, 1e-12)

  @Test def `zero-crossing mode measures the activation time at the step end`(): Unit =
    val detector = new SpikeDetector(
      confirm = SpikeConfirm.AtZeroCrossing,
      interpolateCrossing = false
    )
    val spikes = feed(detector, riseAndFall)
    assertEquals(1, spikes.size)
    assertEquals(0.15, spikes.head.at, 1e-12)
    assertEquals(1.9, spikes.head.peak, 1e-12)

  @Test def `ghost weld abandons a late peak and a stalled excursion confirms nowhere`(): Unit =
    val latePeak = feed(new SpikeDetector(confirm = SpikeConfirm.WithinWindow(0.02)), riseAndFall)
    assertEquals(0, latePeak.size, "a peak reached after the window is a driven creep")

    val stalled: Seq[(Double, Double)] = Seq(
      0.00 -> -1.0,
      0.10 -> 1.0,
      0.15 -> 1.5,
      0.20 -> 1.7,
      0.25 -> 1.2,
      0.30 -> -0.1
    )
    assertEquals(0, feed(new SpikeDetector(confirm = SpikeConfirm.WithinWindow(5.0)), stalled).size)
    assertEquals(
      0,
      feed(new SpikeDetector(confirm = SpikeConfirm.AtZeroCrossing), stalled).size,
      "a peak below the spike level is not a spike"
    )
    assertEquals(
      1,
      feed(new SpikeDetector(confirm = SpikeConfirm.AtSectionCrossing), stalled).size,
      "the fingerprint mode still reports the bare crossing"
    )
