package moe.chestnut.awa.voy.inner.plant

/** Symbiotic condition of a `天麻床` (Gastrodia bed, design note 06 §4.3).
  *
  * Pure value vocabulary (no Minecraft classes) shared between the readout
  * projection in `MandatePhenomena` and the block state property in
  * `blocks/GastrodiaBedBlock`.
  */
enum GastrodiaBedVerdict(val wire: String):
  /** `s < 0.45` (below `Strained`) and healthy mycelial network (`M >= 0.60`):
    * the Armillaria network feeds the tuber and growth advances.
    */
  case Growing extends GastrodiaBedVerdict("healthy")

  /** `s >= 0.45` (`Strained` or above) or damaged mycelial network
    * (`floor <= M < 0.60`), while not in deep incapacitation: growth stalls.
    */
  case Stalled extends GastrodiaBedVerdict("stalled")

  /** Deep incapacitation (`BurnoutNoCycle`) or dead mycelial network
    * (`M < sustenanceFloor`): the symbiotic threads blacken and the bed
    * withers until refurbished with fresh inoculated timber.
    */
  case Withered extends GastrodiaBedVerdict("withered")

/** Snapshot of a Gastrodia bed's discrete state for pure rules and tests. */
final case class PlantDTO(
    age: Int = 0,
    verdict: GastrodiaBedVerdict = GastrodiaBedVerdict.Growing,
    hasCompanionTimber: Boolean = false
)

