package cn.blockforge.generated.maidbettergap.client;

import cn.blockforge.generated.maidbettergap.GeneratedMod;
import cn.blockforge.generated.maidbettergap.network.ModNetwork;
import cn.blockforge.generated.maidbettergap.network.OpenTerminalPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT)
public final class GapClientTicks {

    private GapClientTicks() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!ModKeys.OPEN_TERMINAL.consumeClick()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.isSpectator()) {
            return;
        }
        // 客户端不再自己判断“手里有没有隙间”：本地配置里的额外隙间白名单（EXTRA_GAP_ITEMS）
        // 可能和服务端不一致，那样按 G 会毫无反应且没有提示。
        // 统一把请求发给服务端，由服务端决定开不开（它有同样的持物检查 + 冷却）。
        ModNetwork.sendToServer(new OpenTerminalPacket());
    }
}
