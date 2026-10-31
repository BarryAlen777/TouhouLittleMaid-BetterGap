package cn.blockforge.generated.maidbettergap.network;

import cn.blockforge.generated.maidbettergap.GeneratedMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    /**
     * 握手暗号：客户端/服务端连接时核对，不一致就拒绝连接（明确提示版本不匹配）。
     * 【规矩】任何包格式改动（尤其 TerminalSnapshot 的字段增删）必须同步 +1，
     * 否则新旧两端会按不同格式读同一串字节，解码错位、轻则掉线重则崩溃。
     */
    private static final String PROTOCOL = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int index = 0;

    private ModNetwork() {}

    public static void init() {
        CHANNEL.messageBuilder(TerminalSnapshotPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TerminalSnapshotPacket::encode)
                .decoder(TerminalSnapshotPacket::new)
                .consumerMainThread(TerminalSnapshotPacket::handle)
                .add();
        CHANNEL.messageBuilder(GapActionPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GapActionPacket::encode)
                .decoder(GapActionPacket::new)
                .consumerMainThread(GapActionPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenTerminalPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenTerminalPacket::encode)
                .decoder(OpenTerminalPacket::new)
                .consumerMainThread(OpenTerminalPacket::handle)
                .add();
        CHANNEL.messageBuilder(SlotLocksPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SlotLocksPacket::encode)
                .decoder(SlotLocksPacket::new)
                .consumerMainThread(SlotLocksPacket::handle)
                .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}
