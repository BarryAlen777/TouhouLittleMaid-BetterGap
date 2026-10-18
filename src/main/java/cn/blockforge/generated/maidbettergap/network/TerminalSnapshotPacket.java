package cn.blockforge.generated.maidbettergap.network;

import cn.blockforge.generated.maidbettergap.client.GapTerminalScreen;
import cn.blockforge.generated.maidbettergap.gap.TerminalSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
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
        // consumerMainThread：已经处于主线程，直接打开/刷新界面
        Minecraft mc = Minecraft.getInstance();
        if (open) {
            if (!(mc.screen instanceof GapTerminalScreen)) {
                mc.setScreen(new GapTerminalScreen(snapshot));
            } else {
                ((GapTerminalScreen) mc.screen).updateSnapshot(snapshot);
            }
        } else if (mc.screen instanceof GapTerminalScreen screen) {
            screen.updateSnapshot(snapshot);
        }
        ctx.setPacketHandled(true);
    }
}
