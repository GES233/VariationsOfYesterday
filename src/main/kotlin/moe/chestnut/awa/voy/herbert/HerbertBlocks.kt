package moe.chestnut.awa.voy.herbert

import net.minecraft.block.AbstractBlock
import net.minecraft.block.Block
import net.minecraft.item.BlockItem
import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.sound.BlockSoundGroup
import net.minecraft.util.Identifier


object HerbertBlocks {
    private val LUMINOUS_GRASS: Block = register(
        "luminous_grass",
        { settings: AbstractBlock.Settings? -> Block(settings) },
        AbstractBlock.Settings.create().sounds(BlockSoundGroup.GRASS)
    )


    private fun register(
        path: String,
        blockFactory: ((AbstractBlock.Settings) -> Block),
        settings: AbstractBlock.Settings,
        shouldRegisterItem: Boolean = true
    ): Block {
        val blockKey: RegistryKey<Block> = keyOfBlock(path)
        val block: Block = blockFactory(settings.registryKey(blockKey))

        if (shouldRegisterItem) {
            val blockItemKey: RegistryKey<Item> = keyOfItem(path)
            val blockItem: Item = BlockItem(block, Item.Settings().registryKey(blockItemKey))
            Registry.register(Registries.ITEM, blockItemKey, blockItem)
        }

        return Registry.register(Registries.BLOCK, blockKey, block)
    }

    private fun keyOfBlock(name: String): RegistryKey<Block> {
        return RegistryKey.of(RegistryKeys.BLOCK, Identifier.of("voy", name))
    }

    private fun keyOfItem(name: String): RegistryKey<Item> {
        return RegistryKey.of(RegistryKeys.ITEM, Identifier.of("voy", name))
    }

    fun initialize() {}
}