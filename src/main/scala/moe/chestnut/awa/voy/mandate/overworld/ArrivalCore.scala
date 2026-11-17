package moe.chestnut.awa.voy.mandate.overworld

import java.util.UUID

import net.minecraft.datafixer.DataFixTypes
import net.minecraft.nbt.{NbtCompound, NbtElement, NbtList}
import net.minecraft.registry.RegistryWrapper
import net.minecraft.server.world.ServerWorld
import net.minecraft.world.PersistentState

/** One arrival core per world. Access only on the Minecraft server thread.
  * Joining is read-only; callers trigger explicitly and play effects only
  * after the returned outcome has committed. No mandate-agent state is used.
  */
final class ArrivalCore extends PersistentState:
  import ArrivalCore.*

  private var current = State.Dormant
  private var triggeredPlayers = Set.empty[UUID]

  def state: State = current
  def hasTriggered(player: UUID): Boolean = triggeredPlayers.contains(player)
  def canTrigger(player: UUID): Boolean =
    current != State.Nourished && !hasTriggered(player)

  def trigger(player: UUID): Outcome =
    if current == State.Nourished then Outcome.Stable
    else if hasTriggered(player) then Outcome.AlreadyTriggered
    else
      val first = current == State.Dormant
      current = State.Spent
      triggeredPlayers += player
      markDirty()
      if first then Outcome.CollapseShelter else Outcome.PersonalReceipt

  /** Called when the world-side nourishment condition has been satisfied. */
  def nourish(): Unit =
    if current != State.Nourished then
      current = State.Nourished
      markDirty()

  override def writeNbt(
      nbt: NbtCompound,
      registryLookup: RegistryWrapper.WrapperLookup
  ): NbtCompound =
    nbt.putString("state", current.toString)
    val players = NbtList()
    triggeredPlayers.toVector.sortBy(_.toString).foreach { player =>
      val entry = NbtCompound()
      entry.putUuid("player", player)
      players.add(entry)
    }
    nbt.put("triggeredPlayers", players)
    nbt

object ArrivalCore:
  enum State:
    case Dormant, Spent, Nourished

  enum Outcome:
    case CollapseShelter, PersonalReceipt, AlreadyTriggered, Stable

  val Id = "voy_arrival_core"
  val StateType: PersistentState.Type[ArrivalCore] =
    new PersistentState.Type[ArrivalCore](
      () => new ArrivalCore,
      (nbt, _) => fromNbt(nbt),
      DataFixTypes.LEVEL
    )

  def get(world: ServerWorld): ArrivalCore =
    world.getPersistentStateManager.getOrCreate(StateType, Id)

  private def fromNbt(nbt: NbtCompound): ArrivalCore =
    val core = new ArrivalCore
    core.current = State.valueOf(nbt.getString("state"))
    core.triggeredPlayers = nbt
      .getList("triggeredPlayers", NbtElement.COMPOUND_TYPE)
      .toArray.toList.collect { case entry: NbtCompound => entry.getUuid("player") }
      .toSet
    core
