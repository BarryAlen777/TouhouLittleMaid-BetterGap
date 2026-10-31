package cn.blockforge.generated.maidbettergap.compat.tlm;

import cn.blockforge.generated.maidbettergap.MaidGapConfig;
import cn.blockforge.generated.maidbettergap.gap.Binding;
import cn.blockforge.generated.maidbettergap.gap.ContainerService;
import cn.blockforge.generated.maidbettergap.gap.GapItemData;
import cn.blockforge.generated.maidbettergap.compat.soph.SophBackpackBridge;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * 增强版隙间饰品逻辑（替代原版 WirelessIOBauble，兼容原版 NBT 数据）。
 *
 * 女仆佩戴隙间后，每隔 maid_io_interval_ticks（默认 100 tick，与原版一致）：
 * - 按隙间物品上的绑定列表逐个处理容器（原版增强为最多 4~8 个容器）
 * - 方向：箱子->女仆 或 女仆->箱子（原版 ItemIOMode 键）
 * - 过滤：白名单（空名单不搬运，与原版一致）/ 黑名单（原版 ItemFilterMode + ItemFilterList 键），
 *   可开「类别匹配」：名单条目按物品标签覆盖整类物品（MGFilterTagMatch 键）
 * - 距离：默认受配置 max_bind_distance 限制；开 infinite_range 后可跨维度直连
 * - 尊重终端 / 原版 GUI 设置的女仆槽位锁定（SlotConfigData，双向都生效）
 * - 每轮搬运受终端「拿取数量」限制：每个来源槽最多搬这么多（默认 64 = 整组）
 * - 搬运成功时按配置播放原版隙间风格的粒子与音效
 */
public final class EnhancedGapBauble implements IMaidBauble {

    public static final EnhancedGapBauble INSTANCE = new EnhancedGapBauble();

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** 车万女仆版本对不上时自动停用，避免每 tick 抛错把游戏拖崩。 */
    private static boolean disabled = false;

    /** 普通运行期报错只完整记一次日志，避免每 5 秒刷屏。 */
    private static boolean errorLogged = false;

    private EnhancedGapBauble() {}

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        if (disabled) {
            return;
        }
        try {
            doTick(maid, baubleItem);
        } catch (LinkageError err) {
            disabled = true;
            LOGGER.error("[maid_better_gap] 与车万女仆的接口不匹配，增强隙间的自动搬运已停用。"
                    + " 请把本模组与车万女仆更新到匹配的版本。", err);
        } catch (Exception ex) {
            // 这里抛出去的异常会一路冒到实体 tick，轻则刷屏重则崩服：就地咽下并记一次日志
            if (!errorLogged) {
                errorLogged = true;
                LOGGER.error("[maid_better_gap] 增强隙间搬运时出错，本次搬运已跳过（后续仍会继续尝试）。", ex);
            }
        }
    }

    private static void doTick(EntityMaid maid, ItemStack baubleItem) {
        if (baubleItem.isEmpty()) {
            return;
        }
        // MC 继承成员统一经 Entity 访问，保证重混淆安全
        Entity asEntity = maid;
        Level level = asEntity.level();
        if (level.isClientSide()) {
            return;
        }
        // 升级迁移兜底（R0）：净化必须放在 guiOpening / 搬运间隔检查之前。
        // 玩家打开女仆界面时，车万女仆会把饰品栏物品同步给客户端；如果等到 100 tick 的那一轮
        // 才洗，旧存档里的脏名单就先发出去了。净化本身干净时几乎零成本（不写 NBT）。
        GapItemData.sanitizeFilters(baubleItem);
        if (maid.guiOpening) {
            return;
        }
        int interval = Math.max(20, MaidGapConfig.MAID_IO_INTERVAL.get());
        if (asEntity.tickCount % interval != 0) {
            return;
        }

        String maidDim = level.dimension().location().toString();
        List<Binding> bindings = GapItemData.bindings(baubleItem, maidDim);
        if (bindings.isEmpty()) {
            return;
        }

        boolean maidToChest = GapItemData.isMaidToChest(baubleItem);
        CombinedInvWrapper maidInv = maid.getAvailableInv(false);
        boolean[] slotLock = GapItemData.slotLocks(baubleItem, maidInv.getSlots());
        // 终端里设置的「拿取数量」同样限制女仆每轮搬运：每个来源槽最多搬这么多
        int cap = Math.max(1, GapItemData.pickupAmount(baubleItem));

        boolean moved = false;
        // 女仆身上「精妙背包」的内部物品栏（没装女仆精妙/精妙背包时是空列表）。
        // 注意这是背包*内容物*，不在 maidInv 里，所以之前只会搬女仆自带背包和物品栏。
        List<IItemHandler> backpacks = MaidGapConfig.SOPH_BACKPACKS.get()
                ? SophBackpackBridge.wornBackpacks(asEntity)
                : java.util.Collections.emptyList();
        for (Binding b : bindings) {
            if (b.isFluid()) {
                continue; // 液体只走玩家终端取桶，女仆不搬流体
            }
            IItemHandler chest = resolveChest(asEntity, b);
            if (chest == null) {
                continue;
            }
            // 每个容器用自己的过滤名单：这个容器单独设过名单就只认它自己的，
            // 没设过就沿用隙间上的共用名单（老存档行为不变）。
            java.util.function.Predicate<ItemStack> allowed = GapItemData.filterPredicate(baubleItem, b);
            if (maidToChest) {
                moved |= maidToChest(asEntity, maidInv, chest, allowed, slotLock, cap);
                moved |= backpacksToChest(asEntity, backpacks, chest, allowed, cap);
            } else {
                moved |= chestToMaid(asEntity, chest, maidInv, allowed, slotLock, cap);
                moved |= chestToBackpacks(asEntity, chest, backpacks, allowed, cap);
            }
        }
        if (moved) {
            feedback(asEntity);
        }
    }

    // ------------------------------------------------------------------
    // 容器解析：维度 + 距离 + 模组白名单 + 能力
    // ------------------------------------------------------------------

    private static IItemHandler resolveChest(Entity maidEntity, Binding b) {
        Level level = maidEntity.level();
        boolean sameDim = level.dimension().location().toString().equals(b.dimension());
        boolean infinite = MaidGapConfig.INFINITE_RANGE.get();
        ServerLevel target;
        if (sameDim) {
            target = (ServerLevel) level;
        } else if (infinite) {
            ResourceLocation dimId = ResourceLocation.tryParse(b.dimension());
            if (dimId == null) {
                return null;
            }
            MinecraftServer server = maidEntity.getServer();
            target = server == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        } else {
            return null; // 非无限模式不跨维度
        }
        if (target == null) {
            return null;
        }
        BlockPos pos = b.pos();
        if (!target.isLoaded(pos)) {
            return null;
        }
        if (!infinite) {
            int max = MaidGapConfig.MAX_BIND_DISTANCE.get();
            if (maidEntity.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > (double) max * max) {
                return null;
            }
        }
        BlockEntity be = target.getBlockEntity(pos);
        if (be == null) {
            return null;
        }
        ResourceLocation blockKey = ForgeRegistries.BLOCKS.getKey(be.getBlockState().getBlock());
        if (blockKey == null || !MaidGapConfig.modAllowed(blockKey.getNamespace())) {
            return null;
        }
        return ContainerService.findItems(be);
    }

    // ------------------------------------------------------------------
    // 搬运（过滤语义与原版一致：allowMove = 名单里 ? !黑名单 : 黑名单）
    // ------------------------------------------------------------------

    private static boolean maidToChest(Entity maidEntity, IItemHandler maid, IItemHandler chest,
                                       java.util.function.Predicate<ItemStack> allowed, boolean[] slotLock, int cap) {
        boolean moved = false;
        for (int i = 0; i < maid.getSlots(); i++) {
            if (slotLock != null && i < slotLock.length && slotLock[i]) {
                continue;
            }
            ItemStack stack = maid.getStackInSlot(i);
            if (stack.isEmpty() || !allowed.test(stack)) {
                continue;
            }
            int want = Math.min(cap, stack.getCount());
            if (want <= 0) {
                continue;
            }
            // 先从女仆背包取出、再放进箱子：顺序反过来的话，万一取出失败物品就被复制了
            ItemStack got = maid.extractItem(i, want, false);
            if (got.isEmpty()) {
                continue;
            }
            ItemStack rest = ItemHandlerHelper.insertItemStacked(chest, got, false);
            if (rest.getCount() < got.getCount()) {
                moved = true;
            }
            returnTo(maidEntity, rest, maid, i); // 塞不进箱子的放回去，绝不让物品消失
        }
        return moved;
    }

    private static boolean chestToMaid(Entity maidEntity, IItemHandler chest, IItemHandler maid,
                                       java.util.function.Predicate<ItemStack> allowed, boolean[] slotLock, int cap) {
        boolean moved = false;
        for (int i = 0; i < chest.getSlots(); i++) {
            ItemStack stack = chest.getStackInSlot(i);
            if (stack.isEmpty() || !allowed.test(stack)) {
                continue;
            }
            int want = Math.min(cap, stack.getCount());
            if (want <= 0) {
                continue;
            }
            ItemStack got = chest.extractItem(i, want, false);
            if (got.isEmpty()) {
                continue;
            }
            ItemStack rest = insertSkippingLocked(maid, got, slotLock);
            if (rest.getCount() < got.getCount()) {
                moved = true;
            }
            returnTo(maidEntity, rest, chest, i); // 女仆背包塞不下的放回箱子
        }
        return moved;
    }

    /**
     * 女仆身上「精妙背包」里的东西 -> 容器。
     * 背包内容物不在 maidInv 里，所以单独走一遍；白名单、类别匹配、拿取数量一视同仁。
     * 槽位锁定只针对女仆自己的 38 格，不作用于背包内部，这里不传 slotLock。
     */
    private static boolean backpacksToChest(Entity maidEntity, List<IItemHandler> backpacks, IItemHandler chest,
                                            java.util.function.Predicate<ItemStack> allowed, int cap) {
        boolean moved = false;
        for (IItemHandler pack : backpacks) {
            for (int i = 0; i < pack.getSlots(); i++) {
                ItemStack stack = pack.getStackInSlot(i);
                if (stack.isEmpty() || !allowed.test(stack)) {
                    continue;
                }
                int want = Math.min(cap, stack.getCount());
                if (want <= 0) {
                    continue;
                }
                // 与 maidToChest 同样的顺序：先从背包取出，再放进容器
                ItemStack got = pack.extractItem(i, want, false);
                if (got.isEmpty()) {
                    continue;
                }
                ItemStack rest = ItemHandlerHelper.insertItemStacked(chest, got, false);
                if (rest.getCount() < got.getCount()) {
                    moved = true;
                }
                returnTo(maidEntity, rest, pack, i); // 塞不进箱子的放回背包，绝不丢
            }
        }
        return moved;
    }

    /** 容器 -> 女仆身上「精妙背包」：先往背包里塞，塞不下的再退回去。 */
    private static boolean chestToBackpacks(Entity maidEntity, IItemHandler chest, List<IItemHandler> backpacks,
                                            java.util.function.Predicate<ItemStack> allowed, int cap) {
        if (backpacks.isEmpty()) {
            return false;
        }
        boolean moved = false;
        for (int i = 0; i < chest.getSlots(); i++) {
            ItemStack stack = chest.getStackInSlot(i);
            if (stack.isEmpty() || !allowed.test(stack)) {
                continue;
            }
            int want = Math.min(cap, stack.getCount());
            if (want <= 0) {
                continue;
            }
            ItemStack got = chest.extractItem(i, want, false);
            if (got.isEmpty()) {
                continue;
            }
            ItemStack rest = insertIntoBackpacks(backpacks, got);
            if (rest.getCount() < got.getCount()) {
                moved = true;
            }
            returnTo(maidEntity, rest, chest, i); // 背包塞不下的放回容器
        }
        return moved;
    }

    /** 依次往各个背包里塞，返回没塞进去的剩余部分。 */
    private static ItemStack insertIntoBackpacks(List<IItemHandler> backpacks, ItemStack stack) {
        ItemStack remaining = stack;
        for (IItemHandler pack : backpacks) {
            remaining = ItemHandlerHelper.insertItemStacked(pack, remaining, false);
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return remaining;
    }

    /**
     * 把没搬走的部分放回原来的容器。
     * 万一原槽位已经不收了（槽位上限、被别的模组锁住…），再找别的空位，
     * 最后兜底掉在女仆脚下——宁可地上多一堆，也绝不凭空吞掉玩家的物品。
     */
    private static void returnTo(Entity maidEntity, ItemStack rest, IItemHandler back, int slot) {
        if (rest.isEmpty()) {
            return;
        }
        ItemStack left = back.insertItem(slot, rest, false);
        if (left.isEmpty()) {
            return;
        }
        left = ItemHandlerHelper.insertItemStacked(back, left, false);
        if (left.isEmpty()) {
            return;
        }
        Level level = maidEntity.level();
        if (!level.isClientSide()) {
            net.minecraft.world.level.block.Block.popResource(level, maidEntity.blockPosition(), left);
        }
    }

    /** 仿原版：先找可堆叠的槽、再找空槽，跳过被原版 GUI 锁定的女仆槽。 */
    private static ItemStack insertSkippingLocked(IItemHandler inv, ItemStack stack, boolean[] slotLock) {
        if (stack.isEmpty()) {
            return stack;
        }
        int slots = inv.getSlots();
        if (stack.isStackable()) {
            for (int i = 0; i < slots; i++) {
                if (locked(slotLock, i)) {
                    continue;
                }
                ItemStack slot = inv.getStackInSlot(i);
                if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, stack)) {
                    stack = inv.insertItem(i, stack, false);
                    if (stack.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                }
            }
        }
        for (int i = 0; i < slots; i++) {
            if (locked(slotLock, i)) {
                continue;
            }
            if (inv.getStackInSlot(i).isEmpty()) {
                stack = inv.insertItem(i, stack, false);
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return stack;
    }

    private static boolean locked(boolean[] slotLock, int i) {
        return slotLock != null && i < slotLock.length && slotLock[i];
    }

    // ------------------------------------------------------------------
    // 反馈：原版隙间风格的传送粒子 + 音
    // ------------------------------------------------------------------

    private static void feedback(Entity maidEntity) {
        Level level = maidEntity.level();
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        if (MaidGapConfig.PARTICLE_FEEDBACK.get()) {
            sl.sendParticles(ParticleTypes.PORTAL,
                    maidEntity.getX(), maidEntity.getY() + 1.0, maidEntity.getZ(),
                    8, 0.3, 0.5, 0.3, 0.1);
        }
        if (MaidGapConfig.SOUND_FEEDBACK.get()) {
            sl.playSound(null, maidEntity, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.25f, 1.6f);
        }
    }
}
