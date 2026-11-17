package cn.blockforge.generated.maidbettergap.network;

import cn.blockforge.generated.maidbettergap.GeneratedMod;
import cn.blockforge.generated.maidbettergap.gap.ContainerService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S：终端界面里的操作（拿取/取桶/解绑/切换拿取数量/切换自动补货）。 */
public class GapActionPacket implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "gap_action");
    public static final CustomPacketPayload.Type<GapActionPacket> TYPE =
            new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, GapActionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, p -> p.type,
                    ByteBufCodecs.VAR_INT, p -> p.entryIndex,
                    ByteBufCodecs.VAR_INT, p -> p.slot,
                    GapActionPacket::new);

    public final int type;
    public final int entryIndex;
    public final int slot;

    public GapActionPacket(int type, int entryIndex, int slot) {
        this.type = type;
        this.entryIndex = entryIndex;
        this.slot = slot;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 同类错误只完整记一次堆栈，后续只留一行：数据问题持续存在时不至于刷屏（复审报告 R4）。 */
    private static final java.util.concurrent.atomic.AtomicBoolean ERROR_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public static void handle(GapActionPacket payload, IPayloadContext context) {
        // 主线程回调：等价旧版 consumerMainThread，直接处理
        if (context.player() instanceof ServerPlayer player) {
            try {
                ContainerService.handleAction(player, payload.type, payload.entryIndex, payload.slot);
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
    }
}
