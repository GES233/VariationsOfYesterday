package moe.chestnut.awa.voy.plant

import net.minecraft.item.Item
import net.minecraft.registry.{RegistryKey, RegistryKeys, Registry, Registries}
import net.minecraft.util.Identifier

object PlantItems {
  def initialize(): Unit = { }

  // LuminousGrass
  private val LUMINOUS_GRASS_LEAVES: Item = register("luminous_grass_leaves", settings => Item(settings), Item.Settings())
  
  private def register(path: String, factory: Item.Settings => Item, settings: Item.Settings): Item = {
    val itemKey: RegistryKey[Item] = RegistryKey.of(RegistryKeys.ITEM, Identifier.of("voy", path))
    val item: Item = factory.apply(settings.registryKey(itemKey))

    Registry.register(Registries.ITEM, itemKey, item)

    item
  }
}
