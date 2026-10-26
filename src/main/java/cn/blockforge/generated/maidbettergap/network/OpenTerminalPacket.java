package cn.blockforge.generated.maidbettergap.network;

import cn.blockforge.generated.maidbettergap.GeneratedMod;
import cn.blockforge.generated.maidbettergap.gap.ContainerService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** C2S：请求打开隙间终端（键位触发时用）。 */
public class OpenTerminalPacket {

    public OpenTerminalPacket() {}

    public OpenTerminalPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {}

    public void handle(Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            try {
                ContainerService.openTerminal(player);
            } catch (LinkageError err) {
                // 同上：版本不匹配时别把 LinkageError 扔进网络线程（会崩服）
                GeneratedMod.LOGGER.error("[maid_better_gap] 打开终端时类链接失败（车万女仆版本不匹配？）", err);
            }
        }
        ctx.setPacketHandled(true);
    }
}
