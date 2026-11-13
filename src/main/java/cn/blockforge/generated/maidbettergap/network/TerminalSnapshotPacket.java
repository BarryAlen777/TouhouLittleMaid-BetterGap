package cn.blockforge.generated.maidbettergap.network;

import cn.blockforge.generated.maidbettergap.gap.TerminalSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** S2C：把终端快照推给客户端（open=true 时顺便打开界面）。 */
public class TerminalSnapshotPacket {

    public final TerminalSnapshot snapshot;
    public final boolean open;

    public TerminalSnapshotPacket(TerminalSnapshot snapshot, boolean open) {
        this.snapshot = snapshot;
        this.open = open;
    }

    public TerminalSnapshotPacket(FriendlyByteBuf buf) {
        this.snapshot = TerminalSnapshot.decode(buf);
        this.open = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        snapshot.encode(buf);
        buf.writeBoolean(open);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        // 只在客户端把活儿交给 client 包里的处理类。
        // 这个类（含 handle 方法）在服务端也会被加载，所以这里绝不能直接引用
        // Minecraft/Screen 等界面类——专用服务器加载它们会当场崩溃。
        if (FMLEnvironment.dist == Dist.CLIENT) {
            cn.blockforge.generated.maidbettergap.client.TerminalSnapshotClientHandler.handle(this, ctx);
        } else {
            ctx.setPacketHandled(true);
        }
    }
}
