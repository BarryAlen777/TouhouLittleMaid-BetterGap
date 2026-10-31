package cn.blockforge.generated.maidbettergap.compat.tlm;

import cn.blockforge.generated.maidbettergap.GeneratedMod;
import cn.blockforge.generated.maidbettergap.MaidGapConfig;
import cn.blockforge.generated.maidbettergap.ModItems;
import cn.blockforge.generated.maidbettergap.gap.ContainerService;
import cn.blockforge.generated.maidbettergap.gap.GapItemData;
import cn.blockforge.generated.maidbettergap.gap.GapLinkData;
import cn.blockforge.generated.maidbettergap.gap.GapTarget;
import cn.blockforge.generated.maidbettergap.gap.TerminalSnapshot;
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

    /** 归属校验退化只提醒一次，别把日志刷爆。 */
    private static boolean NOT_OWNABLE_WARNED;

    private MaidLinkService() {}

    /** 右键目标是不是一只女仆。 */
    public static boolean isMaid(Entity entity) {
        return entity instanceof EntityMaid;
    }

    /**
     * 隙间终端右键女仆：链接到这只女仆并直接打开设置界面。
     * 两侧都要拦下事件，防止车万女仆自己的“右键女仆开女仆界面”抢走这次点击。
     */
    public static void handleInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (held.getItem() != ModItems.GAP_TERMINAL.get()) {
            return;
        }
        // 只能链接自己的女仆：别人家的女仆既不能连、也不能改她身上的隙间。
        // 归属判断放在「取消事件」之前，而且客户端也要做（主人 UUID 是同步到客户端的）：
        // 否则客户端把这次点击消费掉了、服务端却把事件还给了车万女仆，
        // 会出现“服务端开了女仆界面、客户端没开”的两侧不一致。
        if (!ownedBy(maid, event.getEntity().getUUID())) {
            // 不取消、也不改 cancellationResult：车万女仆自己的“右键开女仆界面”两侧都照常处理
            if (event.getSide() == LogicalSide.SERVER) {
                event.getEntity().displayClientMessage(Component.translatable("msg.maidgap.not_owner")
                        .withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        event.setCanceled(true);
        if (event.getSide() != LogicalSide.SERVER) {
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.isSpectator()) {
            return;
        }
        boolean added = link(held, maid);
        // NBT 改了要推一次同步，否则客户端手里的终端 tooltip 一直停在“未链接”
        ContainerService.syncHeld(sp);
        if (added) {
            sp.displayClientMessage(Component.translatable("msg.maidgap.linked", displayName(maid))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        } else {
            // 列表满了：这只新女仆没加进去，但界面照开，玩家可以在左侧列表里先删一只
            sp.displayClientMessage(Component.translatable("msg.maidgap.maid_full",
                    String.valueOf(MaidGapConfig.TERMINAL_MAID_LIMIT.get())).withStyle(ChatFormatting.RED), true);
        }
        sp.playSound(SoundEvents.ENDERMAN_TELEPORT, 0.35f, 1.7f);
        GapTarget t = resolve(sp, held);
        if (!t.stack.isEmpty()) {
            ContainerService.openTerminal(sp);
        } else {
            sp.displayClientMessage(Component.translatable("msg.maidgap.maid_nogap")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    /**
     * 把女仆加进终端的女仆列表，并切换成“当前管理她”。
     *
     * @return false = 列表已满（上限见配置 terminal_maid_limit）
     */
    public static boolean link(ItemStack terminal, Entity maid) {
        return GapLinkData.addMaid(terminal, maid.getUUID(), displayName(maid),
                MaidGapConfig.TERMINAL_MAID_LIMIT.get());
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
                // 只用自己女仆身上的隙间：别人的女仆不归你管，绝不能借她的绑定翻别人家箱子
                if (!ownedBy(maid, user.getUUID())) {
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
        UUID uuid = GapLinkData.activeUuid(terminal);
        if (uuid == null) {
            return GapTarget.emptyTerminal(savedName);
        }
        EntityMaid maid = findMaid(user.server, uuid);
        if (maid == null) {
            return GapTarget.emptyTerminal(savedName);
        }
        // 终端可能已经被转手给别人（卖掉 / 掉落被捡）：链接只在“女仆仍归你”时有效，
        // 否则拿到终端的人就能借这个链接翻别人家的箱子
        if (!ownedBy(maid, user.getUUID())) {
            return GapTarget.ofOtherPlayer(savedName);
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
     * 终端左侧女仆列表的状态：每只在不在线、饰品栏里有没有隙间。
     * 女仆不在已加载区块 / 已经不是你的女仆 = 离线（界面上照常列出，也能切换和删除）。
     */
    public static List<TerminalSnapshot.MaidEntry> maidStates(ServerPlayer user, ItemStack terminal) {
        List<TerminalSnapshot.MaidEntry> out = new ArrayList<>();
        for (GapLinkData.MaidRef ref : GapLinkData.maids(terminal)) {
            TerminalSnapshot.MaidEntry e = new TerminalSnapshot.MaidEntry(ref.uuid().toString());
            e.name = ref.name() == null ? "" : ref.name();
            // 每只女仆单独兜底：getMaidBauble()/displayName 之类可能抛 RuntimeException，
            // 只有一只出问题就让她显示“离线”，绝不能把整份列表（乃至整个快照发送）带崩。
            try {
                EntityMaid maid = findMaid(user.server, ref.uuid());
                if (maid != null) {
                    Entity asEntity = maid;
                    if (alive(asEntity)) {
                        if (ownedBy(asEntity, user.getUUID())) {
                            e.online = true;
                            String live = displayName(asEntity);
                            if (!live.isEmpty()) {
                                e.name = live;
                            }
                            e.hasGap = hasGap(maid);
                        } else {
                            // 活着但不是这位玩家的女仆：终端可能转手过，单独标“非本人”
                            e.notOwner = true;
                        }
                    }
                }
            } catch (LinkageError | RuntimeException err) {
                // 查询这只女仆失败：留 e.online=false，按“离线”显示，继续处理下一只
            }
            out.add(e);
        }
        return out;
    }

    /** 这只女仆饰品栏里有没有隙间。 */
    private static boolean hasGap(EntityMaid maid) {
        BaubleItemHandler baubles = maid.getMaidBauble();
        for (int i = 0; i < baubles.getSlots(); i++) {
            if (ContainerService.isGapItem(baubles.getStackInSlot(i))) {
                return true;
            }
        }
        return false;
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
        } catch (LinkageError | RuntimeException err) {
            // 版本不匹配 / 女仆数据未就绪时退回全空显示，锁定本身仍然可编辑
        }
        return out;
    }

    /**
     * 这只女仆是不是这位玩家的。
     * 归属信息走原版接口 OwnableEntity 读，既避开了重混淆的坑，也不用引用车万女仆自己的方法。
     * 读不到归属（没主人的女仆 / 旧版本实体）时保守放行，免得好好的功能突然不能用。
     */
    public static boolean ownedBy(Entity maid, UUID player) {
        if (player == null) {
            return true;
        }
        if (!(maid instanceof net.minecraft.world.entity.OwnableEntity ownable)) {
            // 车万女仆将来若不再继承 TamableAnimal，这里会静默退化成“一律放行”。
            // 至少往日志里喊一声，别让越权修复无声无息地失效。
            if (!NOT_OWNABLE_WARNED) {
                NOT_OWNABLE_WARNED = true;
                GeneratedMod.LOGGER.warn("[maid_better_gap] 女仆不是 OwnableEntity，归属校验无法生效（车万女仆版本变了？）");
            }
            return true;
        }
        UUID owner = ownable.getOwnerUUID();
        return owner == null || owner.equals(player);
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
