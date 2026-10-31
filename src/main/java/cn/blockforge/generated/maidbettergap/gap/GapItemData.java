package cn.blockforge.generated.maidbettergap.gap;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

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
 * 单容器的独立过滤名单用 MGBindingFilters（按绑定坐标索引），没设过的容器沿用上面的共用名单。
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

    /** 单容器独立名单：ListTag，每个元素 = {key, on, black, tag, list}，key 就是绑定坐标。
     *  on=false 表示这套独立名单只是“存档”（切回共用了，数据留着，再切回来能恢复）。 */
    private static final String K_BIND_FILTERS = "MGBindingFilters";
    /** 迁移完成标记（服务端写）：这份物品的名单 NBT 至少被清洗过一次。
     *  注意它**不再**用来跳过扫描——名单可能被原版隙间 GUI 之类的外部代码再次写脏，见 sanitizeFilters。 */
    private static final String K_SANITIZED = "MGSanitized";
    /** ItemStackHandler 序列化格式里物品列表的键名。 */
    private static final String NBT_ITEMS = "Items";
    private static final String K_BF_KEY = "key";
    private static final String K_BF_ON = "on";
    private static final String K_BF_BLACK = "black";
    private static final String K_BF_TAG = "tag";
    private static final String K_BF_LIST = "list";

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
            // 对账：原版 GUI 只会改 BindingPos（它只有单容器、而且不带维度）。
            // 如果 BindingPos 指向的位置不在我们的列表里，说明玩家是在原版界面上改的绑定，
            // 把它也算上——否则会出现“原版 GUI 里明明绑了，终端却不认”。
            // 原版格式不带维度，只能按当前维度兜底（这也是原版自己的行为）。
            if (tag.contains(K_BINDING_POS, Tag.TAG_COMPOUND)) {
                CompoundTag p = tag.getCompound(K_BINDING_POS);
                BlockPos pos = new BlockPos(p.getInt("X"), p.getInt("Y"), p.getInt("Z"));
                boolean known = false;
                for (Binding b : out) {
                    if (b.pos().equals(pos)) {
                        known = true;
                        break;
                    }
                }
                if (!known) {
                    String dim = fallbackDim == null ? "minecraft:overworld" : fallbackDim;
                    out.add(new Binding(dim, pos, Binding.KIND_ITEM));
                }
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
        // 解绑清理：删掉所有已经不在绑定列表里的容器名单（含“存档”的独立名单）。
        // 否则旧名单会一直躺在物品 NBT 里越积越多，而且以后在原坐标重绑一个新容器时，
        // 这份玩家早忘了的旧名单会“复活”并悄悄生效。
        Set<String> keepKeys = new HashSet<>();
        for (Binding b : bindings) {
            keepKeys.add(b.key());
        }
        pruneOwnFilters(tag, keepKeys);
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
    // 传输方向（原版键，原版 GUI 通用）
    // ------------------------------------------------------------------

    public static boolean isMaidToChest(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(K_IO_MODE);
    }

    public static void toggleMaidToChest(ItemStack stack) {
        stack.getOrCreateTag().putBoolean(K_IO_MODE, !isMaidToChest(stack));
    }

    // ------------------------------------------------------------------
    // 过滤名单：默认一套「共用名单」；也可给某个容器单独开一套「独立名单」
    //
    // 为什么这么设计：老存档的名单是共用的，直接改成“每个容器一套”会让所有人的
    // 现有设置一夜之间失效。所以规则是「没单独设过的容器沿用共用名单」，
    // 玩家在终端里给某个箱子点了「名单:独立」之后，那个容器才只认自己的名单。
    // ------------------------------------------------------------------

    /** 这个容器是不是已经有了自己的独立名单（false = 沿用隙间的共用名单）。 */
    public static boolean hasOwnFilter(ItemStack stack, @Nullable Binding binding) {
        return binding != null && ownFilterEntry(stack, binding) != null;
    }

    /** 当前正在生效的独立名单条目（只“存档”、已切回共用的返回 null）。 */
    @Nullable
    private static CompoundTag ownFilterEntry(ItemStack stack, Binding binding) {
        CompoundTag e = rawOwnFilterEntry(stack, binding);
        return e != null && entryActive(e) ? e : null;
    }

    /** 条目是否处于“独立生效”状态。老存档（1.3.0 之前）的条目没有 on 键，一律视为生效。 */
    private static boolean entryActive(CompoundTag e) {
        return !e.contains(K_BF_ON) || e.getBoolean(K_BF_ON);
    }

    /** 原始条目（可能已切回共用、只是存档），供开关切换用。 */
    @Nullable
    private static CompoundTag rawOwnFilterEntry(ItemStack stack, Binding binding) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(K_BIND_FILTERS, Tag.TAG_LIST)) {
            return null;
        }
        String key = binding.key();
        ListTag list = tag.getList(K_BIND_FILTERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            if (key.equals(e.getString(K_BF_KEY))) {
                return e;
            }
        }
        return null;
    }

    /** 删掉 key 不在 keepKeys 里的所有独立名单条目（解绑后调用，见 setBindings）。 */
    private static void pruneOwnFilters(CompoundTag tag, Set<String> keepKeys) {
        if (!tag.contains(K_BIND_FILTERS, Tag.TAG_LIST)) {
            return;
        }
        ListTag old = tag.getList(K_BIND_FILTERS, Tag.TAG_COMPOUND);
        ListTag out = new ListTag();
        for (int i = 0; i < old.size(); i++) {
            CompoundTag e = old.getCompound(i);
            if (keepKeys.contains(e.getString(K_BF_KEY))) {
                out.add(e);
            }
        }
        if (out.isEmpty()) {
            tag.remove(K_BIND_FILTERS);
        } else {
            tag.put(K_BIND_FILTERS, out);
        }
    }

    /** 这个隙间上有几个容器开着独立名单（tooltip 用）。 */
    public static int ownFilterCount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(K_BIND_FILTERS, Tag.TAG_LIST)) {
            return 0;
        }
        ListTag list = tag.getList(K_BIND_FILTERS, Tag.TAG_COMPOUND);
        int n = 0;
        for (int i = 0; i < list.size(); i++) {
            if (entryActive(list.getCompound(i))) {
                n++;
            }
        }
        return n;
    }

    /** 写入（或替换）某个容器的独立名单条目。 */
    private static void putOwnFilterEntry(ItemStack stack, Binding binding, CompoundTag entry) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag old = tag.contains(K_BIND_FILTERS, Tag.TAG_LIST)
                ? tag.getList(K_BIND_FILTERS, Tag.TAG_COMPOUND) : new ListTag();
        ListTag out = new ListTag();
        String key = binding.key();
        for (int i = 0; i < old.size(); i++) {
            CompoundTag e = old.getCompound(i);
            if (!key.equals(e.getString(K_BF_KEY))) {
                out.add(e);
            }
        }
        entry.putString(K_BF_KEY, key);
        out.add(entry);
        tag.put(K_BIND_FILTERS, out);
    }

    /**
     * 打开/关闭某个容器的独立名单。
     * 打开时把当前生效的名单复制过去（玩家不用重摆一遍），之后这个容器的增删只影响它自己；
     * 关闭不再删除数据，而是“存档”（on=false）——玩家随时切回独立能拿回原来那份名单。
     * 真正删除只有解绑（setBindings 清理）那一条路。
     */
    public static void setOwnFilter(ItemStack stack, @Nullable Binding binding, boolean own) {
        if (binding == null || hasOwnFilter(stack, binding) == own) {
            return;
        }
        CompoundTag archived = rawOwnFilterEntry(stack, binding);
        if (!own) {
            if (archived != null) {
                archived.putBoolean(K_BF_ON, false);
                putOwnFilterEntry(stack, binding, archived.copy());
            }
            return;
        }
        if (archived != null) {
            // 之前存档过：直接恢复那份名单，不用从共用复制
            archived.putBoolean(K_BF_ON, true);
            putOwnFilterEntry(stack, binding, archived.copy());
            return;
        }
        CompoundTag e = new CompoundTag();
        e.putBoolean(K_BF_ON, true);
        e.putBoolean(K_BF_BLACK, isBlacklist(stack));
        e.putBoolean(K_BF_TAG, isTagMatch(stack));
        e.put(K_BF_LIST, filterHandler(stack, null).serializeNBT());
        putOwnFilterEntry(stack, binding, e);
    }

    /** 这个容器实际生效的黑白名单模式（独立名单优先，否则用共用设置）。 */
    public static boolean isBlacklist(ItemStack stack) {
        return isBlacklist(stack, null);
    }

    public static boolean isBlacklist(ItemStack stack, @Nullable Binding binding) {
        CompoundTag own = binding == null ? null : ownFilterEntry(stack, binding);
        if (own != null) {
            return own.getBoolean(K_BF_BLACK);
        }
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(K_FILTER_MODE);
    }

    public static void toggleBlacklist(ItemStack stack) {
        toggleBlacklist(stack, null);
    }

    /** 有独立名单就改那套，否则改共用设置（终端里点“白名单/黑名单”就是它）。 */
    public static void toggleBlacklist(ItemStack stack, @Nullable Binding binding) {
        boolean next = !isBlacklist(stack, binding);
        CompoundTag own = binding == null ? null : ownFilterEntry(stack, binding);
        if (own != null) {
            own.putBoolean(K_BF_BLACK, next);
            putOwnFilterEntry(stack, binding, own.copy());
            return;
        }
        stack.getOrCreateTag().putBoolean(K_FILTER_MODE, next);
    }

    /** 类别匹配：名单里的物品按“同类标签”匹配（一条鳕鱼覆盖所有鱼）。 */
    public static boolean isTagMatch(ItemStack stack) {
        return isTagMatch(stack, null);
    }

    public static boolean isTagMatch(ItemStack stack, @Nullable Binding binding) {
        CompoundTag own = binding == null ? null : ownFilterEntry(stack, binding);
        if (own != null) {
            return own.getBoolean(K_BF_TAG);
        }
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(K_TAG_MATCH);
    }

    public static void toggleTagMatch(ItemStack stack) {
        toggleTagMatch(stack, null);
    }

    public static void toggleTagMatch(ItemStack stack, @Nullable Binding binding) {
        boolean next = !isTagMatch(stack, binding);
        CompoundTag own = binding == null ? null : ownFilterEntry(stack, binding);
        if (own != null) {
            own.putBoolean(K_BF_TAG, next);
            putOwnFilterEntry(stack, binding, own.copy());
            return;
        }
        stack.getOrCreateTag().putBoolean(K_TAG_MATCH, next);
    }

    // ------------------------------------------------------------------
    // 物品白名单：9 格过滤名单（原版 ItemFilterList 同格式）
    // ------------------------------------------------------------------

    private static ItemStackHandler filterHandler(ItemStack stack, @Nullable Binding binding) {
        ItemStackHandler h = new ItemStackHandler(FILTER_SLOTS) {
            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
        CompoundTag tag = stack.getTag();
        CompoundTag own = binding == null ? null : ownFilterEntry(stack, binding);
        CompoundTag src = null;
        if (own != null) {
            src = own.getCompound(K_BF_LIST);
        } else if (tag != null && tag.contains(K_FILTER_LIST, Tag.TAG_COMPOUND)) {
            src = tag.getCompound(K_FILTER_LIST);
        }
        if (src != null && !src.isEmpty()) {
            h.deserializeNBT(src);
        }
        // 读取时净化（只修内存副本，不写回）：名单条目只保留“是哪个物品”，丢掉全部 NBT。
        // 匹配本来就只看物品和物品标签（NBT 不参与判断），旧版本却把完整物品存了进来，
        // 一个“塞满成书的潜影盒”能让隙间物品的 NBT 涨几 MB——随物品同步时会撑爆
        // 2 MB 上限把客户端直接踢下线。1.3.0 起新写入的名单已经是干净的，
        // 这里负责让升级前留下的脏数据在“本次使用”里表现正常。
        // 注意：真正写回存档由服务端调用 sanitizeFilters 完成（见该方法注释）——
        // 本方法在客户端 tooltip 里也会被调到，客户端改写物品副本毫无意义还会产生副作用。
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack st = h.getStackInSlot(i);
            if (!st.isEmpty() && (st.hasTag() || st.getCount() != 1)) {
                h.setStackInSlot(i, new ItemStack(st.getItem()));
            }
        }
        return h;
    }

    /**
     * 服务端专用：把这份隙间上所有名单（共用 + 每个容器的独立名单，含“存档”的）一次性净化并写回 NBT。
     *
     * 为什么需要它：r23 及以前往名单里存过带 NBT 的完整物品（附魔书、药水、塞了东西的潜影盒），
     * 这些脏数据如果一直躺在物品里，会随原版容器同步原样发给客户端，可能撑爆 2 MB 上限踢人。
     * 只在“打开某个容器的名单”时清洗是不够的——一件隙间可能绑了好几个容器，
     * 没被打开过的那几份脏名单照样会留在物品上。所以这里一次遍历全部。
     *
     * 调用时机（都是服务端）：玩家背包的物品 tick、登录时、女仆饰品搬运 tick、终端快照构建前、
     * 掉落物进入世界时。
     *
     * 为什么不再用“洗过就跳过”的一次性标记：名单不止本模组会写——车万女仆自己的隙间 GUI
     * （`enhance_original_gap` 关掉时完全走原版）照样能往 ItemFilterList 里塞带完整 NBT 的物品。
     * 如果只认标记，被我们洗过一次的隙间，之后再被原版 GUI 写脏就永远洗不掉了。
     * 所以这里每次都真扫：最多 9 + 9×8 = 81 个条目，每个只做一两次 contains 判断，
     * 干净时一次写入都不做；脏了才写回并补上 MGSanitized（该键仅为兼容 r27 存档而保留）。
     */
    public static void sanitizeFilters(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        boolean dirty = false;
        // 共用名单（原版 ItemFilterList，ItemStackHandler 格式）
        if (tag.contains(K_FILTER_LIST, Tag.TAG_COMPOUND)) {
            dirty |= sanitizeStoredList(tag.getCompound(K_FILTER_LIST));
        }
        // 每个容器的独立名单：包括已切回共用、只是“存档”的那几套（它们同样会随物品同步）
        if (tag.contains(K_BIND_FILTERS, Tag.TAG_LIST)) {
            ListTag sets = tag.getList(K_BIND_FILTERS, Tag.TAG_COMPOUND);
            for (int i = 0; i < sets.size(); i++) {
                CompoundTag entry = sets.getCompound(i);
                if (entry.contains(K_BF_LIST, Tag.TAG_COMPOUND)) {
                    dirty |= sanitizeStoredList(entry.getCompound(K_BF_LIST));
                }
            }
        }
        if (dirty) {
            tag.putBoolean(K_SANITIZED, true);
        }
    }

    /**
     * 清洗一个序列化后的 9 格名单：条目只留“是哪个物品”，数量归 1。
     *
     * 要清两处：`tag`（附魔、药水、潜影盒内容物等）和根上的 `ForgeCaps`
     * （Forge 把物品的能力数据写在根 compound 而不是 tag 里，只删 tag 会漏）。
     * 匹配只看物品 ID 和物品标签，删掉这些不改变过滤行为。
     *
     * @return 是否真的改动了数据（没脏就不写回，避免每次扫描都触发物品同步）
     */
    private static boolean sanitizeStoredList(CompoundTag handlerTag) {
        if (!handlerTag.contains(NBT_ITEMS, Tag.TAG_LIST)) {
            return false;
        }
        boolean dirty = false;
        ListTag items = handlerTag.getList(NBT_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag e = items.getCompound(i);
            if (e.contains("tag")) {
                e.remove("tag");
                dirty = true;
            }
            if (e.contains("ForgeCaps")) {
                e.remove("ForgeCaps");
                dirty = true;
            }
            if (e.contains("Count") && e.getByte("Count") != 1) {
                e.putByte("Count", (byte) 1);
                dirty = true;
            }
        }
        return dirty;
    }

    private static void saveFilterHandler(ItemStack stack, @Nullable Binding binding, ItemStackHandler h) {
        CompoundTag own = binding == null ? null : ownFilterEntry(stack, binding);
        if (own != null) {
            own.put(K_BF_LIST, h.serializeNBT());
            putOwnFilterEntry(stack, binding, own.copy());
            return;
        }
        stack.getOrCreateTag().put(K_FILTER_LIST, h.serializeNBT());
    }

    public static List<ItemStack> filters(ItemStack stack) {
        return filters(stack, null);
    }

    public static List<ItemStack> filters(ItemStack stack, @Nullable Binding binding) {
        ItemStackHandler h = filterHandler(stack, binding);
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
        return matchesFilter(stack, null, target);
    }

    public static boolean matchesFilter(ItemStack stack, @Nullable Binding binding, ItemStack target) {
        if (target.isEmpty()) {
            return false;
        }
        boolean tagMatch = isTagMatch(stack, binding);
        ItemStackHandler h = filterHandler(stack, binding);
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
     * 批量过滤用的预计算断言（女仆每个搬运周期、每个容器构建一次，避免逐件物品重复解析名单）。
     * 语义与原版一致：白名单 = 命中才搬；黑名单 = 命中就不搬。
     */
    public static Predicate<ItemStack> filterPredicate(ItemStack gap) {
        return filterPredicate(gap, null);
    }

    public static Predicate<ItemStack> filterPredicate(ItemStack gap, @Nullable Binding binding) {
        boolean blacklist = isBlacklist(gap, binding);
        boolean tagMatch = isTagMatch(gap, binding);
        List<Item> exact = new ArrayList<>();
        List<Set<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>>> tagSets = new ArrayList<>();
        ItemStackHandler h = filterHandler(gap, binding);
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

    /** 名单里是不是已经有这个物品了（按物品类型判，和 addFilter 的去重口径一致）。 */
    public static boolean containsFilter(ItemStack stack, ItemStack sample) {
        return containsFilter(stack, null, sample);
    }

    public static boolean containsFilter(ItemStack stack, @Nullable Binding binding, ItemStack sample) {
        if (sample.isEmpty()) {
            return false;
        }
        ItemStackHandler h = filterHandler(stack, binding);
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack entry = h.getStackInSlot(i);
            if (!entry.isEmpty() && ItemStack.isSameItem(entry, sample)) {
                return true;
            }
        }
        return false;
    }

    /** 加入过滤名单（去重）。名单满了返回 false。 */
    public static boolean addFilter(ItemStack stack, ItemStack sample) {
        return addFilter(stack, null, sample);
    }

    public static boolean addFilter(ItemStack stack, @Nullable Binding binding, ItemStack sample) {
        if (sample.isEmpty()) {
            return false;
        }
        ItemStackHandler h = filterHandler(stack, binding);
        for (int i = 0; i < h.getSlots(); i++) {
            if (ItemStack.isSameItem(h.getStackInSlot(i), sample)) {
                return false;
            }
        }
        for (int i = 0; i < h.getSlots(); i++) {
            if (h.getStackInSlot(i).isEmpty()) {
                // 名单只存“是哪个物品”：不带 NBT、数量固定 1。
                // 匹配只看物品本身和标签，NBT 从来不参与判断；以前 sample.copy() 原样入名单，
                // 一个“塞满成书的潜影盒”能往隙间物品的 NBT 里灌几 MB，
                // 随物品同步时撑爆 2 MB 上限把玩家踢下线（见 filterHandler 的读取净化）。
                ItemStack one = new ItemStack(sample.getItem());
                h.setStackInSlot(i, one);
                saveFilterHandler(stack, binding, h);
                return true;
            }
        }
        return false;
    }

    public static void removeFilter(ItemStack stack, int index) {
        removeFilter(stack, null, index);
    }

    public static void removeFilter(ItemStack stack, @Nullable Binding binding, int index) {
        if (index < 0 || index >= FILTER_SLOTS) {
            return;
        }
        ItemStackHandler h = filterHandler(stack, binding);
        h.setStackInSlot(index, ItemStack.EMPTY);
        saveFilterHandler(stack, binding, h);
    }

    public static void clearFilters(ItemStack stack) {
        clearFilters(stack, null);
    }

    /** 清空名单：有独立名单就只清它，否则清共用名单。 */
    public static void clearFilters(ItemStack stack, @Nullable Binding binding) {
        ItemStackHandler h = filterHandler(stack, binding);
        for (int i = 0; i < h.getSlots(); i++) {
            h.setStackInSlot(i, ItemStack.EMPTY);
        }
        saveFilterHandler(stack, binding, h);
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
        // 女仆实际槽位比 38 多时，把最后两格（主手/副手）的锁定搬到实际数组的末尾。
        // 搬之前必须先把原位置清零：否则多出来的背包第 36/37 格会被误锁（整体错位一格）。
        if (raw.length >= ORIGINAL_SLOT_NUM && size > raw.length && size >= 2) {
            tmp[ORIGINAL_SLOT_NUM - 2] = 0;
            tmp[ORIGINAL_SLOT_NUM - 1] = 0;
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
