package cn.blockforge.generated.maidbettergap.item;

import cn.blockforge.generated.maidbettergap.MaidGapConfig;
import cn.blockforge.generated.maidbettergap.gap.ContainerService;
import cn.blockforge.generated.maidbettergap.gap.GapItemData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 更好的隙间（本模组的核心女仆饰品）。
 * - 放进女仆饰品栏：女仆按方向与白名单在箱子↔女仆之间自动搬运
 * - 手持右键空气：打开设置界面（浏览/拿取/白名单配置）
 * - 手持潜行+右键容器方块：绑定/解绑（存在物品 NBT，与原版隙间同格式）
 * - 不想从饰品栏取下时，也可以用「隙间终端」远程设置它（实时生效）
 */
public class MaidGapTerminalItem extends Item {

    public MaidGapTerminalItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            // 服务端把绑定容器与内容快照发给客户端，客户端收到后打开界面
            ContainerService.openTerminal(player);
        }
        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    /**
     * 升级迁移兜底（复审报告 R0）：只要这件隙间躺在玩家背包/快捷栏里，服务端就会周期性走到这里，
     * 把新旧名单洗净写回。不这样兜的话，一件 r23 时期存了脏名单的隙间
     * 可能在玩家还没打开终端前就随背包同步撑爆包上限踢人。
     *
     * 每 20 tick（1 秒）扫一次：扫描成本很低（干净时只做几次 contains），但没必要每刻都来；
     * 而且不再依赖“洗过就跳过”的标记——原版隙间 GUI 之后写进来的脏数据同样能被洗掉（见 sanitizeFilters）。
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity carrier, int slotId, boolean isSelected) {
        if (level.isClientSide()) {
            return;
        }
        if (carrier == null || carrier.tickCount % 20 == 0) {
            GapItemData.sanitizeFilters(stack);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int max = MaidGapConfig.MAX_BOUND_CONTAINERS.get();
        int bound = GapItemData.bindings(stack, "minecraft:overworld").size();
        int filters = 0;
        for (ItemStack f : GapItemData.filters(stack)) {
            if (!f.isEmpty()) {
                filters++;
            }
        }

        tooltip.add(Component.translatable("maidgap.tooltip.io",
                        Component.translatable(GapItemData.isMaidToChest(stack)
                                ? "maidgap.io.maid_to_chest" : "maidgap.io.chest_to_maid")
                                .withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("maidgap.tooltip.filter",
                        Component.translatable(GapItemData.isBlacklist(stack)
                                ? "maidgap.filter.black" : "maidgap.filter.white")
                                .withStyle(ChatFormatting.AQUA),
                        String.valueOf(filters))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("maidgap.tooltip.tag",
                        Component.translatable(GapItemData.isTagMatch(stack)
                                ? "maidgap.gui.on" : "maidgap.gui.off")
                                .withStyle(GapItemData.isTagMatch(stack) ? ChatFormatting.GREEN : ChatFormatting.RED))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("maidgap.tooltip.bound",
                        String.valueOf(bound), String.valueOf(max))
                .withStyle(bound == 0 ? ChatFormatting.YELLOW : ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("maidgap.tooltip.per_container").withStyle(ChatFormatting.DARK_GRAY));
        // 上面几行显示的都是共用名单；有几个容器开了独立名单就补一句，免得“对不上号”（L3）
        int own = GapItemData.ownFilterCount(stack);
        if (own > 0) {
            tooltip.add(Component.translatable("maidgap.tooltip.own_count", String.valueOf(own))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable("maidgap.tooltip.usage.1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("maidgap.tooltip.usage.2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("maidgap.tooltip.bauble").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("maidgap.tooltip.terminal").withStyle(ChatFormatting.AQUA));
    }
}
