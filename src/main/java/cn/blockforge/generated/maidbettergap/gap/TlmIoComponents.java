package cn.blockforge.generated.maidbettergap.gap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 车万女仆 1.21.1 原版隙间（touhou_little_maid:wireless_io）的数据组件桥。
 *
 * 【为什么需要它】1.20.1 时代原版隙间把所有设置写在物品 NBT（tag）里：
 * BindingPos / ItemIOMode / ItemFilterMode / ItemFilterList / SlotConfigData。
 * 我们（更好的隙间）就是按这套键做兼容的。但车万女仆 1.21.1（1.5.0~1.5.3 源码核实）
 * 把这些全部迁进了**独立的自定义数据组件**（touhou_little_maid:binding_pos 等五个），
 * 不再碰 custom_data——照旧只读写 custom_data 的话，原版 GUI 写的东西我们看不见，
 * 我们写的东西原版 GUI 也看不见，「增强原版隙间」就名存实亡（审查报告 P5，已证实）。
 *
 * 【怎么做到不依赖车万女仆】只按组件 ID 从原版注册表
 * {@link BuiltInRegistries#DATA_COMPONENT_TYPE} 查 DataComponentType，
 * 再用 ItemStack 的通用 get/set 读写——全程不引用对方的任何类。
 * 没装车万女仆（或版本太老没有这些组件）时注册表查不到，读返回 null、写静默跳过，
 * 一切自动退回「只用 custom_data」的旧行为，不会崩。
 * 读只认物品自己 patch 里<b>有记录</b>的组件值（审查报告 P4 → r50 复审 R3 升级：
 * {@code ItemStack.has()} 会回落到 prototype——物品注册时带的默认组件——只要车万女仆
 * 给某个组件注册了默认值它就恒真，"没写过"会被误判成"用户写过"，旧存档的 custom_data
 * 被凭空遮死。改用 {@code getComponentsPatch()} 的三态判定，见 {@link #get}。）；
 * 写之前按组件自己的 codec 试编校验载荷类型（审查报告 P5），对不上就跳过 + 警告一次。
 * 「查不到」的负缓存在注册表冻结后（common setup）会清一次重查，防止抢跑误判（P6）。
 *
 * 【patch 三态（R3/R2 的统一口径）】{@code patch.get(type)} 的三种返回值：
 * <ul>
 *   <li>{@code null}（无记录）—— 从没被写过。值就算存在也是物品默认，不算用户设置：
 *       custom_data 回退、旧值提升都照常允许。边角：写入的值恰好等于默认时原版会把
 *       patch 记录合并掉——无害，因为我们每次写组件都同步写 custom_data 镜像，
 *       两边同值；但别把它当 bug「修」回去；</li>
 *   <li>{@code Optional.of(v)} —— 写过值（原版 GUI 或我们）：组件是唯一真相，不回退、不提升；</li>
 *   <li>{@code Optional.empty()} —— 被显式删除（原版 GUI 解绑/清空）：同样是"一手真相=没有"，
 *       必须挡住回退与提升，否则玩家刚清空的东西会被旧镜像复活（R2 的另一面）。</li>
 * </ul>
 * {@link #carries} 问「有没有记录」（of / empty 都算），{@link #get} 只认 of。
 *
 * 【对哪些物品生效】只对 touhou_little_maid:wireless_io。我们自己注册的隙间物品、
 * 以及配置里额外登记的第三方隙间，都只走 custom_data，一个字节的组件都不碰。
 *
 * 【五个组件（与 1.5.3 的 InitDataComponent 一一对应）】
 * <ul>
 *   <li>{@code binding_pos}      —— BlockPos，原版单容器绑定（不带维度）</li>
 *   <li>{@code item_io_mode}     —— Boolean，true = 女仆-&gt;箱子</li>
 *   <li>{@code item_filter_mode} —— Boolean，true = 黑名单</li>
 *   <li>{@code item_filter_list} —— CompoundTag，9 格名单（ItemStackHandler 序列化格式）</li>
 *   <li>{@code slot_config_data} —— List(Boolean) 38 项，女仆槽位锁定</li>
 * </ul>
 * 五个组件都声明了 networkSynchronized 编解码器，原版会自动随物品发到客户端，
 * 所以客户端 tooltip / GUI 与服务端读到的是同一份数据，这里两侧都能安全读写。
 */
public final class TlmIoComponents {

    private static final String TLM_NS = "touhou_little_maid";
    private static final ResourceLocation WIRELESS_IO =
            ResourceLocation.fromNamespaceAndPath(TLM_NS, "wireless_io");

    static final String ID_BINDING_POS = TLM_NS + ":binding_pos";
    static final String ID_IO_MODE = TLM_NS + ":item_io_mode";
    static final String ID_FILTER_MODE = TLM_NS + ":item_filter_mode";
    static final String ID_FILTER_LIST = TLM_NS + ":item_filter_list";
    static final String ID_SLOT_CONFIG = TLM_NS + ":slot_config_data";

    /** 组件类型查一次缓存一次；查不到的记进 ABSENT，避免每件物品都再打一次注册表。 */
    private static final Map<String, DataComponentType<Object>> TYPES = new ConcurrentHashMap<>();
    private static final java.util.Set<String> ABSENT = ConcurrentHashMap.newKeySet();

    /**
     * 组件轨「判定性死亡」名单：codec 拒收过我们的载荷（版本变了、载荷类型对不上）。
     * 同一 (codec, 载荷类型) 对的拒绝是确定性的，永久成立，同时只警告一次（审查报告 P5）。
     * R2：这份判定还决定 custom_data 回退能不能重新打开——轨都写不进去，
     * 迁移标记就不再有权关闭回退（详见 GapItemData#legacyFallbackOpen）。
     */
    private static final java.util.Set<String> REJECTED = ConcurrentHashMap.newKeySet();

    /** 「组件携带着、但解码不出我们预期的类型」也只警告一次（R4：读路径的静默失明）。 */
    private static final java.util.Set<String> READ_MISMATCH_WARNED = ConcurrentHashMap.newKeySet();

    /** 五个组件的注册 ID（启动自检查用，见 GeneratedMod#probeTlmBaubleApi）。 */
    public static final String[] COMPONENT_IDS = {
            ID_BINDING_POS, ID_IO_MODE, ID_FILTER_MODE, ID_FILTER_LIST, ID_SLOT_CONFIG};

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private TlmIoComponents() {}

    /**
     * 注册表冻结后清一次「查不到」负缓存（审查报告 P6）。
     * 首次查询若早于车万女仆的组件注册，ABSENT 会把“没有”永久记死；
     * 加载期结束（common setup）时所有 DataComponentType 必已注册完毕，
     * 这里清掉重来，之后缓存的就是真实状态。找到的正向缓存不用清。
     */
    public static void onRegistriesFrozen() {
        ABSENT.clear();
    }

    /** 车万女仆装了但某些组件查不到（版本太老）时给启动自检列出来（审查报告 P5）。 */
    public static List<String> missingComponentIds() {
        List<String> out = new ArrayList<>();
        for (String id : COMPONENT_IDS) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null || BuiltInRegistries.DATA_COMPONENT_TYPE.get(rl) == null) {
                out.add(id);
            }
        }
        return out;
    }

    /** 手里这件是不是车万女仆的原版隙间（只有它需要组件桥）。 */
    public static boolean isOriginalGap(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return WIRELESS_IO.equals(key);
    }

    /** 组件类型解析（车万女仆没装 / 版本太老 → null，读写自动降级为纯 custom_data）。 */
    @Nullable
    @SuppressWarnings("unchecked")
    private static DataComponentType<Object> type(String id) {
        if (ABSENT.contains(id)) {
            return null;
        }
        DataComponentType<Object> cached = TYPES.get(id);
        if (cached != null) {
            return cached;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Object found = rl == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.get(rl);
        if (found == null) {
            // 注册表里没有这个组件：记一笔，后面不再重复查（没装前置时这是常态路径）
            ABSENT.add(id);
            return null;
        }
        DataComponentType<Object> t = (DataComponentType<Object>) found;
        TYPES.put(id, t);
        return t;
    }

    // ------------------------------------------------------------------
    // 读（物品不是原版隙间、或组件不存在时一律 null，调用方回落到 custom_data）
    // ------------------------------------------------------------------

    @Nullable
    public static BlockPos bindingPos(ItemStack stack) {
        return isOriginalGap(stack) ? get(stack, ID_BINDING_POS, BlockPos.class) : null;
    }

    @Nullable
    public static Boolean ioMode(ItemStack stack) {
        return isOriginalGap(stack) ? get(stack, ID_IO_MODE, Boolean.class) : null;
    }

    @Nullable
    public static Boolean filterMode(ItemStack stack) {
        return isOriginalGap(stack) ? get(stack, ID_FILTER_MODE, Boolean.class) : null;
    }

    @Nullable
    public static CompoundTag filterList(ItemStack stack) {
        return isOriginalGap(stack) ? get(stack, ID_FILTER_LIST, CompoundTag.class) : null;
    }

    /** 槽位锁定的原始组件值（List&lt;Boolean&gt;，原版 GUI 存 38 项）；没有返回 null。 */
    @Nullable
    public static List<?> slotConfig(ItemStack stack) {
        return isOriginalGap(stack) ? get(stack, ID_SLOT_CONFIG, List.class) : null;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static <X> X get(ItemStack stack, String id, Class<X> expected) {
        DataComponentType<Object> t = type(id);
        if (t == null) {
            return null;
        }
        // 【R3，取代旧 P4 的 has() 判定】只读物品自己的 patch，不碰 prototype（物品默认组件表）。
        // ItemStack.has → getComponents().has → PatchedDataComponentMap.get 在无记录时回落到
        // prototype：车万女仆只要给这五个组件注册了默认值，has() 就恒为 true，"从没写过"
        // 会被当成"用户写过"——默认值冒充用户设置，旧存档 custom_data 里的真值被静默遮死，
        // 且迁移标记照样立起、回退全关（复审 r50 §2.1/2.2，字节码坐实）。
        // DataComponentPatch.get 的三态才是要的语义：
        //   null             → patch 无记录（从没写过，或写过又恰好等于默认被合并掉）→ 当作没有，
        //                      回退/提升由调用方按 carries()/migrationBlocks() 决定；
        //   Optional.of(v)   → 写过值 → 唯一真相；
        //   Optional.empty() → 被显式删除（原版 GUI 解绑/清空）→ 同样"无值"，
        //                      但 carries() 认它，回退口会关上（R2：这是玩家清空的，不该复活）。
        Optional<?> state = stack.getComponentsPatch().get(t);
        if (state == null || state.isEmpty()) {
            return null;
        }
        Object v = state.get();
        // 类型对不上（对方改了组件的载荷类型）：宁可当作没有，也不 ClassCastException。
        // R4：这条「携带着但读不出来」的分支原来是纯静默的——组件明明有值、我们永远
        // 当作缺失，互通悄悄失明。每个组件只警告一次，出问题时日志里能一眼看到。
        if (!expected.isInstance(v)) {
            if (READ_MISMATCH_WARNED.add(id)) {
                LOGGER.warn("[maid_better_gap] 车万女仆组件 {} 的载荷是 {}，不是本模组预期的 {}——"
                        + "按「组件无值」处理（与原版 GUI 的互通在这一点上失明，请检查版本匹配）。",
                        id, v == null ? "null" : v.getClass().getSimpleName(), expected.getSimpleName());
            }
            return null;
        }
        // R6：CompoundTag/List 是组件表里的活对象，直接递出去等于把可变的内部状态
        // 交给了调用方（现在的所有调用点都只读或先 copy，但接口不该指望调用方守规矩）。
        if (v instanceof CompoundTag ct) {
            return (X) ct.copy();
        }
        if (v instanceof List<?> l) {
            return (X) new ArrayList<>(l);
        }
        return (X) v;
    }

    // ------------------------------------------------------------------
    // 写（同样只对原版隙间生效；传 null = 清掉组件；返回是否真的落盘）
    // ------------------------------------------------------------------

    /**
     * 组件轨上这一路现在还存不存在：类型能在注册表里查到 = true。
     * R2：迁移标记只有关到「存在组件轨」的物品才成立——前置被卸载/降级导致组件
     * 类型整个消失时，custom_data 是唯一还能读到的轨，回退口必须重新打开（自愈）。
     */
    public static boolean isTrackRegistered(String id) {
        return type(id) != null;
    }

    /**
     * 组件轨在这一路上是否已经「接管」——R3 口径：物品 patch 里有<b>任何记录</b>就算，
     * 不问我们能不能解码，也分不管是指向值还是显式删除的墓碑：
     * <ul>
     *   <li>带值（原版 GUI 或我们写过）：ItemStack.set 是无条件覆盖写，拿旧镜像去盖它就是盖真数据；</li>
     *   <li>显式删除（原版 GUI 解绑/清空）：「没有」本身就是玩家的一手决定，
     *       放行回退/提升等于替玩家撤销清空（R2 的另一半——旧版靠 has() 认不出墓碑，
     *       全靠迁移标记兜，标记立之前有一段空窗）。</li>
     * </ul>
     * 无记录（含"写过又恰好等于物品默认值、被原版合并掉"的边角）= 从没写过：
     * 回退与提升照常允许。我们的每次组件写入都同步写 custom_data 镜像，两边同值，
     * 合并掉记录不会让回退读到错数据。
     */
    public static boolean carries(ItemStack stack, String id) {
        if (!isOriginalGap(stack)) {
            return false;
        }
        DataComponentType<Object> t = type(id);
        return t != null && stack.getComponentsPatch().get(t) != null;
    }

    public static boolean setBindingPos(ItemStack stack, @Nullable BlockPos pos) {
        return isOriginalGap(stack) && put(stack, ID_BINDING_POS, pos);
    }

    public static boolean setIoMode(ItemStack stack, boolean value) {
        return isOriginalGap(stack) && put(stack, ID_IO_MODE, value);
    }

    public static boolean setFilterMode(ItemStack stack, boolean value) {
        return isOriginalGap(stack) && put(stack, ID_FILTER_MODE, value);
    }

    public static boolean setFilterList(ItemStack stack, @Nullable CompoundTag handlerTag) {
        return isOriginalGap(stack) && put(stack, ID_FILTER_LIST, handlerTag);
    }

    public static boolean setSlotLocks(ItemStack stack, boolean[] locks) {
        if (!isOriginalGap(stack)) {
            return false;
        }
        List<Boolean> out = new ArrayList<>(locks.length);
        for (boolean b : locks) {
            out.add(b);
        }
        return put(stack, ID_SLOT_CONFIG, out);
    }

    /**
     * 名单组件是否携带着一份解得出的值（掉落物净化早退用，GapInteractions）。
     * R3 后无记录/墓碑都返回 false——它们都意味着「组件轨上没有名单」；
     * 旧存档物品照样会被 custom_data 一路接走（这里只是省一次全量扫描的快捷判定）。
     * N2（r50 复审）：同族的 hasBindingPos / hasIoMode / hasFilterMode / hasSlotConfig
     * 已被 carries() + 直接读值全面替代且无调用点，删除。
     */
    public static boolean hasFilterList(ItemStack stack) {
        return isOriginalGap(stack) && filterList(stack) != null;
    }

    /**
     * 写一次组件轨，返回是否真正落盘（R1）：
     * 类型查不到（前置没装/太老）或 codec 拒收载荷 → false，调用方必须把它当失败，
     * 不能继续当作「写完了」——一次性迁移的完成标记就是靠这里成败来立的。
     */
    private static boolean put(ItemStack stack, String id, @Nullable Object value) {
        DataComponentType<Object> t = type(id);
        if (t == null) {
            return false;
        }
        if (value == null) {
            stack.remove(t);
            return true;
        }
        // 【P5】写入前先用组件自己的 codec 试编一次。载荷类型不匹配（车万女仆将来把
        // CompoundTag 换成别的记录类型）如果放到这里不管，真正的爆炸点是下一次
        // 存盘 / 发同步包——那已经是“物品带着坏数据走天下”之后，可能崩档也可能踢人。
        // 试编失败就跳过这次写入并只警告一次：最多是原版 GUI 看不到我们这一笔改动，
        // 我们自己的 custom_data 轨照常完整工作。
        try {
            var codec = t.codec();
            if (codec != null && codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, value).result().isEmpty()) {
                if (REJECTED.add(id)) {
                    LOGGER.warn("[maid_better_gap] 车万女仆组件 {} 不接受我们写入的载荷类型 {}（对方版本变了？）。"
                            + "已跳过组件轨，本模组改用自带的 custom_data 继续工作。", id,
                            value.getClass().getSimpleName());
                }
                return false;
            }
        } catch (RuntimeException | LinkageError ex) {
            if (REJECTED.add(id)) {
                LOGGER.warn("[maid_better_gap] 校验车万女仆组件 {} 的载荷时抛异常，已跳过组件轨，"
                        + "本模组改用自带的 custom_data 继续工作。", id, ex);
            }
            return false;
        }
        stack.set(t, value);
        return true;
    }
}
