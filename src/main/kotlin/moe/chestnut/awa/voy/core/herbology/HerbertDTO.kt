package moe.chestnut.awa.voy.core.herbology

import moe.chestnut.awa.voy.core.plant.PlantComponent


/**
 * 游戏与核心业务逻辑的交互对象。
 ***/
data class HerbertDTO<T>(
    val herbertType: T,
    val componentType: Collection<PlantComponent>,
    val elementProperties: Map<Any, Any>,
    val extra: Any
)
