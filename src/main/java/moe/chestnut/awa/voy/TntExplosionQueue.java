package moe.chestnut.awa.voy;

import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/**
 * TNT 爆炸事件的 Java 侧暂存队列。
 *
 * compileJava 先于 compileScala，Mixin（Java）无法直接调用 Scala 对象，
 * 因此 Mixin 只把爆炸事件压入本队列，由 Scala 侧（PocGame）在 server
 * tick 里轮询消费。事件语义不变：一次 TNT 爆炸 = 一条记录。
 */
public final class TntExplosionQueue {

	/** 一条爆炸记录：世界 registry id + 坐标。 */
	public record Explosion(String worldId, double x, double y, double z) {}

	private static final ConcurrentLinkedQueue<Explosion> QUEUE = new ConcurrentLinkedQueue<>();

	private TntExplosionQueue() {}

	/** 仅接受服务端世界的爆炸；客户端烟花式爆炸不入队。 */
	public static void push(World world, double x, double y, double z) {
		if (world instanceof ServerWorld) {
			QUEUE.add(new Explosion(world.getRegistryKey().getValue().toString(), x, y, z));
		}
	}

	/** 取出一条记录；空队列返回 null（供 Scala 侧 while 轮询）。 */
	public static Explosion poll() {
		return QUEUE.poll();
	}
}
