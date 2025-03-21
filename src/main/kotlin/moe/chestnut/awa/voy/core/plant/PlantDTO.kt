package moe.chestnut.awa.voy.core.plant


typealias CurrentPhase = PlantPhase
typealias NextPhase = PlantPhase

/**
 * 植物本体。
 * */
data class PlantDTO<T>(
    val plantType: T,
    val currentPhase: CurrentPhase,
    val growthContext: Pair<NextPhase, Number>,
    val trait: Map<Any, Any> = mapOf(),
    val inEnv: PlantEnv,
)
