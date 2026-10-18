package cn.blockforge.generated.maidbettergap.item;

import cn.blockforge.generated.maidbettergap.gap.ContainerService;
import cn.blockforge.generated.maidbettergap.gap.GapLinkData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 隙间终端：不用把隙间从女仆身上取下来就能设置她。
 * - 右键女仆 = 把终端链接到这只女仆，并直接打开设置界面
 *   （界面与「更好的隙间」完全一样，只是改的是女仆饰品栏里那件隙间，实时生效）
 * - 右键空气 = 打开已链接女仆的隙间设置
 * - 潜行+右键空气 = 解除链接
 * - 潜行+右键容器方块 = 给女仆的隙间绑定/解绑容器
 */
public class GapTerminalItem extends Item {

    public GapTerminalItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                if (GapLinkData.hasLink(stack)) {
                    GapLinkData.unlink(stack);
                    player.displayClientMessage(
                            Component.translatable("msg.maidgap.unlinked").withStyle(ChatFormatting.GRAY), true);
                }
            } else {
                // 服务端把目标隙间（手里 or 链接女仆饰品栏里）打包推给客户端开界面
                ContainerService.openTerminal(player);
            }
        }
        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (GapLinkData.hasLink(stack)) {
            String name = GapLinkData.savedName(stack);
            tooltip.add(Component.translatable("maidgap.tt.linked",
                            name.isEmpty() ? "?" : name)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tooltip.add(Component.translatable("maidgap.tt.notlinked").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("maidgap.tt.usage.1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("maidgap.tt.usage.2").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("maidgap.tt.usage.3").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("maidgap.tt.realtime").withStyle(ChatFormatting.GREEN));
    }
}
