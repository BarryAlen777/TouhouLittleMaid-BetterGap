package cn.blockforge.generated.maidbettergap.network;

import cn.blockforge.generated.maidbettergap.GeneratedMod;
import cn.blockforge.generated.maidbettergap.gap.ContainerService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S：终端界面里的操作（拿取/取桶/解绑/切换拿取数量/切换自动补货）。 */
public class GapActionPacket {

    public final int type;
    public final int entryIndex;
    public final int slot;

    public GapActionPacket(int type, int entryIndex, int slot) {
        this.type = type;
        this.entryIndex = entryIndex;
        this.slot = slot;
    }

    public GapActionPacket(FriendlyByteBuf buf) {
        this.type = buf.readVarInt();
        this.entryIndex = buf.readVarInt();
        this.slot = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(type);
        buf.writeVarInt(entryIndex);
        buf.writeVarInt(slot);
    }

    /** 同类错误只完整记一次堆栈，后续只留一行：数据问题持续存在时不至于刷屏（复审报告 R4）。 */
    private static final java.util.concurrent.atomic.AtomicBoolean ERROR_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        // consumerMainThread：已经处于主线程，直接处理
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            try {
                ContainerService.handleAction(player, type, entryIndex, slot);
            } catch (LinkageError | RuntimeException err) {
                // 车万女仆改了内部方法签名时（我们用的是编译桩）或某次数据没就绪时，
                // 从包处理里漏出去的异常会直接踢崩服务器。这里只丢这一次动作，
                // 别让整局游戏陪葬（M5/M7 的入口级最后防线）。
                if (ERROR_LOGGED.compareAndSet(false, true)) {
                    GeneratedMod.LOGGER.error("[maid_better_gap] 动作处理时出错，本次动作已忽略（同类错误后续不再打完整堆栈）。", err);
                } else {
                    GeneratedMod.LOGGER.debug("[maid_better_gap] 动作处理时出错，本次动作已忽略。", err);
                }
            }
        }
        ctx.setPacketHandled(true);
    }
}
