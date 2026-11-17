package cn.blockforge.generated.maidbettergap.network;

import cn.blockforge.generated.maidbettergap.GeneratedMod;
import cn.blockforge.generated.maidbettergap.gap.TerminalSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C：把终端快照推给客户端（open=true 时顺便打开界面）。 */
public class TerminalSnapshotPacket implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(GeneratedMod.MOD_ID, "terminal_snapshot");
    public static final CustomPacketPayload.Type<TerminalSnapshotPacket> TYPE =
            new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSnapshotPacket> STREAM_CODEC =
            StreamCodec.of(TerminalSnapshotPacket::encode, TerminalSnapshotPacket::decode);

    public final TerminalSnapshot snapshot;
    public final boolean open;

    public TerminalSnapshotPacket(TerminalSnapshot snapshot, boolean open) {
        this.snapshot = snapshot;
        this.open = open;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static TerminalSnapshotPacket decode(RegistryFriendlyByteBuf buf) {
        return new TerminalSnapshotPacket(TerminalSnapshot.decode(buf), buf.readBoolean());
    }

    private static void encode(RegistryFriendlyByteBuf buf, TerminalSnapshotPacket payload) {
        payload.snapshot.encode(buf);
        buf.writeBoolean(payload.open);
    }

    public static void handle(TerminalSnapshotPacket payload, IPayloadContext context) {
        // 只在客户端把活儿交给 client 包里的处理类。
        // 这个类（含 handle 方法）在服务端也会被加载，所以这里绝不能直接引用
        // Minecraft/Screen 等界面类——专用服务器加载它们会当场崩溃。
        if (FMLEnvironment.dist == Dist.CLIENT) {
            cn.blockforge.generated.maidbettergap.client.TerminalSnapshotClientHandler.handle(payload, context);
        }
    }
}
