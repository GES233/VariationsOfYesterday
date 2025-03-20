package moe.chestnut.awa.voy.herbert

import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier


object HerbertItems {
    // LuminousGrass
    private val LUMINOUS_GRASS: Item = register("luminous_grass", { settings: Item.Settings? -> Item(settings) }, Item.Settings())
    private val LUMINOUS_GRASS_LEAVES: Item = register("luminous_grass_leaves", { settings: Item.Settings? -> Item(settings) }, Item.Settings())
    private val LUMINOUS_GRASS_STEM: Item = register("luminous_grass_stem", { settings: Item.Settings? -> Item(settings) }, Item.Settings())

    private fun register(path: String?, factory: ((Item.Settings?) -> Item), settings: Item.Settings?): Item {
        val registryKey: RegistryKey<Item> = RegistryKey.of(RegistryKeys.ITEM, Identifier.of("voy", path))
        return Items.register(registryKey, factory, settings)
    }

    fun initialize() { }
}
