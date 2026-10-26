package cn.blockforge.generated.maidbettergap.network;

import cn.blockforge.generated.maidbettergap.GeneratedMod;
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
            try {
                ContainerService.handleAction(player, type, entryIndex, slot);
            } catch (LinkageError err) {
                // 车万女仆改了内部方法签名时（我们用的是编译桩），网络线程里漏出去的 LinkageError
                // 会直接踢崩服务器。这里只丢这一次动作，别让整局游戏陪葬。
                GeneratedMod.LOGGER.error("[maid_better_gap] 动作处理时类链接失败（车万女仆版本不匹配？）", err);
            }
        }
        ctx.setPacketHandled(true);
    }
}
