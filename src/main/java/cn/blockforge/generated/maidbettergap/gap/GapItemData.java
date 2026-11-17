package cn.blockforge.generated.maidbettergap.gap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

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
 * 键名与原版 touhou_little_maid:wireless_io 完全兼容——但「原版把数据存在哪」分两个时代：
 * <ul>
 *   <li>1.20.1：物品 NBT（custom_data）里就是下面这套键名，我们直接读写；</li>
 *   <li>1.21.1（车万女仆 1.5.x）：原版把这些键迁进了**五个独立数据组件**
 *       （binding_pos / item_io_mode / item_filter_mode / item_filter_list / slot_config_data），
 *       custom_data 里不再放。本类对原版隙间做了双轨：读时组件优先、custom_data 兜底；
 *       写时两边同步；旧存档搬来的 custom_data 值会被一次性提升到组件
 *       （见 {@link TlmIoComponents} 与 sanitizeFilters 末尾的 promoteLegacyComponents）。</li>
 * </ul>
 * 各键语义不变：
 *   BindingPos      —— 原版单容器绑定坐标 {X,Y,Z}（我们同步写第一条物品绑定，原版 GUI 照常显示）
 *   ItemIOMode      —— true = 女仆->箱子，false = 箱子->女仆
 *   ItemFilterMode  —— true = 黑名单，false = 白名单
 *   ItemFilterList  —— 9 格过滤名单（原版 ItemStackHandler 格式）
 *   SlotConfigData  —— 女仆槽位锁定（原版格式，38 字节；本模组终端里可视化编辑）
 * 多容器绑定用我们自己的键 MGBindings 扩展（只放 custom_data），不影响原版功能。
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
    /**
     * 迁移标记此刻是否有权关闭 custom_data 回退（R2：标记感知）。
     *
     * 光看标记不够——标记的含义是「组件轨已经接管」，前提是组件轨**还存在**。
     * 前置被卸载或降级到没有这五个组件的老版本时，类型从注册表里消失、
     * 物品上的组件值也读不到了，组件轨等于整条蒸发；这时旧标记必须失效、
     * 回退重新打开，否则所有回退口全被一个「已无意义」的标记关死，
     * 1.20.1 时代的旧数据永久不可见、换回版本前也没有自动恢复（和 R1 是同一个洞的两半：
     * R1 管「标记不该立的时候立了」，R2 管「标记还在但轨已经没了的时候它不该继续管事」）。
     * 类型查不到的负缓存（ABSENT）让这里只是几次 map.get，不贵。
     */
    private static boolean migrationBlocks(@Nullable CompoundTag tag, ItemStack stack, String componentId) {
        return isComponentMigrated(tag) && TlmIoComponents.isTrackRegistered(componentId);
    }

    /**
     * 旧值→组件的一次性迁移完成标记（N1，只对车万女仆原版隙间写）。
     *
     * 【要堵的洞】custom_data 里的旧版键（BindingPos 等）在组件轨建立后就是“过期镜像”：
     * 原版 GUI 改绑只写组件。r42 修掉了「GUI 改绑到别处、旧镜像被合并复活」这一支
     * （读取时组件优先），但还剩一支：**GUI 直接解绑**时组件被整个删掉，
     * 「组件缺失」就与「旧存档还没迁移」两种状态撞车——旧镜像又被当成有效绑定合并，
     * 幽灵绑定继续搬运；promoteLegacyComponents 还会把它提升回组件，等于帮倒忙。
     *
     * 【怎么区分】见过一次组件轨 = 迁移已完成（本标记）：此后 custom_data 的旧键
     * 一律不再读取、不再提升；原版 GUI 解绑就是真解绑。非原版隙间永远没有这个标记，
     * 它们只有 custom_data 一条轨，旧键永远有效。
     */
    private static final String K_MIGRATED = "MGComponentMigrated";

    /**
     * 迁移标记的版本号（R8，r52 复审后改读）：**按版本比对**——byte 且 ≥ 本值才算已迁移。
     *
     * 旧的"任意非零 = 已迁移"读法让版本号形同虚设（getBoolean 等价 getByte != 0，
     * 1b 与 2b 根本分不开），而它有真实的受害人群：r47–r50 存在"组件轨提升失败也照样立标记"
     * 的缺陷（allLanded 记账 r51 才补上，修得了新标记、救不了已误立的存量），
     * 那批物品被遮死的旧 custom_data 永远没有自愈路径。版本号现在终于管事：
     * 低版本标记（r47–r51 写的 1b/true）一律视作未迁移 → custom_data 回退重开 →
     * 下一轮服务端扫描 promoteLegacyComponents 再跑一遍——carries() 认账跳过已有值、
     * 显式解绑的墓碑同样算接管，正确迁移过的物品是一次 O(1) 幂等重跑、改立新版本号后自愈；
     * 被误立标记的物品则回退重开、旧数据复活——后者正是修复目标。
     * 手改成其它类型（如 IntTag）同样视作未迁移，保守方向安全。
     */
    private static final byte MIGRATED_VERSION = 2;

    /**
     * custom_data 的旧版键还可不可以当作回退读？
     * 非原版隙间永远可以（它们只有 custom_data 一条轨）；原版隙间在一次性迁移
     * （promoteLegacyComponents）完成后就不行了——那时起组件是唯一真相，
     * 「组件不存在」= 原版 GUI 主动删过（解绑/清空），不是「还没迁移」。
     */
    private static boolean isComponentMigrated(@Nullable CompoundTag tag) {
        // R8（r52 复审）：按版本读。getBoolean 在 1.21.1 里就是 getByte != 0，
        // 会把我方写 1b 还是 2b 一概吞掉——版本号必须在这里真正参与比较。
        return tag != null
                && tag.contains(K_MIGRATED, Tag.TAG_BYTE)
                && tag.getByte(K_MIGRATED) >= MIGRATED_VERSION;
    }
    /** ItemStackHandler 序列化格式里物品列表的键名。 */
    private static final String NBT_ITEMS = "Items";
    private static final String K_BF_KEY = "key";
    private static final String K_BF_ON = "on";
    private static final String K_BF_BLACK = "black";
    private static final String K_BF_TAG = "tag";
    private static final String K_BF_LIST = "list";

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private GapItemData() {}

    // ------------------------------------------------------------------
    // 容器绑定（多容器 + 原版单容器兼容）
    // ------------------------------------------------------------------

    public static List<Binding> bindings(ItemStack stack, String fallbackDim) {
        List<Binding> out = new ArrayList<>();
        if (stack.isEmpty()) {
            return out;
        }
        CompoundTag tag = StackData.get(stack);
        if (tag != null && tag.contains(K_BINDINGS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(K_BINDINGS, Tag.TAG_COMPOUND);
            // 自动合并重复绑定：同一容器（同维度同坐标）出现多条时只认第一条。
            // 正常操作走 bindOrToggle 不会产生重复，这里兜底异常数据/手改 NBT/外部写入，
            // 顺带让终端界面只显示一条。
            Set<String> seenKeys = new HashSet<>();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                Binding b = new Binding(e.getString("dim"),
                        new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z")), e.getByte("kind"));
                if (seenKeys.add(b.key())) {
                    out.add(b);
                }
            }
            // 对账①：旧版 custom_data 里的 BindingPos（1.20.1 的原版 GUI / r40 移植版只写过 custom_data）。
            // 位置不在我们的列表里 = 玩家是在原版界面上改的绑定，把它也算上——
            // 否则会出现“原版 GUI 里明明绑了，终端却不认”。
            // 原版格式不带维度，只能按当前维度兜底（这也是原版自己的行为）。
            // 【组件优先】1.21.1 组件 binding_pos 有值时 custom_data 那份必定是过期镜像
            // （原版 GUI 改绑只写组件、不碰我们的 custom_data），再合并旧坐标会把解掉的
            // 容器“复活”成幽灵绑定继续搬运（r42 复审同族问题，本轮修复）；
            // 只在组件缺失（旧存档还没迁移）时才认 custom_data。
            // R2：门禁问「携带」而不是「解码得出」（carries，同提升侧 R3）——
            // 组件携带着但我们读不出（对方换了载荷类型）时组件依然是一手真相，
            // 退回旧镜像反而比读空更误导；标记要「组件轨还活着」才关得住回退（migrationBlocks）。
            if (!TlmIoComponents.carries(stack, TlmIoComponents.ID_BINDING_POS)
                    && !migrationBlocks(tag, stack, TlmIoComponents.ID_BINDING_POS)
                    && tag.contains(K_BINDING_POS, Tag.TAG_COMPOUND)) {
                CompoundTag p = tag.getCompound(K_BINDING_POS);
                addLegacyBinding(out, new BlockPos(p.getInt("X"), p.getInt("Y"), p.getInt("Z")), fallbackDim);
            }
            // 对账②：1.21.1 原版 GUI 写的是数据组件 binding_pos，同样按位置补进来。
            addLegacyBinding(out, TlmIoComponents.bindingPos(stack), fallbackDim);
            return out;
        }
        // 兼容：只用过原版功能的物品。1.21.1 先看组件（原版 GUI 写的就是它），
        // 组件缺失时再看 1.20.1 时代的 custom_data BindingPos（旧存档直接搬来的物品只有它）。
        // 与上面同理：组件携带值、或迁移标记已立（且组件轨还在，R2），就都不再合并旧坐标（N1）。
        addLegacyBinding(out, TlmIoComponents.bindingPos(stack), fallbackDim);
        if (!TlmIoComponents.carries(stack, TlmIoComponents.ID_BINDING_POS) && tag != null
                && !migrationBlocks(tag, stack, TlmIoComponents.ID_BINDING_POS)
                && tag.contains(K_BINDING_POS, Tag.TAG_COMPOUND)) {
            CompoundTag p = tag.getCompound(K_BINDING_POS);
            addLegacyBinding(out, new BlockPos(p.getInt("X"), p.getInt("Y"), p.getInt("Z")), fallbackDim);
        }
        return out;
    }

    /** 把原版单容器绑定（不带维度）按位置去重后补进列表；pos 为 null 时什么都不做。 */
    private static void addLegacyBinding(List<Binding> out, @Nullable BlockPos pos, String fallbackDim) {
        if (pos == null) {
            return;
        }
        for (Binding b : out) {
            if (b.pos().equals(pos)) {
                return;
            }
        }
        String dim = fallbackDim == null ? "minecraft:overworld" : fallbackDim;
        out.add(new Binding(dim, pos, Binding.KIND_ITEM));
    }

    public static void setBindings(ItemStack stack, List<Binding> bindings) {
        ListTag list = new ListTag();
        // 写入端同样去重：和 bindings() 的读取合并保持一致，旧档里的重复条目
        // 会在下一次终端改动时被顺手洗掉。
        Set<String> writtenKeys = new HashSet<>();
        for (Binding b : bindings) {
            if (!writtenKeys.add(b.key())) {
                continue;
            }
            CompoundTag e = new CompoundTag();
            e.putString("dim", b.dimension());
            e.putInt("x", b.pos().getX());
            e.putInt("y", b.pos().getY());
            e.putInt("z", b.pos().getZ());
            e.putByte("kind", b.kind());
            list.add(e);
        }
        // 解绑清理：删掉所有已经不在绑定列表里的容器名单（含“存档”的独立名单）。
        // 否则旧名单会一直躺在物品 NBT 里越积越多，而且以后在原坐标重绑一个新容器时，
        // 这份玩家早忘了的旧名单会“复活”并悄悄生效。
        Set<String> keepKeys = new HashSet<>();
        for (Binding b : bindings) {
            keepKeys.add(b.key());
        }
        // 同步原版 BindingPos = 第一条物品容器绑定，保证原版 GUI 显示一致
        Binding firstItem0 = null;
        for (Binding b : bindings) {
            if (!b.isFluid()) {
                firstItem0 = b;
                break;
            }
        }
        final Binding firstItem = firstItem0;
        StackData.update(stack, tag -> {
            tag.put(K_BINDINGS, list);
            pruneOwnFilters(tag, keepKeys);
            if (firstItem != null) {
                CompoundTag p = new CompoundTag();
                p.putInt("X", firstItem.pos().getX());
                p.putInt("Y", firstItem.pos().getY());
                p.putInt("Z", firstItem.pos().getZ());
                tag.put(K_BINDING_POS, p);
            } else {
                tag.remove(K_BINDING_POS);
            }
        });
        // 1.21.1 同步写组件：原版 GUI / tooltip / 原版搬运逻辑只认 binding_pos 组件。
        TlmIoComponents.setBindingPos(stack, firstItem == null ? null : firstItem.pos());
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
        // 1.21.1 原版 GUI 写的是 item_io_mode 组件：有组件就组件优先，
        // custom_data 只是它的镜像 + 非原版隙间物品的唯一存放处。
        // 迁移标记已立的原版隙间不再回退 custom_data（N1：组件被原版 GUI 删过 = 真没有，
        // 不是“没迁移”）；R2：轨没了标记就失效，回退重新打开。
        Boolean comp = TlmIoComponents.ioMode(stack);
        if (comp != null) {
            return comp;
        }
        // R2：组件携带着但解码不出（对方版本换了载荷类型）同样不回退——
        // 镜像只在我们「看不见组件轨」时才作数，携带但读不出时读旧镜像更误导。
        CompoundTag tag = StackData.get(stack);
        return tag != null
                && !TlmIoComponents.carries(stack, TlmIoComponents.ID_IO_MODE)
                && !migrationBlocks(tag, stack, TlmIoComponents.ID_IO_MODE)
                && tag.getBoolean(K_IO_MODE);
    }

    public static void toggleMaidToChest(ItemStack stack) {
        boolean next = !isMaidToChest(stack);
        StackData.update(stack, t -> t.putBoolean(K_IO_MODE, next));
        TlmIoComponents.setIoMode(stack, next);
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
        CompoundTag tag = StackData.get(stack);
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
        CompoundTag tag = StackData.get(stack);
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
        String key = binding.key();
        entry.putString(K_BF_KEY, key);
        StackData.update(stack, tag -> {
            ListTag old = tag.contains(K_BIND_FILTERS, Tag.TAG_LIST)
                    ? tag.getList(K_BIND_FILTERS, Tag.TAG_COMPOUND) : new ListTag();
            ListTag out = new ListTag();
            for (int i = 0; i < old.size(); i++) {
                CompoundTag e = old.getCompound(i);
                if (!key.equals(e.getString(K_BF_KEY))) {
                    out.add(e);
                }
            }
            out.add(entry);
            tag.put(K_BIND_FILTERS, out);
        });
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
        e.put(K_BF_LIST, writeFilterList(filterHandler(stack, null)));
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
        // 共用模式：1.21.1 组件优先（原版 GUI 改的是它），custom_data 兜底。
        // 迁移标记已立的原版隙间不再兜底（N1），同 isMaidToChest；R2 的轨存活判定同样生效。
        Boolean comp = TlmIoComponents.filterMode(stack);
        if (comp != null) {
            return comp;
        }
        // R2：同 isMaidToChest，携带但读不出不回退。
        CompoundTag tag = StackData.get(stack);
        return tag != null
                && !TlmIoComponents.carries(stack, TlmIoComponents.ID_FILTER_MODE)
                && !migrationBlocks(tag, stack, TlmIoComponents.ID_FILTER_MODE)
                && tag.getBoolean(K_FILTER_MODE);
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
        StackData.update(stack, t -> t.putBoolean(K_FILTER_MODE, next));
        TlmIoComponents.setFilterMode(stack, next);
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
        CompoundTag tag = StackData.get(stack);
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
        StackData.update(stack, t -> t.putBoolean(K_TAG_MATCH, next));
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
        CompoundTag tag = StackData.get(stack);
        CompoundTag own = binding == null ? null : ownFilterEntry(stack, binding);
        CompoundTag src = null;
        if (own != null) {
            // 独立名单只住 custom_data（原版没有这个概念），不需要组件双轨
            src = own.getCompound(K_BF_LIST);
        } else {
            // 共用名单：1.21.1 组件优先（原版隙间 GUI 写的是 item_filter_list 组件），
            // custom_data 兜底（非原版隙间、以及旧存档升上来的物品）。
            // 迁移标记已立的原版隙间不再兜底（N1）：原版 GUI 清过名单 = 名单就是空的。
            // R2：组件类型从注册表里消失（前置卸载/降级）时标记失效、兜底重新打开——
            // 否则组件轨和 custom_data 轨同时读不到，玩家刚在终端里设的名单都会凭空蒸发
            // （写路径每次都同步 custom_data，那份镜像才是轨没了之后唯一可读的存档）。
            src = TlmIoComponents.filterList(stack);
            if (src == null && tag != null
                    && !TlmIoComponents.carries(stack, TlmIoComponents.ID_FILTER_LIST)
                    && !migrationBlocks(tag, stack, TlmIoComponents.ID_FILTER_LIST)
                    && tag.contains(K_FILTER_LIST, Tag.TAG_COMPOUND)) {
                src = tag.getCompound(K_FILTER_LIST);
            }
        }
        if (src != null && !src.isEmpty()) {
            loadFilterSlots(h, src);
        }
        return h;
    }

    /**
     * 读取 9 格名单（1.21.1 的 ItemStackHandler 格式：{Items:[{Slot,id,count}],Size}）。
     *
     * 手写解码而不用 {@code h.deserializeNBT(provider, tag)}：1.21.1 起物品序列化必须带
     * 注册表查询（HolderLookup.Provider），而本类的方法链只有 ItemStack、拿不到 level——
     * 为一个用不上的 components 字段把 provider 塞穿十几处调用点不值。
     * 名单只存“是哪个物品”，匹配只看物品和标签，组件数据（旧版 NBT）一概忽略，
     * 顺带天然完成了旧版脏数据（带完整物品的名单）的读取净化：
     * 一个“塞满成书的潜影盒”不会再把隙间 NBT 撑爆 2 MB 同步上限踢玩家。
     * 真正写回存档由服务端 sanitizeFilters 完成（见该方法注释）。
     */
    private static void loadFilterSlots(ItemStackHandler h, CompoundTag src) {
        if (!src.contains(NBT_ITEMS, Tag.TAG_LIST)) {
            return;
        }
        ListTag items = src.getList(NBT_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag e = items.getCompound(i);
            int slot = e.getInt("Slot");
            if (slot < 0 || slot >= h.getSlots()) {
                continue;
            }
            // id：1.21.1 起 holder 序列化仍是字符串；兼容极少数写成 {id:{...}} 的异常数据（跳过）
            if (!e.contains("id", Tag.TAG_STRING)) {
                continue;
            }
            // ITEM 是 DefaultedRegistry：未知 id 返回 air，直接当空槽跳过
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(e.getString("id")));
            if (item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            h.setStackInSlot(slot, new ItemStack(item));
        }
    }

    /** 把内存名单写成 1.21.1 ItemStackHandler 同格式（条目只含 Slot/id/count，永远干净）。 */
    private static CompoundTag writeFilterList(ItemStackHandler h) {
        ListTag items = new ListTag();
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack st = h.getStackInSlot(i);
            if (st.isEmpty()) {
                continue;
            }
            CompoundTag e = new CompoundTag();
            e.putInt("Slot", i);
            e.putString("id", BuiltInRegistries.ITEM.getKey(st.getItem()).toString());
            e.putInt("count", 1);
            items.add(e);
        }
        CompoundTag out = new CompoundTag();
        out.put(NBT_ITEMS, items);
        out.putInt("Size", h.getSlots());
        return out;
    }

    /**
     * 服务端专用：把这份隙间上所有名单（共用 + 每个容器的独立名单，含“存档”的；
     * 1.21.1 原版隙间还包括 item_filter_list 组件）一次性净化并写回物品。
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
        // ① 1.21.1 组件轨（r42 复审 P1）：原版隙间的共用名单住在 item_filter_list 组件里，
        // 原版 GUI 只写这里，条目带完整 ItemStack 序列化（附魔、潜影盒内容全在里面）——
        // 那份能把同步撑爆 2 MB 踢人的脏数据就在组件上。纯原版用出来的隙间根本没有
        // custom_data，这一步必须排在 tag==null 早退**之前**，否则所有清洗点同时失效。
        sanitizeFilterListComponent(stack);
        // ② custom_data 轨：本模组自己的键（独立名单等）+ 非原版隙间的共用名单。
        // 【R5】原版隙间即使完全没有 custom_data 也不能在这里早退：纯原版用出来的隙间
        // 没有旧键可迁，但迁移标记必须照常补立——不然玩家第一次用我们的终端配置后，
        // custom_data 里多出旧键镜像而标记迟迟不立，之后原版 GUI 清空名单/解绑（删组件），
        // 下一轮提升就会把这份刚过期的镜像又「提升」回组件——等于替玩家撤销了清空的
        // 操作（r42 N1 幽灵问题在纯新版物品上的翻版）。空壳只为承接标记，
        // 提升没立起标记时不会写回、一个字节都不落盘。
        CompoundTag tag = StackData.get(stack);
        if (tag == null) {
            if (!TlmIoComponents.isOriginalGap(stack)) {
                return; // 非原版隙间且无 custom_data：没有任何可清洗的东西
            }
            tag = new CompoundTag();
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
        // 顺带做一次性「旧值提升到组件」迁移（只对车万女仆原版隙间生效）：
        // 从 1.20.1（或 r40 移植版）搬上来的物品只有 custom_data、没有数据组件，
        // 车万女仆自己的 GUI / tooltip / 原版搬运逻辑只读组件，看不到这份数据。
        // 提升后原版侧立刻可见；组件已有值时什么都不做（不覆盖更新的一侧）。
        // 迁移完成会立 MGComponentMigrated 标记（N1）：此后 custom_data 旧键不再被读取，
        // 原版 GUI 解绑（删组件）也就不会让旧镜像“复活”。标记要跟着下面的写回落盘。
        dirty |= promoteLegacyComponents(stack, tag);
        // 1.21.1 的数据在 CUSTOM_DATA 组件里，get() 拿到的是副本：
        // 干净时一次写回都不做（避免每秒的例行扫描都触发物品同步），脏了才整块写回。
        if (dirty) {
            tag.putBoolean(K_SANITIZED, true);
            StackData.set(stack, tag);
        }
    }

    /**
     * 净化 1.21.1 原版隙间的 item_filter_list 组件（r42 复审 P1）。
     *
     * 车万女仆 1.5.x 的隙间 GUI 把名单写进这个组件（条目 = 完整 ItemStack 序列化，
     * 带 components 载荷），这是 2 MB 同步撑爆踢人的主通道，custom_data 里根本没有这份数据。
     * 做法与 custom_data 轨一致：条目只留“是哪个物品”、数量归 1。
     * 干净时零写入；脏了拿副本清洗后整块写回组件（原地改组件值容易漏触发同步，不划算）。
     */
    private static void sanitizeFilterListComponent(ItemStack stack) {
        CompoundTag comp = TlmIoComponents.filterList(stack);
        if (comp == null || comp.isEmpty()) {
            return;
        }
        CompoundTag cleaned = comp.copy();
        if (sanitizeStoredList(cleaned)) {
            TlmIoComponents.setFilterList(stack, cleaned);
        }
    }

    /**
     * 一次性迁移：custom_data 里有旧版键、而对应数据组件还没有「显式携带」的值时，把旧值写进组件。
     * 只在原版隙间 (touhou_little_maid:wireless_io) 上发生；非原版隙间一个字都不碰。
     *
     * 完成后在 custom_data 立 MGComponentMigrated 标记（N1）：此后所有读点的 custom_data
     * 回退分支对本物品关闭——组件缺失不再等价于「旧存档还没迁移」，而是「原版 GUI 主动删过」
     * （解绑 / 清空）。少了这道标记，GUI 解绑后旧镜像会被当有效绑定合并回来，
     * 而且下一轮扫描还会把它再提升回组件，等于替玩家“撤销”了解绑。
     *
     * 任何意外都就地咽下且不立标记（下一轮扫描重试）——这只是便利性迁移，
     * 读路径本身已兼容纯 custom_data，迁移失败最多是原版 GUI 暂时看不到旧值，
     * 绝不能把清洗主流程带崩。
     *
     * 【R1】「需要写的每一笔都真的写进去了」才配立标记：组件写路径会在两种情况下
     * 静默跳过（类型查不到 = 前置没这套组件；codec 拒收 = 对方版本改了载荷），
     * 跳过时什么都没搬进组件轨，若照旧立标记，全部回退口立刻被关死，
     * 旧数据从此不可见、且没有任何自动恢复路径。所以这里逐路记账，
     * 有任何一路没落地就不立标记、下轮再试（ABSENT/REJECTED 缓存让重试几乎免费）。
     *
     * 【R3（r52 修订）】「组件已有值」的门禁问 carries()——物品 patch 里有没有记录，
     * 不再用 stack.has()（它会回落到物品默认组件，车万女仆一旦给组件注册默认值就恒真，
     * 把「从没写过」误判成「用户写过」，旧存档数据被凭空遮死，见 TlmIoComponents 类注释）。
     * 门禁不问我们能不能解码：ItemStack.set 是无条件覆盖写——对方把载荷类型换掉后
     * 我们解码不出值，照旧「解码不了就提升」会拿 custom_data 的旧镜像盖掉原版 GUI 刚写的真数据。
     * 携带着但读不出 = 照样跳过写入、照样算这一路「已了结」。显式删除的墓碑同样算接管
     * （玩家清空过的东西不许被旧镜像复活——R2 的另一半，r50 时代只能靠标记兜，现在提前生效）。
     *
     * @return custom_data 是否被本方法改动（立了标记）；调用方据此决定是否写回
     */
    private static boolean promoteLegacyComponents(ItemStack stack, CompoundTag tag) {
        if (!TlmIoComponents.isOriginalGap(stack) || isComponentMigrated(tag)) {
            return false;
        }
        boolean allLanded = true;
        try {
            if (!TlmIoComponents.carries(stack, TlmIoComponents.ID_BINDING_POS)
                    && tag.contains(K_BINDING_POS, Tag.TAG_COMPOUND)) {
                CompoundTag p = tag.getCompound(K_BINDING_POS);
                allLanded &= TlmIoComponents.setBindingPos(stack,
                        new BlockPos(p.getInt("X"), p.getInt("Y"), p.getInt("Z")));
            }
            if (!TlmIoComponents.carries(stack, TlmIoComponents.ID_IO_MODE)
                    && tag.contains(K_IO_MODE, Tag.TAG_BYTE)) {
                allLanded &= TlmIoComponents.setIoMode(stack, tag.getBoolean(K_IO_MODE));
            }
            if (!TlmIoComponents.carries(stack, TlmIoComponents.ID_FILTER_MODE)
                    && tag.contains(K_FILTER_MODE, Tag.TAG_BYTE)) {
                allLanded &= TlmIoComponents.setFilterMode(stack, tag.getBoolean(K_FILTER_MODE));
            }
            if (!TlmIoComponents.carries(stack, TlmIoComponents.ID_FILTER_LIST)
                    && tag.contains(K_FILTER_LIST, Tag.TAG_COMPOUND)) {
                allLanded &= TlmIoComponents.setFilterList(stack, tag.getCompound(K_FILTER_LIST).copy());
            }
            if (!TlmIoComponents.carries(stack, TlmIoComponents.ID_SLOT_CONFIG)
                    && tag.contains(K_SLOT_CONFIG, Tag.TAG_BYTE_ARRAY)) {
                byte[] b = tag.getByteArray(K_SLOT_CONFIG);
                boolean[] flags = new boolean[b.length];
                for (int i = 0; i < b.length; i++) {
                    flags[i] = b[i] != 0;
                }
                allLanded &= TlmIoComponents.setSlotLocks(stack, flags);
            }
        } catch (RuntimeException | LinkageError ex) {
            // 见方法注释：不立标记、静默咽下，下一轮扫描重来
            return false;
        }
        if (!allLanded) {
            // R1：有组件轨没接住提升——这一轮不算迁移完成，不立标记，下轮重试
            return false;
        }
        // 【N1，r50 复审】"哪些旧键要提升"是按 NBT 类型筛的：手改过 NBT、或被第三方
        // 写成 TAG_INT/TAG_LIST 的旧键（contains(key) 真、contains(key, 类型) 假）不进记账，
        // allLanded 照样为 true——标记一立，这类物品"读路认得、提升不认"的旧值就永久被关在
        // 回退门外。所以只要存在类型认不出的旧键就不立标记（每种键警告一次），
        // 让这件物品保持回退可用；能提升的路这轮已经提升完，下轮扫描是幂等空转，不贵。
        if (hasUnrecognizedLegacyKey(tag)) {
            return false;
        }
        // 迁移（含"组件本来就携带值、这一路无需动笔"的情况）到此结束：立一次性标记。
        // R8（r52 复审）：写成带版本号的 byte，isComponentMigrated 按版本比对读——
        // 低于本版本号的旧标记（含 r47–r50 误立的那批）会被判未迁移、回退重开，
        // 再由这轮幂等提升改立新版本号完成自愈。
        tag.putByte(K_MIGRATED, MIGRATED_VERSION);
        return true;
    }

    /** N1：custom_data 里存在「有这份旧键、但 NBT 类型不是提升与读取所认的那一种」。 */
    private static boolean hasUnrecognizedLegacyKey(CompoundTag tag) {
        boolean bad = unexpectedType(tag, K_BINDING_POS, Tag.TAG_COMPOUND);
        bad |= unexpectedType(tag, K_IO_MODE, Tag.TAG_BYTE);
        bad |= unexpectedType(tag, K_FILTER_MODE, Tag.TAG_BYTE);
        bad |= unexpectedType(tag, K_FILTER_LIST, Tag.TAG_COMPOUND);
        bad |= unexpectedType(tag, K_SLOT_CONFIG, Tag.TAG_BYTE_ARRAY);
        return bad;
    }

    private static boolean unexpectedType(CompoundTag tag, String key, int expected) {
        if (!tag.contains(key) || tag.contains(key, expected)) {
            return false;
        }
        // 每种键只警告一次：不立标记的代价是这件物品每轮扫描都白跑一遍，日志别跟着白刷
        if (UNRECOGNIZED_WARNED.add(key)) {
            LOGGER.warn("[maid_better_gap] 隙间 custom_data 的旧键 {} 是意外的 NBT 类型（预期 {}，"
                    + "被手改或被别的模组重写过？）。迁移标记暂不设立，旧值将继续从自带数据读取。",
                    key, typeName(expected));
        }
        return true;
    }

    private static String typeName(int tagId) {
        return switch (tagId) {
            case Tag.TAG_BYTE -> "byte";
            case Tag.TAG_BYTE_ARRAY -> "byte[]";
            case Tag.TAG_COMPOUND -> "compound";
            default -> "tag" + tagId;
        };
    }

    /** N1：警告过哪些"类型认不出的旧键"（每种键一次，见 unexpectedType）。 */
    private static final java.util.Set<String> UNRECOGNIZED_WARNED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 清洗一个序列化后的 9 格名单：条目只留“是哪个物品”，数量归 1。
     *
     * 1.21.1 的条目格式是 {Slot, id, count, components}：`components` 就是旧版 `tag` 的继任者
     * （附魔、药水、潜影盒内容物都住在里面），匹配只看物品 ID 和物品标签，删掉不改变过滤行为。
     * 根上的 `ForgeCaps` 是 1.20.1 Forge 时代留下的脏键，一并防御性清掉。
     * 注意：原版隙间 GUI（1.21.1 的车万女仆）写条目用的是 ItemStack 序列化，会带 count，
     * 这里统一归 1；数量对名单没有意义。
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
            if (e.contains("components")) {
                e.remove("components");
                dirty = true;
            }
            // 1.20.1 时代的旧键（跨版本搬来的存档、或手改 NBT）
            if (e.contains("tag")) {
                e.remove("tag");
                dirty = true;
            }
            if (e.contains("ForgeCaps")) {
                e.remove("ForgeCaps");
                dirty = true;
            }
            if (e.contains("Count")) {
                e.remove("Count");
                dirty = true;
            }
            if (e.contains("count", Tag.TAG_INT) && e.getInt("count") != 1) {
                e.putInt("count", 1);
                dirty = true;
            }
        }
        return dirty;
    }

    private static void saveFilterHandler(ItemStack stack, @Nullable Binding binding, ItemStackHandler h) {
        CompoundTag own = binding == null ? null : ownFilterEntry(stack, binding);
        if (own != null) {
            own.put(K_BF_LIST, writeFilterList(h));
            putOwnFilterEntry(stack, binding, own.copy());
            return;
        }
        CompoundTag written = writeFilterList(h);
        StackData.update(stack, t -> t.put(K_FILTER_LIST, written));
        // 1.21.1：共用名单同步写 item_filter_list 组件，原版 GUI 才能看到我们改的东西。
        // writeFilterList 已经产出 ItemStackHandler 同格式（Size/Items/Slot/id/count），
        // 直接塞进组件，原版 getFilterList 会照常反序列化。
        if (TlmIoComponents.isOriginalGap(stack)) {
            // 传副本：custom_data 与组件是两个持有者，别共享同一个可变量
            TlmIoComponents.setFilterList(stack, written.copy());
        }
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
        byte[] raw = componentLockBytes(stack);
        if (raw == null) {
            CompoundTag tag = StackData.get(stack);
            // 迁移标记已立的原版隙间不再回落 custom_data（N1）：原版 GUI 删过组件 = 真的没锁定。
            // R2：组件轨整个消失时标记失效、回落重新可用；携带但读不出同样不回退。
            if (tag != null
                    && !TlmIoComponents.carries(stack, TlmIoComponents.ID_SLOT_CONFIG)
                    && !migrationBlocks(tag, stack, TlmIoComponents.ID_SLOT_CONFIG)
                    && tag.contains(K_SLOT_CONFIG, Tag.TAG_BYTE_ARRAY)) {
                raw = tag.getByteArray(K_SLOT_CONFIG);
            }
        }
        if (raw != null) {
            System.arraycopy(raw, 0, out, 0, Math.min(raw.length, out.length));
        }
        return out;
    }

    /** 写入 38 字节锁定数组（custom_data + 1.21.1 组件双轨，原版 GUI 互通）。 */
    public static void setSlotLocks(ItemStack stack, byte[] locks) {
        byte[] arr = Arrays.copyOf(locks, ORIGINAL_SLOT_NUM);
        StackData.update(stack, t -> t.putByteArray(K_SLOT_CONFIG, arr));
        if (TlmIoComponents.isOriginalGap(stack)) {
            boolean[] flags = new boolean[arr.length];
            for (int i = 0; i < arr.length; i++) {
                flags[i] = arr[i] != 0;
            }
            TlmIoComponents.setSlotLocks(stack, flags);
        }
    }

    /**
     * 原版隙间「slot_config_data」组件里的锁定值转成 38 字节的 custom_data 同型数组。
     * 非原版隙间 / 组件不存在返回 null（调用方回落到 custom_data）。
     */
    @Nullable
    private static byte[] componentLockBytes(ItemStack stack) {
        List<?> raw = TlmIoComponents.slotConfig(stack);
        if (raw == null) {
            return null;
        }
        byte[] out = new byte[raw.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) (Boolean.TRUE.equals(raw.get(i)) ? 1 : 0);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 拿取数量（存在隙间物品上：终端点击与女仆每轮搬运共用同一个上限）
    // ------------------------------------------------------------------

    /** 未设置时默认 64（整组，等同原版女仆搬运行为）。 */
    public static int pickupAmount(ItemStack stack) {
        CompoundTag tag = StackData.get(stack);
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
        StackData.update(stack, t -> t.putInt(K_PICKUP, next));
    }

    // ------------------------------------------------------------------
    // 原版女仆槽位锁定（只读兼容：原版 GUI 锁住的槽不参与 箱子->女仆）
    // ------------------------------------------------------------------

    public static boolean[] slotLocks(ItemStack stack, int maidSlots) {
        // 1.21.1 原版 GUI 只写组件：先读组件，再回落 custom_data。
        byte[] raw = componentLockBytes(stack);
        if (raw == null) {
            CompoundTag tag = StackData.get(stack);
            // 迁移标记已立的原版隙间不再回落（N1），同 slotLockBytes；
            // R2：轨存活判定 + 「携带但读不出不回退」同样生效。
            if (tag == null
                    || TlmIoComponents.carries(stack, TlmIoComponents.ID_SLOT_CONFIG)
                    || migrationBlocks(tag, stack, TlmIoComponents.ID_SLOT_CONFIG)
                    || !tag.contains(K_SLOT_CONFIG, Tag.TAG_BYTE_ARRAY)) {
                return null;
            }
            raw = tag.getByteArray(K_SLOT_CONFIG);
        }
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
