package cn.blockforge.generated.maidbettergap.network;

import cn.blockforge.generated.maidbettergap.GeneratedMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    private static final String PROTOCOL = "1";
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
