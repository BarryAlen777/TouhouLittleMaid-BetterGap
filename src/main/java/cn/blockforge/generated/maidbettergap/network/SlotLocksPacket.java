package cn.blockforge.generated.maidbettergap.network;

import cn.blockforge.generated.maidbettergap.gap.ContainerService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S：槽位配置界面点「完成」，把 38 字节女仆槽位锁定数组写回目标隙间。 */
public class SlotLocksPacket {

    public final byte[] locks;

    public SlotLocksPacket(byte[] locks) {
        this.locks = locks;
    }

    public SlotLocksPacket(FriendlyByteBuf buf) {
        this.locks = buf.readByteArray();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByteArray(locks);
    }

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            ContainerService.applySlotLocks(player, locks);
        }
        ctx.setPacketHandled(true);
    }
}
