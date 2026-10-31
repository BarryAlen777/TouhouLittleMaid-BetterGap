package cn.blockforge.generated.maidbettergap.event;

import cn.blockforge.generated.maidbettergap.MaidGapConfig;

import cn.blockforge.generated.maidbettergap.ModItems;
import cn.blockforge.generated.maidbettergap.gap.ContainerService;
import cn.blockforge.generated.maidbettergap.gap.GapItemData;
import cn.blockforge.generated.maidbettergap.gap.GapStore;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/**
 * 隙间增强交互：
 * - 手持隙间物品（终端或白名单里的原模组物品）潜行+右键容器方块 = 绑定/解绑
 * - 普通右键不拦截，原模组物品保留原模组的右键逻辑（非侵入式增强）
 * - 自动补货按配置间隔扫描
 * - 死亡重生保留绑定数据
 */
@Mod.EventBusSubscriber(modid = "maid_better_gap")
public final class GapInteractions {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 车万女仆桥接报错只提示一次，避免刷屏。 */
    private static boolean bridgeWarned = false;

    /** 自动补货的运行期报错也只记一次。 */
    private static boolean refillWarned = false;

    /** 右键女仆联动时的运行期报错也只记一次。 */
    private static boolean interactWarned = false;

    private static int refillClock = 0;

    private GapInteractions() {}

    /** 潜行+右键方块：绑定/解绑。只在服务端裁决，普通右键完全放行。 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide() != LogicalSide.SERVER) {
            return;
        }
        Player player = event.getEntity();
        if (player.isSpectator() || !player.isShiftKeyDown()) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (held.getItem() != ModItems.MAID_GAP_TERMINAL.get()
                && held.getItem() != ModItems.GAP_TERMINAL.get()
                && !ContainerService.isGapItem(held)) {
            return;
        }
        // 主副手都拿着隙间时只处理主手那一次，避免同一次点击绑定+解绑互相抵消
        if (event.getHand() == net.minecraft.world.InteractionHand.OFF_HAND
                && (ContainerService.isGapItem(player.getMainHandItem())
                    || player.getMainHandItem().getItem() == ModItems.GAP_TERMINAL.get())) {
            return;
        }
        // 先问隙间要不要这次点击：没目标（没链接女仆 / 不是容器）就不拦，方块照常正常打开
        if (!ContainerService.tryBindAt((ServerPlayer) player, event.getPos())) {
            return;
        }
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }

    /**
     * 右键女仆：隙间终端链接到她并打开设置界面（只有装了车万女仆才走到桥接类里）。
     * HIGHEST：要抢在车万女仆自己的“右键女仆开背包”之前拦下来，否则两个界面会互相顶掉。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getItemStack().getItem() != ModItems.GAP_TERMINAL.get()) {
            return;
        }
        if (!ContainerService.tlmLoaded()) {
            return;
        }
        try {
            cn.blockforge.generated.maidbettergap.compat.tlm.MaidLinkService.handleInteract(event);
        } catch (LinkageError err) {
            // 女仆模组版本对不上（方法/字段名变了）时：只提示，不把整个存档拖崩
            if (!bridgeWarned) {
                bridgeWarned = true;
                LOGGER.error("[maid_better_gap] 与车万女仆的接口不匹配，隙间终端的右键功能已停用。"
                        + " 请把本模组与车万女仆更新到匹配的版本。", err);
            }
            if (event.getEntity() instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("msg.maidgap.bridge_broken")
                        .withStyle(ChatFormatting.RED), true);
            }
        } catch (RuntimeException ex) {
            // 联动时的任何运行期异常都不该把玩家/服务器踹崩，只记一次日志
            if (!interactWarned) {
                interactWarned = true;
                LOGGER.error("[maid_better_gap] 右键女仆链接隙间终端时出错，本次已跳过。", ex);
            }
        }
    }

    /** 自动补货：按配置间隔扫描开了补货的玩家。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!MaidGapConfig.AUTO_REFILL_ENABLED.get()) {
            return;
        }
        refillClock++;
        if (refillClock < Math.max(10, MaidGapConfig.AUTO_REFILL_INTERVAL.get())) {
            return;
        }
        refillClock = 0;
        for (ServerPlayer sp : event.getServer().getPlayerList().getPlayers()) {
            if (!GapStore.autoRefillOn(sp)) {
                continue;
            }
            try {
                ContainerService.autoRefill(sp);
            } catch (LinkageError err) {
                // 前置版本对不上：停掉补货扫描，别每个循环都炸一次服务器线程
                if (!bridgeWarned) {
                    bridgeWarned = true;
                    LOGGER.error("[maid_better_gap] 自动补货因车万女仆接口不匹配已停用。", err);
                }
                return;
            } catch (RuntimeException ex) {
                // 服务器 tick 线程里抛异常会崩整个存档，这里咽下并只记一次
                if (!refillWarned) {
                    refillWarned = true;
                    LOGGER.error("[maid_better_gap] 自动补货时出错，本次已跳过（后续仍会重试）。", ex);
                }
            }
        }
    }

    /** 死亡重生：把隙间数据带到新玩家实体。 */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        GapStore.copyOnClone(event.getOriginal(), event.getEntity());
    }

    /**
     * 玩家登录：扫一遍背包里的隙间做升级净化（复审报告 R0）。
     * 本模组的隙间有自己的 inventoryTick 兜底，这里补上「配置白名单里的原版隙间」
     * ——那些物品的 tick 不归我们管，只能在登录时手动洗一次。
     *
     * 提醒：登录事件与“服务端发出的第一份背包内容包”谁先谁后没有保证，
     * 极端情况下背包里直接躺着一件 >2 MB 的旧隙间，进服瞬间仍可能被踢一次。
     * 稳妥做法是先把旧隙间放进箱子再取出来，或者进服后等一秒；更新说明里写了。
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && ContainerService.isGapItem(stack)) {
                GapItemData.sanitizeFilters(stack);
            }
        }
    }

    /**
     * 掉在地上的旧隙间：区块加载或玩家走近时，物品实体里的物品会随同步发给客户端。
     * 如果名单又脏又大，玩家还没伸手去捡就可能被踢下线，所以实体刚进世界时先洗一遍
     * （干净时零写入，成本可以忽略）。箱子里的旧隙间不在这个覆盖范围内——拿一次就干净。
     */
    @SubscribeEvent
    public static void onItemEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof ItemEntity drop) {
            ItemStack stack = drop.getItem();
            if (!stack.isEmpty() && ContainerService.isGapItem(stack)) {
                GapItemData.sanitizeFilters(stack);
            }
        }
    }

    /** 玩家退出：清掉内存里的“最近用过”记录。 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        GapStore.forgetRecent(event.getEntity());
    }

    // ------------------------------------------------------------------
    // 「最近用过的物品」：自动补货用它兜底“整组一次用光没被扫描到”的情况
    // 只记服务端，且只听真正消耗物品的事件（把东西存进箱子不会触发，不会被反向掏出来）
    // ------------------------------------------------------------------

    /** 吃东西 / 喝药水等“用完了”的物品。 */
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            GapStore.rememberUsed(sp, event.getItem());
        }
    }

    /** 右键用物品：投掷物、弓箭、点火、种地等（这些物品会少一个）。 */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getSide() != LogicalSide.SERVER) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer sp) {
            GapStore.rememberUsed(sp, event.getItemStack());
        }
    }

    /** 放置方块：方块从背包里消失了，也该补。记“真的放下去的那块”，副手放置也不会记错。 */
    @SubscribeEvent
    public static void onPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            // 优先用手上那一堆当样本（含 NBT）：直接 new ItemStack(方块) 会把潜影盒、带内容的箱子、
            // 带图案的旗帜都记成“白板物品”，之后补货就会拉出一个空潜影盒而不是刚放下的那个。
            ItemStack held = sp.getMainHandItem();
            ItemStack off = sp.getOffhandItem();
            ItemStack placed = held.getItem() == event.getPlacedBlock().getBlock().asItem() ? held.copy()
                    : off.getItem() == event.getPlacedBlock().getBlock().asItem() ? off.copy()
                    : new ItemStack(event.getPlacedBlock().getBlock().asItem());
            if (!placed.isEmpty()) {
                GapStore.rememberUsed(sp, placed);
            }
        }
    }
}
