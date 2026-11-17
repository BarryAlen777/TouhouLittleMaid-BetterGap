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

/** C2S：槽位配置界面点「完成」，把 38 字节女仆槽位锁定数组写回目标隙间。 */
public class SlotLocksPacket implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "slot_locks");
    public static final CustomPacketPayload.Type<SlotLocksPacket> TYPE =
            new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SlotLocksPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.byteArray(256), SlotLocksPacket::locks,
                    SlotLocksPacket::new);

    private final byte[] locks;

    public SlotLocksPacket(byte[] locks) {
        this.locks = locks;
    }

    public byte[] locks() {
        return locks;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 同类错误只完整记一次堆栈，后续只留一行（与 GapActionPacket / OpenTerminalPacket 同款兜底）。 */
    private static final java.util.concurrent.atomic.AtomicBoolean ERROR_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public static void handle(SlotLocksPacket payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            try {
                ContainerService.applySlotLocks(player, payload.locks);
            } catch (LinkageError | RuntimeException err) {
                // applySlotLocks 会走 gapTarget → syncGap → MaidLinkService.syncBauble（车万女仆类调用），
                // 版本对不上时异常绝不能从包处理里冒出去（M5/M7 的入口级最后防线，审查报告 P2）。
                if (ERROR_LOGGED.compareAndSet(false, true)) {
                    GeneratedMod.LOGGER.error("[maid_better_gap] 保存槽位锁定时出错，本次请求已忽略（同类错误后续不再打完整堆栈）。", err);
                } else {
                    GeneratedMod.LOGGER.debug("[maid_better_gap] 保存槽位锁定时出错，本次请求已忽略。", err);
                }
            }
        }
    }
}
