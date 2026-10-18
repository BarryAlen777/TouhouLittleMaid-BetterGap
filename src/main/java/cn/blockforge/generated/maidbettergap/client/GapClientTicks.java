package cn.blockforge.generated.maidbettergap.client;

import cn.blockforge.generated.maidbettergap.GeneratedMod;
import cn.blockforge.generated.maidbettergap.ModItems;
import cn.blockforge.generated.maidbettergap.gap.ContainerService;
import cn.blockforge.generated.maidbettergap.network.ModNetwork;
import cn.blockforge.generated.maidbettergap.network.OpenTerminalPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        // 两只手都要看：终端或隙间放在副手时按 G 也该能打开
        boolean holding = ContainerService.isGapItem(main) || ContainerService.isGapItem(off)
                || main.getItem() == ModItems.GAP_TERMINAL.get()
                || off.getItem() == ModItems.GAP_TERMINAL.get();
        if (holding) {
            ModNetwork.sendToServer(new OpenTerminalPacket());
        }
    }
}
