package cn.blockforge.generated.maidbettergap.item;

import cn.blockforge.generated.maidbettergap.gap.ContainerService;
import cn.blockforge.generated.maidbettergap.gap.GapLinkData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

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
                // 只有“准星对着空气”潜行右键才解链。
                // 以前只要潜行右键就解链，而潜行右键方块正是「绑定容器」的操作，
                // 玩家想绑箱子时会顺手把自己的链接清掉（tooltip 上写的是“潜力右键空气”）。
                if (GapLinkData.hasLink(stack) && lookingAtAir(player)) {
                    GapLinkData.unlink(stack);
                    if (player instanceof ServerPlayer sp) {
                        // 推一次同步，客户端 tooltip 立刻变回“尚未链接”
                        ContainerService.syncHeld(sp);
                    }
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

    /** 准星是不是真的对着空气（服务端也能算：用玩家自己的视线做一次射线检测）。 */
    private static boolean lookingAtAir(Player player) {
        HitResult hit = player.pick(4.5D, 0.0F, false);
        return hit.getType() == HitResult.Type.MISS;
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
