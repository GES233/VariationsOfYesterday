package moe.chestnut.awa.voy

import java.util.UUID

import net.minecraft.nbt.NbtCompound
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

import moe.chestnut.awa.voy.mandate.overworld.ArrivalCore
import moe.chestnut.awa.voy.mandate.overworld.ArrivalCore.{Outcome, State}

class ArrivalCoreSpec:
  private val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
  private val newcomer = UUID.fromString("00000000-0000-0000-0000-000000000002")

  private def reload(core: ArrivalCore): ArrivalCore =
    ArrivalCore.StateType.deserializer.apply(core.writeNbt(NbtCompound(), null), null)

  @Test def `joining and checking eligibility do not collapse the shelter`(): Unit =
    val core = new ArrivalCore
    assertTrue(core.canTrigger(first))
    assertTrue(core.canTrigger(newcomer))
    assertEquals(State.Dormant, core.state)
    assertFalse(core.isDirty)
    assertEquals(State.Dormant, reload(core).state)

  @Test def `two players cause one collapse and each gets one trigger`(): Unit =
    val core = new ArrivalCore
    assertEquals(Outcome.CollapseShelter, core.trigger(first))
    assertTrue(core.isDirty)
    assertEquals(Outcome.PersonalReceipt, core.trigger(newcomer))
    assertEquals(Outcome.AlreadyTriggered, core.trigger(first))
    assertEquals(Outcome.AlreadyTriggered, core.trigger(newcomer))
    assertEquals(State.Spent, core.state)

  @Test def `reload preserves collapse and personal eligibility`(): Unit =
    val core = new ArrivalCore
    core.trigger(first)
    val loaded = reload(core)
    assertEquals(State.Spent, loaded.state)
    assertFalse(loaded.canTrigger(first))
    assertTrue(loaded.canTrigger(newcomer))
    assertEquals(Outcome.AlreadyTriggered, loaded.trigger(first))
    assertEquals(Outcome.PersonalReceipt, loaded.trigger(newcomer))
    assertTrue(reload(loaded).hasTriggered(newcomer))

  @Test def `nourishment is terminal before or after collapse and across reload`(): Unit =
    for collapsed <- List(false, true) do
      val core = new ArrivalCore
      if collapsed then core.trigger(first)
      core.nourish()
      assertTrue(core.isDirty)
      val loaded = reload(core)
      loaded.nourish()
      assertEquals(Outcome.Stable, loaded.trigger(first))
      assertEquals(Outcome.Stable, loaded.trigger(newcomer))
      assertFalse(loaded.canTrigger(newcomer))
      assertFalse(loaded.hasTriggered(newcomer))
      assertEquals(collapsed, loaded.hasTriggered(first))
      assertEquals(State.Nourished, loaded.state)
      assertFalse(loaded.isDirty)
