package cn.blockforge.generated.maidbettergap.network;

import cn.blockforge.generated.maidbettergap.GeneratedMod;
import cn.blockforge.generated.maidbettergap.gap.ContainerService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S：请求打开隙间终端（键位触发时用）。 */
public class OpenTerminalPacket {

    public OpenTerminalPacket() {}

    public OpenTerminalPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    /** 同类错误只完整记一次堆栈，后续只留一行（复审报告 R4）。 */
    private static final java.util.concurrent.atomic.AtomicBoolean ERROR_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            try {
                ContainerService.openTerminal(player);
            } catch (LinkageError | RuntimeException err) {
                // 同上：版本不匹配或数据异常时，别让异常从包处理里冒出去（会崩服）
                if (ERROR_LOGGED.compareAndSet(false, true)) {
                    GeneratedMod.LOGGER.error("[maid_better_gap] 打开终端时出错，本次请求已忽略（同类错误后续不再打完整堆栈）。", err);
                } else {
                    GeneratedMod.LOGGER.debug("[maid_better_gap] 打开终端时出错，本次请求已忽略。", err);
                }
            }
        }
        ctx.setPacketHandled(true);
    }
}
