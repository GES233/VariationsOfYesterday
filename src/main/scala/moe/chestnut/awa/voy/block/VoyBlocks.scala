package moe.chestnut.awa.voy.block

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.block.{AbstractBlock, Block, MapColor}
import net.minecraft.block.enums.NoteBlockInstrument
import net.minecraft.item.{BlockItem, Item, ItemGroups}
import net.minecraft.registry.{Registries, Registry, RegistryKey, RegistryKeys}
import net.minecraft.sound.BlockSoundGroup
import net.minecraft.util.Identifier

/** Block and BlockItem registry for *Variations of Yesterday*. */
object VoyBlocks:
  final val ModId = "variations-of-yesterday"

  val TelluricPlumbId: Identifier = Identifier.of(ModId, "telluric_plumb")
  val TelluricPlumbBlockKey: RegistryKey[Block] =
    RegistryKey.of(RegistryKeys.BLOCK, TelluricPlumbId)
  val TelluricPlumbItemKey: RegistryKey[Item] =
    RegistryKey.of(RegistryKeys.ITEM, TelluricPlumbId)

  lazy val TelluricPlumb: TelluricPlumbBlock =
    val settings = AbstractBlock.Settings
      .create()
      .registryKey(TelluricPlumbBlockKey)
      .mapColor(MapColor.IRON_GRAY)
      .instrument(NoteBlockInstrument.BELL)
      .strength(2.5f, 6.0f)
      .sounds(BlockSoundGroup.LANTERN)
      .nonOpaque()
      .luminance { state =>
        state.get(TelluricPlumbBlock.PHASE) match
          case PlumbPhase.Resting    => 2
          case PlumbPhase.Refractory => 7
          case PlumbPhase.Vulnerable => 9
          case PlumbPhase.Excitation => 13
      }
    new TelluricPlumbBlock(settings)

  lazy val TelluricPlumbItem: BlockItem =
    val itemSettings = new Item.Settings()
      .registryKey(TelluricPlumbItemKey)
      .useBlockPrefixedTranslationKey()
    new BlockItem(TelluricPlumb, itemSettings)

  def register(): Unit =
    Registry.register(Registries.BLOCK, TelluricPlumbBlockKey, TelluricPlumb)
    Registry.register(Registries.ITEM, TelluricPlumbItemKey, TelluricPlumbItem)

    ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register { entries =>
      entries.add(TelluricPlumbItem)
    }
    ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register { entries =>
      entries.add(TelluricPlumbItem)
    }
