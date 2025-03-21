package moe.chestnut.awa.voy.core.herbology

import moe.chestnut.awa.voy.core.plant.PlantComponent


/**
 * 用于描述草药或草药组分的属性的类。
 * */
data class HerbertSchema<T>(
    val herbertType: T,
    val validComponents: Collection<PlantComponent>,
    val functions: Collection<HerbertFunction>,
)
