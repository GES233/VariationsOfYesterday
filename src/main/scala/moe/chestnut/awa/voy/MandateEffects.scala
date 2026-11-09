package moe.chestnut.awa.voy

import net.minecraft.entity.attribute.{EntityAttributeModifier, EntityAttributes}
import net.minecraft.entity.effect.{StatusEffect, StatusEffectCategory}
import net.minecraft.registry.Registries
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.util.Identifier

/** The `沉滞` (Stagnation) status effects (design draft §3.2, 2026-11-09
  * revision).
  *
  * Stagnation's primary bite lands on the vanilla *economy* (trade prices,
  * breeding, golem summons, crop growth — see `MandateEconomy` and the
  * mixins), restoring the original §7.8 motivation loop. The baseline tier
  * never touches the player's body — villagers wear `Stagnation` as a
  * visible marker of the settlement going listless. The player's body pays
  * during the acute tiers — `StagnationSpike` while the ground is spiking
  * or burnout-saturated (×0.09 break speed, plus vanilla `SLOWNESS` as a
  * secondary cue), and `PulseSaturation` as the immediate recoil for
  * over-pulsing a cell into acute `BurnoutNoCycle`.
  *
  * Why these are *our* effects instead of vanilla `MINING_FATIGUE`: a vanilla
  * effect carries a vanilla name, icon and tooltip, so the pressure reads as
  * "the game is slow" instead of "this land is holding something back". The
  * whole point of the effect is attribution — the player has to be able to
  * point at the thing the land is doing to them.
  *
  * The spike case is a second effect rather than an amplifier level on the
  * first one: `StatusEffect` declares its attribute modifiers once, so an
  * amplifier would scale a number whose meaning is written in the tooltip.
  * Two effects, two fixed multipliers, nothing left implicit.
  *
  * The spike multiplier mirrors the vanilla value it replaces:
  * `MINING_FATIGUE` II = ×0.09 block break speed → [[StagnationSpike]].
  */
object MandateEffects:
  private val Namespace = "variations-of-yesterday"

  val StagnationId: Identifier = Identifier.of(Namespace, "stagnation")
  val StagnationSpikeId: Identifier = Identifier.of(Namespace, "stagnation_spike")
  val PulseSaturationId: Identifier = Identifier.of(Namespace, "pulse_saturation")

  /** 沉滞: marker tier — this ground has had no fresh pulse; the economy
    * sags, the player's hands are spared.
    */
  val Stagnation: StatusEffect = register(
    StagnationId,
    new StagnationEffect(StagnationId, 0x4C5B52, None)
  )

  /** 沉滞·尖峰: what the same ground does to the body while it is spiking
    * (×0.09 break speed).
    */
  val StagnationSpike: StatusEffect = register(
    StagnationSpikeId,
    new StagnationEffect(StagnationSpikeId, 0x6B2F45, Some(-0.91))
  )

  /** 脉冲过载: immediate minor backlash when over-rapid pulses drive a cell into
    * acute `BurnoutNoCycle` (×0.75 break speed, ×0.85 movement speed).
    */
  val PulseSaturation: StatusEffect = register(
    PulseSaturationId,
    new PulseSaturationEffect(PulseSaturationId, 0x8A3B32, -0.25, -0.15)
  )

  /** Registry entries, which is what `StatusEffectInstance` wants. */
  lazy val StagnationEntry: RegistryEntry[StatusEffect] = entryOf(Stagnation)
  lazy val StagnationSpikeEntry: RegistryEntry[StatusEffect] = entryOf(StagnationSpike)
  lazy val PulseSaturationEntry: RegistryEntry[StatusEffect] = entryOf(PulseSaturation)

  private def register(id: Identifier, effect: StatusEffect): StatusEffect =
    net.minecraft.registry.Registry.register(Registries.STATUS_EFFECT, id, effect)

  private def entryOf(effect: StatusEffect): RegistryEntry[StatusEffect] =
    Registries.STATUS_EFFECT.getEntry(effect)

  /** A stagnation effect: an optional block-break-speed modifier, no ticking
    * behaviour. The marker tier passes `None`.
    */
  private final class StagnationEffect(
      id: Identifier,
      color: Int,
      breakSpeedModifier: Option[Double]
  ) extends StatusEffect(StatusEffectCategory.HARMFUL, color):
    breakSpeedModifier.foreach { modifier =>
      this.addAttributeModifier(
        EntityAttributes.BLOCK_BREAK_SPEED,
        Identifier.of(id.getNamespace, s"effect.${id.getPath}"),
        modifier,
        EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
      )
    }

    /** Stagnation is a continuous, ambient suppression: it does nothing on tick. */
    override def canApplyUpdateEffect(duration: Int, amplifier: Int): Boolean = false

  /** Immediate minor backlash effect for over-pulsing a cell into `BurnoutNoCycle`. */
  private final class PulseSaturationEffect(
      id: Identifier,
      color: Int,
      breakSpeedModifier: Double,
      moveSpeedModifier: Double
  ) extends StatusEffect(StatusEffectCategory.HARMFUL, color):
    this.addAttributeModifier(
      EntityAttributes.BLOCK_BREAK_SPEED,
      Identifier.of(id.getNamespace, s"effect.${id.getPath}.break"),
      breakSpeedModifier,
      EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    )
    this.addAttributeModifier(
      EntityAttributes.MOVEMENT_SPEED,
      Identifier.of(id.getNamespace, s"effect.${id.getPath}.move"),
      moveSpeedModifier,
      EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    )

    override def canApplyUpdateEffect(duration: Int, amplifier: Int): Boolean = false
end MandateEffects
