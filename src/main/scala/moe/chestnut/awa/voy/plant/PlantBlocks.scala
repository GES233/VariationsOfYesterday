package moe.chestnut.awa.voy.plant

import net.minecraft.block.{AbstractBlock, Block}
import net.minecraft.item.{BlockItem, Item, Items}
import net.minecraft.registry.{Registries, Registry, RegistryKey, RegistryKeys}
import net.minecraft.sound.BlockSoundGroup
import net.minecraft.util.Identifier

object PlantBlocks:
  def initialize(): Unit = { }

  private val LUMINOUS_GRASS: Block = register(
    "luminous_grass",
    settings => Block(settings),
    AbstractBlock.Settings.create().sounds(BlockSoundGroup.GRASS),
    true
  )

  private def register(
                        name: String,
                        factory: AbstractBlock.Settings => Block,
                        settings: AbstractBlock.Settings,
                        shouldRegisterItem: Boolean): Block =
    val blockKey = keyOfBlock(name)
    val block = factory(settings.registryKey(blockKey))

    if shouldRegisterItem then
      val itemKey = keyOfItem(name)
      val item = new BlockItem(block, new Item.Settings().registryKey(itemKey))
      Registry.register(Registries.ITEM, itemKey, item)

    Registry.register(Registries.BLOCK, blockKey, block)

    block

  private def keyOfBlock(name: String): RegistryKey[Block] =
    RegistryKey.of[Block](RegistryKeys.BLOCK, Identifier.of("voy", name))

  private def keyOfItem(name: String): RegistryKey[Item] =
    RegistryKey.of[Item](RegistryKeys.ITEM, Identifier.of("voy", name))
