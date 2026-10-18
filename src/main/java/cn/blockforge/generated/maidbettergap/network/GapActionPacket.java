package cn.blockforge.generated.maidbettergap.network;

import cn.blockforge.generated.maidbettergap.gap.ContainerService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S：终端界面里的操作（拿取/取桶/解绑/切换拿取数量/切换自动补货）。 */
public class GapActionPacket {

    public final int type;
    public final int entryIndex;
    public final int slot;

    public GapActionPacket(int type, int entryIndex, int slot) {
        this.type = type;
        this.entryIndex = entryIndex;
        this.slot = slot;
    }

    public GapActionPacket(FriendlyByteBuf buf) {
        this.type = buf.readVarInt();
        this.entryIndex = buf.readVarInt();
        this.slot = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(type);
        buf.writeVarInt(entryIndex);
        buf.writeVarInt(slot);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        // consumerMainThread：已经处于主线程，直接处理
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            ContainerService.handleAction(player, type, entryIndex, slot);
        }
        ctx.setPacketHandled(true);
    }
}
