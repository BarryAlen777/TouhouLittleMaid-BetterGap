package cn.blockforge.generated.maidbettergap.network;

import cn.blockforge.generated.maidbettergap.GeneratedMod;
import cn.blockforge.generated.maidbettergap.gap.ContainerService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S：请求打开隙间终端（键位触发时用）。 */
public class OpenTerminalPacket implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "open_terminal");
    public static final CustomPacketPayload.Type<OpenTerminalPacket> TYPE =
            new CustomPacketPayload.Type<>(ID);
    /** 无字段包：固定编码成空字节串，解码时直接给单例。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTerminalPacket> STREAM_CODEC =
            StreamCodec.unit(new OpenTerminalPacket());

    /** 同类错误只完整记一次堆栈，后续只留一行（复审报告 R4）。 */
    private static final java.util.concurrent.atomic.AtomicBoolean ERROR_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenTerminalPacket payload, IPayloadContext context) {
        // PayloadRegistrar 默认主线程回调（HandlerThread.MAIN），等价旧版 consumerMainThread
        if (context.player() instanceof ServerPlayer player) {
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
    }
}
