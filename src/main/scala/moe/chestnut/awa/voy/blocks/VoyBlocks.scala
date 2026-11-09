package moe.chestnut.awa.voy.blocks

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.minecraft.block.{AbstractBlock, Block, MapColor, PillarBlock}
import net.minecraft.component.`type`.FoodComponent
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.{BlockItem, Item, ItemGroup, ItemStack}
import net.minecraft.registry.{Registries, Registry, RegistryKey, RegistryKeys}
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.BlockSoundGroup
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.world.World

import moe.chestnut.awa.voy.mandate.{MandateEffects, MandateWording}

/** Registry holder for the mycelial symbiosis blocks and items
  * (design note 06 §4.3 & WO-2/WO-3 foundation).
  *
  * Minecraft 1.21.2 requires `registryKey` to be stamped onto
  * `AbstractBlock.Settings` and `Item.Settings` before constructing the
  * `Block` or `Item` instance.
  */
object VoyBlocks:
  val Namespace: String = "variations-of-yesterday"

  def id(path: String): Identifier = Identifier.of(Namespace, path)

  private def blockKey(path: String): RegistryKey[Block] =
    RegistryKey.of(RegistryKeys.BLOCK, id(path))

  private def itemKey(path: String): RegistryKey[Item] =
    RegistryKey.of(RegistryKeys.ITEM, id(path))

  /** `菌塘` (Mycelial Bed, `variations-of-yesterday:gastrodia_bed`).
    *
    * Faintly bioluminescent (luminance 2, obeying note 06 §7.3 cap) when its
    * mycelial network is healthy (`condition == 0`).
    */
  val GastrodiaBed: Block = registerBlock(
    "gastrodia_bed",
    key =>
      new GastrodiaBedBlock(
        AbstractBlock.Settings
          .create()
          .registryKey(key)
          .mapColor(MapColor.DIRT_BROWN)
          .strength(1.2f)
          .sounds(BlockSoundGroup.ROOTED_DIRT)
          .ticksRandomly()
          .luminance(state =>
            if state.get(GastrodiaBedBlock.CONDITION).intValue() == GastrodiaBedBlock.ConditionHealthy
            then 2
            else 0
          )
      )
  )

  val GastrodiaBedItem: Item = registerBlockItem("gastrodia_bed", GastrodiaBed)

  /** `荧丝菌材` (Glowsilk Inoculum Log, `variations-of-yesterday:inoculated_timber`).
    *
    * Debarked mountain timber threaded with glowing rhizomorphs (the fictional
    * `荧丝`; its real-world prototype stays in the internal sourcing section of
    * design note 06 §6 and never reaches player-facing text). Serves as
    * companion substrate beside a `菌塘`, refurbishes withered beds, and acts
    * as a rich mycelial inoculum on depleted soil.
    */
  val InoculatedTimber: Block = registerBlock(
    "inoculated_timber",
    key =>
      new PillarBlock(
        AbstractBlock.Settings
          .create()
          .registryKey(key)
          .mapColor(MapColor.OAK_TAN)
          .strength(1.8f)
          .sounds(BlockSoundGroup.WOOD)
          .luminance(_ => 2)
      )
  )

  val InoculatedTimberItem: Item = registerBlockItem("inoculated_timber", InoculatedTimber)

  /** `鲜伏根` (Fresh glowroot tuber, `variations-of-yesterday:fresh_gastrodia`). */
  val FreshGastrodiaItem: Item = registerItem(
    "fresh_gastrodia",
    key =>
      new Item(
        new Item.Settings()
          .registryKey(key)
          .food(
            new FoodComponent.Builder()
              .nutrition(2)
              .saturationModifier(0.3f)
              .build()
          )
      )
  )

  /** `煨伏根` (Roasted Glowroot, `variations-of-yesterday:roasted_gastrodia`).
    *
    * Folk tonic: eating it clears acute `脉冲过载`
    * (`voy:pulse_saturation`) and tremor-induced `SLOWNESS` / `NAUSEA` on the
    * player's body without altering the land's underlying `s` or `M`.
    */
  val RoastedGastrodiaItem: Item = registerItem(
    "roasted_gastrodia",
    key =>
      new RoastedGastrodiaTonicItem(
        new Item.Settings()
          .registryKey(key)
          .food(
            new FoodComponent.Builder()
              .nutrition(6)
              .saturationModifier(0.7f)
              .alwaysEdible()
              .build()
          )
      )
  )

  /** Creative tab for Variations of Yesterday blocks and items. */
  val MainItemGroupKey: RegistryKey[ItemGroup] =
    RegistryKey.of(RegistryKeys.ITEM_GROUP, id("main"))

  val MainItemGroup: ItemGroup = Registry.register(
    Registries.ITEM_GROUP,
    MainItemGroupKey,
    FabricItemGroup
      .builder()
      .icon(() => new ItemStack(GastrodiaBedItem))
      .displayName(Text.translatable("itemGroup.variations-of-yesterday.main"))
      .entries { (_, entries) =>
        entries.add(GastrodiaBedItem)
        entries.add(InoculatedTimberItem)
        entries.add(FreshGastrodiaItem)
        entries.add(RoastedGastrodiaItem)
      }
      .build()
  )

  /** Force object initialization during `ModInitializer.onInitialize`. */
  def register(): Unit =
    val _ = (
      GastrodiaBed,
      GastrodiaBedItem,
      InoculatedTimber,
      InoculatedTimberItem,
      FreshGastrodiaItem,
      RoastedGastrodiaItem,
      MainItemGroup
    )

  private def registerBlock(path: String, factory: RegistryKey[Block] => Block): Block =
    val key = blockKey(path)
    val block = factory(key)
    Registry.register(Registries.BLOCK, key, block)

  private def registerBlockItem(path: String, block: Block): Item =
    val key = itemKey(path)
    val item = new BlockItem(
      block,
      new Item.Settings()
        .registryKey(key)
        .useBlockPrefixedTranslationKey()
    )
    Registry.register(Registries.ITEM, key, item)

  private def registerItem(path: String, factory: RegistryKey[Item] => Item): Item =
    val key = itemKey(path)
    val item = factory(key)
    Registry.register(Registries.ITEM, key, item)

  private final class RoastedGastrodiaTonicItem(settings: Item.Settings) extends Item(settings):
    override def finishUsing(stack: ItemStack, world: World, user: LivingEntity): ItemStack =
      val result = super.finishUsing(stack, world, user)
      if !world.isClient then
        val clearedPulse = user.removeStatusEffect(MandateEffects.PulseSaturationEntry)
        val clearedSlow = user.removeStatusEffect(StatusEffects.SLOWNESS)
        val clearedNausea = user.removeStatusEffect(StatusEffects.NAUSEA)
        val clearedAny = clearedPulse || clearedSlow || clearedNausea
        user match
          case player: ServerPlayerEntity =>
            player.sendMessage(
              Text.literal(MandateWording.formatRoastedGastrodiaRelief(clearedAny)),
              true
            )
          case _ => ()
      result
