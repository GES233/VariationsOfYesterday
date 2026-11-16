package moe.chestnut.awa.voy

import java.nio.file.{Files, Path}

import scala.compiletime.uninitialized

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import moe.chestnut.awa.voy.inner.veins.RhythmParam

/** External configuration loading (`config/variations-of-yesterday.json`). */
class VoyConfigSpec:
  @TempDir var tempDir: Path = uninitialized

  private def configPath: Path = tempDir.resolve(VoyConfig.FileName)

  @Test def `a missing file yields the defaults and is written out for editing`(): Unit =
    val cfg = VoyConfig.loadFrom(configPath)
    assertEquals(VoyConfig(), cfg, "no file must mean exactly the defaults")
    assertTrue(Files.exists(configPath), "defaults must be discoverable on disk")
    assertEquals(cfg, VoyConfig.loadFrom(configPath))

  @Test def `a partial override applies per field and keeps every other default`(): Unit =
    Files.writeString(
      configPath,
      """{
        |  "tickDt": 0.025,
        |  "stressParam": { "gamma": 0.0007 },
        |  "lattice": { "width": 64 },
        |  "heartbeatParam": { "overdriveGuard": 9.6 },
        |  "calmingParam": { "dose": 0.01, "tau": 1800.0 }
        |}""".stripMargin
    )
    val cfg = VoyConfig.loadFrom(configPath)

    assertEquals(0.025, cfg.tickDt, 1e-12)
    assertEquals(0.0007, cfg.stressParam.gamma, 1e-12, "the calibration knob")
    assertEquals(9.6, cfg.rhythmParam.overdriveGuard, 1e-12)
    assertEquals(0.01, cfg.calmingParam.dose, 1e-12)
    assertEquals(1800.0, cfg.calmingParam.tau, 1e-12)
    assertEquals(0.04, cfg.calmingParam.cap, 1e-12, "unspecified fields keep their defaults")
    assertEquals(0.01, VoyConfig().stressParam.gamma, 1e-12)
    assertEquals(1e-3, cfg.stressParam.sustenanceGrowth, 1e-12)
    assertEquals(39.47, cfg.rhythmParam.nominalPeriod, 1e-12)
    assertEquals(64, cfg.lattice.width)
    assertEquals(32, cfg.lattice.height)
    assertEquals(-16, cfg.lattice.originX)
    assertEquals(VoyConfig().fastParam, cfg.fastParam)

  @Test def `an unreadable file falls back to the defaults whole`(): Unit =
    Files.writeString(configPath, "{ not json !")
    assertEquals(
      VoyConfig(),
      VoyConfig.loadFrom(configPath),
      "a malformed file must never half-apply"
    )

  @Test def `rhythm parameters parse per field and keep derived relations`(): Unit =
    Files.writeString(
      configPath,
      """{
        |  "heartbeatParam": { "nominalPeriod": 42.0, "minCycles": 4 }
        |}""".stripMargin
    )
    val cfg = VoyConfig.loadFrom(configPath)
    assertEquals(42.0, cfg.rhythmParam.nominalPeriod, 1e-12)
    assertEquals(4, cfg.rhythmParam.minCycles)
    assertEquals(0.75 * 42.0, cfg.rhythmParam.periodMin, 1e-12, "derived thresholds track")
    assertEquals(RhythmParam().staleCycles, cfg.rhythmParam.staleCycles, 0.0)

  @Test def `derived lattice defaults are written out and reload identically`(): Unit =
    val cfg = VoyConfig.loadFrom(configPath)
    assertEquals(LatticeConfig.DefaultInitialStressPeak, cfg.lattice.initialStressPeak, 1e-12)
    assertEquals(LatticeConfig.DefaultInitialStressFalloff, cfg.lattice.initialStressFalloff, 1e-12)
    assertEquals(cfg, VoyConfig.loadFrom(configPath), "the written defaults must reload exactly")
