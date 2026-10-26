package cn.blockforge.generated.maidbettergap.event;

import cn.blockforge.generated.maidbettergap.GeneratedMod;
import cn.blockforge.generated.maidbettergap.ModItems;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 成就页签的补发逻辑。
 *
 * 两个成就（「更好的女仆搬运」「终端管理！」）挂在模组自己的「更好的隙间」页签下。
 * 页签的根节点用 minecraft:impossible 触发、由这里在玩家进游戏时补发一次：
 * 这样两个成就的父节点永远处于“已完成”，玩家打开成就界面看到的是正常图标，
 * 不会变成一堆「???」。
 *
 * 顺带处理老存档：玩家进游戏时背包里已经有这两件物品的，直接把对应成就补上，
 * 不需要把物品丢掉再捡一次。
 */
@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID)
public final class AdvancementHandler {

    private static final String CRITERION_ROOT = "granted";
    private static final String CRITERION_ITEM = "obtained";

    private AdvancementHandler() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        // 页签根节点
        grant(server, player, "root", CRITERION_ROOT);
        // 老存档补偿：背包里已经有这两件东西的话，成就直接补上
        if (hasItem(player, ModItems.MAID_GAP_TERMINAL.get())) {
            grant(server, player, "better_maid_transport", CRITERION_ITEM);
        }
        if (hasItem(player, ModItems.GAP_TERMINAL.get())) {
            grant(server, player, "terminal_manager", CRITERION_ITEM);
        }
    }

    private static boolean hasItem(ServerPlayer player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    private static void grant(MinecraftServer server, ServerPlayer player, String path, String criterion) {
        Advancement advancement = server.getAdvancements()
                .getAdvancement(ResourceLocation.tryBuild(GeneratedMod.MOD_ID, path));
        if (advancement == null) {
            // 数据包没加载出来（被别的模组/数据包改坏了）时静默跳过，别影响进游戏
            return;
        }
        PlayerAdvancements progress = player.getAdvancements();
        if (!progress.getOrStartProgress(advancement).isDone()) {
            progress.award(advancement, criterion);
        }
    }
}
