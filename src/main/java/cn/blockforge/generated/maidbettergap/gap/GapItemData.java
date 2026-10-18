package cn.blockforge.generated.maidbettergap.gap;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 隙间绑定数据：存在「隙间物品自己的 NBT」里（和车万女仆原版设计一致）。
 * 这样女仆把隙间戴在饰品栏上就能带着绑定走，玩家手持同一件物品也能配置。
 *
 * 键名与原版 touhou_little_maid:wireless_io 完全兼容：
 *   BindingPos      —— 原版单容器绑定坐标 {X,Y,Z}（我们同步写第一条物品绑定，原版 GUI 照常显示）
 *   ItemIOMode      —— true = 女仆->箱子，false = 箱子->女仆
 *   ItemFilterMode  —— true = 黑名单，false = 白名单
 *   ItemFilterList  —— 9 格过滤名单（原版 ItemStackHandler 格式）
 *   SlotConfigData  —— 女仆槽位锁定（原版格式，38 字节；本模组终端里可视化编辑）
 * 多容器绑定用我们自己的键 MGBindings 扩展，不影响原版功能。
 */
public final class GapItemData {

    public static final int FILTER_SLOTS = 9;
    /** 原版女仆背包槽位总数（槽位锁定数组按它存）：0-35 背包，36 主手，37 副手。 */
    public static final int ORIGINAL_SLOT_NUM = 38;

    private static final String K_BINDING_POS = "BindingPos";
    private static final String K_IO_MODE = "ItemIOMode";
    private static final String K_FILTER_MODE = "ItemFilterMode";
    private static final String K_FILTER_LIST = "ItemFilterList";
    private static final String K_SLOT_CONFIG = "SlotConfigData";
    private static final String K_BINDINGS = "MGBindings";
    private static final String K_TAG_MATCH = "MGFilterTagMatch";
    private static final String K_PICKUP = "MGPickupAmount";

    private GapItemData() {}

    // ------------------------------------------------------------------
    // 容器绑定（多容器 + 原版单容器兼容）
    // ------------------------------------------------------------------

    public static List<Binding> bindings(ItemStack stack, String fallbackDim) {
        List<Binding> out = new ArrayList<>();
        if (stack.isEmpty()) {
            return out;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return out;
        }
        if (tag.contains(K_BINDINGS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(K_BINDINGS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                out.add(new Binding(e.getString("dim"),
                        new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z")), e.getByte("kind")));
            }
            return out;
        }
        // 兼容：只用过原版功能的物品（BindingPos 单容器）
        if (tag.contains(K_BINDING_POS, Tag.TAG_COMPOUND)) {
            CompoundTag p = tag.getCompound(K_BINDING_POS);
            String dim = fallbackDim == null ? "minecraft:overworld" : fallbackDim;
            out.add(new Binding(dim, new BlockPos(p.getInt("X"), p.getInt("Y"), p.getInt("Z")), Binding.KIND_ITEM));
        }
        return out;
    }

    public static void setBindings(ItemStack stack, List<Binding> bindings) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag list = new ListTag();
        for (Binding b : bindings) {
            CompoundTag e = new CompoundTag();
            e.putString("dim", b.dimension());
            e.putInt("x", b.pos().getX());
            e.putInt("y", b.pos().getY());
            e.putInt("z", b.pos().getZ());
            e.putByte("kind", b.kind());
            list.add(e);
        }
        tag.put(K_BINDINGS, list);
        // 同步原版 BindingPos = 第一条物品容器绑定，保证原版 GUI 显示一致
        Binding firstItem = null;
        for (Binding b : bindings) {
            if (!b.isFluid()) {
                firstItem = b;
                break;
            }
        }
        if (firstItem != null) {
            CompoundTag p = new CompoundTag();
            p.putInt("X", firstItem.pos().getX());
            p.putInt("Y", firstItem.pos().getY());
            p.putInt("Z", firstItem.pos().getZ());
            tag.put(K_BINDING_POS, p);
        } else {
            tag.remove(K_BINDING_POS);
        }
    }

    public enum BindResult { BOUND, UNBOUND, FULL }

    /** 同一坐标再次绑定 = 解绑（切换）。达到上限返回 FULL。 */
    public static BindResult bindOrToggle(ItemStack stack, Binding candidate, int maxCount, String fallbackDim) {
        List<Binding> list = bindings(stack, fallbackDim);
        boolean existed = list.removeIf(b -> b.key().equals(candidate.key()));
        if (existed) {
            setBindings(stack, list);
            return BindResult.UNBOUND;
        }
        if (list.size() >= maxCount) {
            return BindResult.FULL;
        }
        list.add(candidate);
        setBindings(stack, list);
        return BindResult.BOUND;
    }

    // ------------------------------------------------------------------
    // 传输方向 / 过滤模式（原版键，原版 GUI 通用）
    // ------------------------------------------------------------------

    public static boolean isMaidToChest(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(K_IO_MODE);
    }

    public static void toggleMaidToChest(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(K_IO_MODE, !isMaidToChest(stack));
    }

    public static boolean isBlacklist(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(K_FILTER_MODE);
    }

    public static void toggleBlacklist(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(K_FILTER_MODE, !isBlacklist(stack));
    }

    /** 类别匹配：名单里的物品按“同类标签”匹配（一条鳕鱼覆盖所有鱼）。 */
    public static boolean isTagMatch(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(K_TAG_MATCH);
    }

    public static void toggleTagMatch(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(K_TAG_MATCH, !isTagMatch(stack));
    }

    // ------------------------------------------------------------------
    // 物品白名单：9 格过滤名单（原版 ItemFilterList 同格式）
    // ------------------------------------------------------------------

    private static ItemStackHandler filterHandler(ItemStack stack) {
        ItemStackHandler h = new ItemStackHandler(FILTER_SLOTS) {
            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(K_FILTER_LIST, Tag.TAG_COMPOUND)) {
            h.deserializeNBT(tag.getCompound(K_FILTER_LIST));
        }
        return h;
    }

    public static List<ItemStack> filters(ItemStack stack) {
        ItemStackHandler h = filterHandler(stack);
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < h.getSlots(); i++) {
            out.add(h.getStackInSlot(i).copy());
        }
        return out;
    }

    /**
     * 物品是否命中过滤名单：
     * 先按物品本身精确匹配（与原版一致）；开了类别匹配时，再按共享标签匹配。
     */
    public static boolean matchesFilter(ItemStack stack, ItemStack target) {
        if (target.isEmpty()) {
            return false;
        }
        boolean tagMatch = isTagMatch(stack);
        ItemStackHandler h = filterHandler(stack);
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack entry = h.getStackInSlot(i);
            if (entry.isEmpty()) {
                continue;
            }
            if (ItemStack.isSameItem(entry, target)) {
                return true;
            }
            if (tagMatch && TagMatcher.sharesCategory(entry, target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 批量过滤用的预计算断言（女仆每个搬运周期构建一次，避免逐件物品重复解析名单）。
     * 语义与原版一致：白名单 = 命中才搬；黑名单 = 命中就不搬。
     */
    public static java.util.function.Predicate<ItemStack> filterPredicate(ItemStack gap) {
        boolean blacklist = isBlacklist(gap);
        boolean tagMatch = isTagMatch(gap);
        List<Item> exact = new ArrayList<>();
        List<Set<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>>> tagSets = new ArrayList<>();
        ItemStackHandler h = filterHandler(gap);
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack entry = h.getStackInSlot(i);
            if (entry.isEmpty()) {
                continue;
            }
            exact.add(entry.getItem());
            if (tagMatch) {
                Set<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>> set =
                        TagMatcher.tagSet(entry);
                if (!set.isEmpty() && !tagSets.contains(set)) {
                    tagSets.add(set);
                }
            }
        }
        return stack -> {
            if (stack.isEmpty()) {
                return false;
            }
            boolean inList = exact.contains(stack.getItem());
            if (!inList && !tagSets.isEmpty()) {
                Set<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>> targetTags =
                        new HashSet<>();
                stack.getTags().forEach(targetTags::add);
                for (Set<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>> entryTags : tagSets) {
                    for (net.minecraft.tags.TagKey<net.minecraft.world.item.Item> t : entryTags) {
                        if (targetTags.contains(t)) {
                            inList = true;
                            break;
                        }
                    }
                    if (inList) {
                        break;
                    }
                }
            }
            return blacklist != inList;
        };
    }

    /** 加入过滤名单（去重）。名单满了返回 false。 */
    public static boolean addFilter(ItemStack stack, ItemStack sample) {
        if (sample.isEmpty()) {
            return false;
        }
        ItemStackHandler h = filterHandler(stack);
        for (int i = 0; i < h.getSlots(); i++) {
            if (ItemStack.isSameItem(h.getStackInSlot(i), sample)) {
                return false;
            }
        }
        for (int i = 0; i < h.getSlots(); i++) {
            if (h.getStackInSlot(i).isEmpty()) {
                ItemStack one = sample.copy();
                one.setCount(1);
                h.setStackInSlot(i, one);
                stack.getOrCreateTag().put(K_FILTER_LIST, h.serializeNBT());
                return true;
            }
        }
        return false;
    }

    public static void removeFilter(ItemStack stack, int index) {
        if (index < 0 || index >= FILTER_SLOTS) {
            return;
        }
        ItemStackHandler h = filterHandler(stack);
        h.setStackInSlot(index, ItemStack.EMPTY);
        stack.getOrCreateTag().put(K_FILTER_LIST, h.serializeNBT());
    }

    public static void clearFilters(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(K_FILTER_LIST);
        }
    }

    // ------------------------------------------------------------------
    // 女仆槽位锁定（原版 SlotConfigData 格式：38 字节，非 0 = 锁定不搬运）
    // ------------------------------------------------------------------

    /** 原始 38 字节锁定数组（不足补 0、超出截断），给终端的槽位配置界面用。 */
    public static byte[] slotLockBytes(ItemStack stack) {
        byte[] out = new byte[ORIGINAL_SLOT_NUM];
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(K_SLOT_CONFIG, Tag.TAG_BYTE_ARRAY)) {
            byte[] raw = tag.getByteArray(K_SLOT_CONFIG);
            System.arraycopy(raw, 0, out, 0, Math.min(raw.length, out.length));
        }
        return out;
    }

    /** 写入 38 字节锁定数组（与原版 GUI 存的东西完全同格式，互通）。 */
    public static void setSlotLocks(ItemStack stack, byte[] locks) {
        stack.getOrCreateTag().putByteArray(K_SLOT_CONFIG, Arrays.copyOf(locks, ORIGINAL_SLOT_NUM));
    }

    // ------------------------------------------------------------------
    // 拿取数量（存在隙间物品上：终端点击与女仆每轮搬运共用同一个上限）
    // ------------------------------------------------------------------

    /** 未设置时默认 64（整组，等同原版女仆搬运行为）。 */
    public static int pickupAmount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(K_PICKUP)) {
            int v = tag.getInt(K_PICKUP);
            if (v > 0) {
                return Math.min(v, 64);
            }
        }
        return 64;
    }

    /** 按配置里的预设列表循环切换拿取数量。 */
    public static void cyclePickupAmount(ItemStack stack) {
        List<Integer> presets = cn.blockforge.generated.maidbettergap.MaidGapConfig.pickupPresets();
        int cur = pickupAmount(stack);
        int idx = presets.indexOf(cur);
        int next = idx < 0 ? presets.get(0) : presets.get((idx + 1) % presets.size());
        stack.getOrCreateTag().putInt(K_PICKUP, next);
    }

    // ------------------------------------------------------------------
    // 原版女仆槽位锁定（只读兼容：原版 GUI 锁住的槽不参与 箱子->女仆）
    // ------------------------------------------------------------------

    public static boolean[] slotLocks(ItemStack stack, int maidSlots) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(K_SLOT_CONFIG, Tag.TAG_BYTE_ARRAY)) {
            return null;
        }
        byte[] raw = tag.getByteArray(K_SLOT_CONFIG);
        // 目标数组长度跟女仆实际槽位数对齐；不足补 0，绝不用负下标/越界下标（否则女仆 tick 直接崩）
        int size = Math.max(maidSlots, Math.min(raw.length, ORIGINAL_SLOT_NUM));
        byte[] tmp = Arrays.copyOf(raw, size);
        // 原版按 38 槽存（0-35 背包 + 36 主手 + 37 副手）：
        // 女仆实际槽位比 38 多时，把最后两格（主手/副手）的锁定搬到实际数组的末尾
        if (raw.length >= ORIGINAL_SLOT_NUM && size > raw.length && size >= 2) {
            tmp[size - 2] = raw[ORIGINAL_SLOT_NUM - 2];
            tmp[size - 1] = raw[ORIGINAL_SLOT_NUM - 1];
        }
        boolean[] out = new boolean[tmp.length];
        for (int i = 0; i < tmp.length; i++) {
            out[i] = tmp[i] != 0;
        }
        return out;
    }
}
