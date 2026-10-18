package cn.blockforge.generated.maidbettergap.compat.tlm;

import cn.blockforge.generated.maidbettergap.ModItems;
import cn.blockforge.generated.maidbettergap.gap.ContainerService;
import cn.blockforge.generated.maidbettergap.gap.GapItemData;
import cn.blockforge.generated.maidbettergap.gap.GapLinkData;
import cn.blockforge.generated.maidbettergap.gap.GapTarget;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 「隙间终端 → 女仆饰品栏里的隙间」的桥接。
 * 只有装了车万女仆时才会被调用（调用方先查 ModList），类加载是惰性的，没装前置不会炸。
 *
 * 【重要·别再踩的坑】车万女仆的 EntityMaid 在本模组里只是编译桩（构建时不打进 jar）。
 * 编译期用的是官方映射，发布前要重映射成游戏运行时的名字（SRG），而这一步只认原版类：
 * 直接写 maid.getName() 会在 jar 里留下 "EntityMaid.getName()" 这种引用，
 * 运行期女仆类里没有这个名字（原版方法运行期叫 m_7755_），一调用就 NoSuchMethodError 崩服。
 * 规则：EntityMaid 自己声明的成员（getMaidBauble / getAvailableInv / guiOpening）可以直接调；
 * 凡是从原版继承来的成员（getName / isRemoved / level / getUUID …），
 * 一律先把引用转成 Entity 类型再调，见下面的 displayName() / alive()。
 */
public final class MaidLinkService {

    private MaidLinkService() {}

    /** 右键目标是不是一只女仆。 */
    public static boolean isMaid(Entity entity) {
        return entity instanceof EntityMaid;
    }

    /**
     * 隙间终端右键女仆：链接到这只女仆并直接打开设置界面。
     * 两侧都要拦下事件，防止车万女仆自己的“右键女仆开女仆背包”抢走这次点击。
     */
    public static void handleInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (held.getItem() != ModItems.GAP_TERMINAL.get()) {
            return;
        }
        event.setCanceled(true);
        if (event.getSide() != LogicalSide.SERVER) {
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        ServerPlayer sp = (ServerPlayer) event.getEntity();
        if (sp.isSpectator()) {
            return;
        }
        link(held, maid);
        sp.displayClientMessage(Component.translatable("msg.maidgap.linked", displayName(maid))
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        sp.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.35f, 1.7f);
        GapTarget t = resolve(sp, held);
        if (!t.stack.isEmpty()) {
            ContainerService.openTerminal(sp);
        } else {
            sp.displayClientMessage(Component.translatable("msg.maidgap.maid_nogap")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** 把终端链接到这只女仆，并记下她的展示名。 */
    public static void link(ItemStack terminal, Entity maid) {
        GapLinkData.link(terminal, maid.getUUID(), displayName(maid));
    }

    /**
     * 收集玩家附近女仆饰品栏里的隙间物品（自动补货找绑定容器用）。
     * 女仆戴着隙间时，隙间不在玩家背包里，光扫玩家背包是找不到绑定的。
     */
    public static void addNearbyMaidGaps(ServerPlayer user, List<ItemStack> out) {
        try {
            final double radius = 96.0;
            AABB box = user.getBoundingBox().inflate(radius);
            for (EntityMaid maid : user.level().getEntitiesOfClass(EntityMaid.class, box)) {
                Entity e = maid; // 原版继承成员走 Entity 引用调用（重混淆安全），见类注释
                if (!alive(e) || e.distanceToSqr(user) > radius * radius) {
                    continue;
                }
                BaubleItemHandler baubles = maid.getMaidBauble();
                for (int i = 0; i < baubles.getSlots(); i++) {
                    ItemStack st = baubles.getStackInSlot(i);
                    if (ContainerService.isGapItem(st)) {
                        out.add(st);
                    }
                }
            }
        } catch (LinkageError err) {
            // 车万女仆版本对不上时忽略女仆侧来源，玩家自己携带的隙间照常可用
        }
    }

    /**
     * 解析终端当前要操作的隙间：链接的女仆 → 她饰品栏里第一件隙间（活引用）。
     * 女仆不在已加载区块 / 没链接 / 饰品栏没隙间，都会带上对应标记返回。
     */
    public static GapTarget resolve(ServerPlayer user, ItemStack terminal) {
        String savedName = GapLinkData.savedName(terminal);
        UUID uuid = GapLinkData.linkUuid(terminal);
        if (uuid == null) {
            return GapTarget.emptyTerminal(savedName);
        }
        EntityMaid maid = findMaid(user.server, uuid);
        if (maid == null) {
            return GapTarget.emptyTerminal(savedName);
        }
        String name = displayName(maid);
        BaubleItemHandler baubles = maid.getMaidBauble();
        for (int i = 0; i < baubles.getSlots(); i++) {
            ItemStack st = baubles.getStackInSlot(i);
            if (ContainerService.isGapItem(st)) {
                return GapTarget.ofMaid(st, maid, i, name);
            }
        }
        return GapTarget.maidWithoutGap(maid, name);
    }

    /** 改完 NBT 后推一次饰品槽，触发车万女仆自己的同步（饰品栏 GUI 立即刷新）。 */
    public static void syncBauble(GapTarget t) {
        if (t.maid instanceof EntityMaid maid && t.baubleSlot >= 0) {
            BaubleItemHandler baubles = maid.getMaidBauble();
            if (t.baubleSlot >= baubles.getSlots()) {
                return;
            }
            ItemStack inSlot = baubles.getStackInSlot(t.baubleSlot);
            // 槽位里的东西已经不在了（被别人拿走/换成别的物品）就别写回去，否则等于凭空复制一个隙间
            if (inSlot.isEmpty() || inSlot.getItem() != t.stack.getItem()) {
                return;
            }
            baubles.setStackInSlot(t.baubleSlot, t.stack);
        }
    }

    /**
     * 槽位配置界面要展示的女仆背包内容，固定 38 格：
     * 0-35 = 背包（超出她实际背包大小的格留空），36 = 主手，37 = 副手。
     * 索引语义与原版 SlotConfigData 数组一致（见 EnhancedGapBauble / GapItemData）。
     */
    public static List<ItemStack> maidInvDisplay(Object maidObj) {
        List<ItemStack> out = new ArrayList<>(GapItemData.ORIGINAL_SLOT_NUM);
        for (int i = 0; i < GapItemData.ORIGINAL_SLOT_NUM; i++) {
            out.add(ItemStack.EMPTY);
        }
        if (!(maidObj instanceof EntityMaid maid)) {
            return out;
        }
        try {
            CombinedInvWrapper inv = maid.getAvailableInv(false);
            int size = inv.getSlots();
            if (size < 2) {
                return out;
            }
            int pack = size - 2;
            for (int i = 0; i < pack && i < 36; i++) {
                out.set(i, inv.getStackInSlot(i).copy());
            }
            out.set(36, inv.getStackInSlot(size - 2).copy());
            out.set(37, inv.getStackInSlot(size - 1).copy());
        } catch (LinkageError err) {
            // 版本不匹配时退回全空显示，锁定本身仍然可编辑
        }
        return out;
    }

    @Nullable
    private static EntityMaid findMaid(MinecraftServer server, UUID uuid) {
        if (server == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            // isRemoved() 走 Entity 引用调用（重混淆安全），见类注释
            if (e instanceof EntityMaid && alive(e)) {
                return (EntityMaid) e;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 原版继承成员的访问入口：参数刻意写成 Entity，编译器才会把调用记在原版类上，
    // 构建期的重混淆才认得它。直接对 EntityMaid 调原版方法 = 运行期崩溃。
    // ------------------------------------------------------------------

    /** 女仆的展示名（有命名卡就用命名卡，否则用实体类型名）。 */
    private static String displayName(Entity maid) {
        return maid.getName().getString();
    }

    /** 女仆是否还在世界里（区块没卸载 / 还没被移除）。 */
    private static boolean alive(Entity entity) {
        return !entity.isRemoved();
    }
}
