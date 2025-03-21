package moe.chestnut.awa.voy.core.plant

// 需要两种情况：
// 一个是自然的生命周期
// 一个是死亡的判断
enum class PlantPhase {
    GAMMA,
    BREEDERS,
    SPROUT,
    // 体成分堆积
    // 繁殖组分堆积
    // 授粉期
    // 成熟期
    // 衰退、老化
    DEAD_TISSUE,
}