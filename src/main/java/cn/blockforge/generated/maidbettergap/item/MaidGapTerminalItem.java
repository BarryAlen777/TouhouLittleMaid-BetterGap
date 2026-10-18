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
        tooltip.add(Component.translatable("maidgap.tooltip.usage.1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("maidgap.tooltip.usage.2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("maidgap.tooltip.bauble").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("maidgap.tooltip.terminal").withStyle(ChatFormatting.AQUA));
    }
}
