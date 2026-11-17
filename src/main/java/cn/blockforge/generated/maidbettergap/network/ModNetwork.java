package cn.blockforge.generated.maidbettergap.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {

    /**
     * 握手暗号：客户端/服务端连接时核对，不一致就拒绝连接（明确提示版本不匹配）。
     * 【规矩】任何包格式改动（尤其 TerminalSnapshot 的字段增删）必须同步 +1，
     * 否则新旧两端会按不同格式读同一串字节，解码错位、轻则掉线重则崩溃。
     */
    private static final String PROTOCOL = "3";

    private ModNetwork() {}

    /** 挂在模组事件总线上（GeneratedMod 构造里 addListener）。NeoForge 默认在主线程回调 handler。 */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToClient(TerminalSnapshotPacket.TYPE, TerminalSnapshotPacket.STREAM_CODEC,
                TerminalSnapshotPacket::handle);
        registrar.playToServer(GapActionPacket.TYPE, GapActionPacket.STREAM_CODEC,
                GapActionPacket::handle);
        registrar.playToServer(OpenTerminalPacket.TYPE, OpenTerminalPacket.STREAM_CODEC,
                OpenTerminalPacket::handle);
        registrar.playToServer(SlotLocksPacket.TYPE, SlotLocksPacket.STREAM_CODEC,
                SlotLocksPacket::handle);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    public static void sendToServer(CustomPacketPayload packet) {
        PacketDistributor.sendToServer(packet);
    }
}
