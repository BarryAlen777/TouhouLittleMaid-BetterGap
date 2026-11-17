package cn.blockforge.generated.maidbettergap;

import cn.blockforge.generated.maidbettergap.gap.TlmIoComponents;
import cn.blockforge.generated.maidbettergap.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * 车万女仆：更好的隙间（Touhou Little Maid: Better Gap）—— 让隙间成为可跨模组、可配置、带可视化反馈的智能物流终端。
 *
 * 融合的功能（按车万女仆原版隙间的架构实现：绑定存在隙间物品的 NBT 上）：
 * - 女仆饰品：增强终端通过 @LittleMaidExtension 注册为女仆饰品，可放进饰品栏；
 *   女仆佩戴后按「箱子→女仆 / 女仆→箱子」方向在绑定容器间自动搬运
 * - 直接增强原版隙间：默认把 touhou_little_maid:wireless_io 的饰品逻辑替换为增强版
 *   （原版 GUI、NBT、槽位锁定全部兼容，配置可关）
 * - 物品白名单/黑名单：9 格过滤名单（与原版同格式），终端界面里 Shift+左键即加
 * - 跨模组容器绑定：任何暴露 IItemHandler 的方块（精妙存储等）都能绑
 * - 液体储罐绑定：暴露 IFluidHandler 的储罐可绑定并抽桶（玩家终端侧）
 * - 模组白名单：配置里限定可绑定的模组 ID
 * - 绑定距离：默认 32 格、可调至 128 格；无限距离模式支持跨维度（女仆搬运同样生效）
 * - 多容器绑定：默认 4 个、可调到 8 个（原版只有 1 个）
 * - 拿取数量：预设列表可自由配置，界面里一键切换
 * - 智能物品筛选：武器/工具/食物/方块/装备/其他分类标签页
 * - 视觉与音效反馈：沿用原版隙间的传送粒子与末影音效
 * - 玩家自动补货：背包物品快耗尽时自动从绑定容器补充
 */
@Mod(GeneratedMod.MOD_ID)
public final class GeneratedMod {

    public static final String MOD_ID = "maid_better_gap";

    public static final Logger LOGGER = LogUtils.getLogger();

    public GeneratedMod(IEventBus bus) {
        // 升级迁移：抢在加载器读配置之前，把旧版本配置里的 extra_gap_items 改名为 enhanced_gap_items。
        // 放晚了没用——加载器会把不认识的键直接丢掉，老玩家登记过的隙间物品就失效了（见方法注释）。
        MaidGapConfig.migrateLegacyConfig(FMLPaths.CONFIGDIR.get());
        // 用 COMMON 而不是 SERVER：车万女仆在 common setup 阶段就会调用我们的
        // bindMaidBauble 注册饰品，SERVER 配置那时还没加载，拿不到用户的真实值。
        ModLoadingContext.get().getActiveContainer()
                .registerConfig(ModConfig.Type.COMMON, MaidGapConfig.SPEC, MaidGapConfig.CONFIG_FILE_NAME);
        ModItems.register(bus);
        bus.addListener(ModItems::onBuildContents);
        bus.addListener(ModNetwork::register);
        bus.addListener(this::commonSetup);
        // 【P7】按键注册不再在这里显式挂 GapClientSetup（客户端类不该出现在通用主类里）：
        // GapClientSetup 自带 @EventBusSubscriber(value = Dist.CLIENT, bus = MOD)，
        // 扫描器按侧别决定加不加载这个类——专用服务器根本不会触碰它，比手写 dist 判断更干净。
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // 【P6】此刻所有注册（含车万女仆的 DataComponentType）都已冻结：
        // 清掉 TlmIoComponents 的“组件查不到”负缓存，让第一次游戏内查询重新走注册表。
        // 没有这一句，若某次查询抢在对方注册之前发生，“没有”就会被永久记死。
        TlmIoComponents.onRegistriesFrozen();
        event.enqueueWork(GeneratedMod::logDependencies);
    }

    /**
     * 启动自检：把前置版本打进日志。
     * 我们和车万女仆之间是「编译桩 + 运行时反射式对接」的关系，版本一变就可能对接不上，
     * 出了问题（终端没反应、女仆不搬东西）先看这一行就能判断是不是版本的事。
     */
    private static void logDependencies() {
        ModList list = ModList.get();
        if (list == null) {
            return;
        }
        var tlm = list.getModContainerById("touhou_little_maid");
        if (tlm.isEmpty()) {
            LOGGER.info("[maid_better_gap] 未检测到车万女仆：终端与女仆饰品功能不可用，其余功能正常");
        } else {
            LOGGER.info("[maid_better_gap] 已对接车万女仆 {}（mods.toml 里声明 [1.5,)，实测 1.5.0/1.5.2/1.5.3）",
                    tlm.get().getModInfo().getVersion());
            probeTlmBaubleApi();
        }
    }

    /**
     * 启动自检第二步：反射确认车万女仆的对接面全部还在。
     *
     * 我们是「编译桩 + 运行时对接」：如果对方改了这些签名，功能会静默停用（有兜底、不会崩服），
     * 但日志里只会在第一次搬运时出现一行看不懂的 NoSuchMethodError。这里提前逐项探测，
     * 启动日志里直接写清楚「能用」还是「哪一项对不上」。
     *
     * 审查报告 P7：只查 onTick/bind 会给「接口自检通过」的假安全感——
     * 真正容易先漂移的是 EntityMaid 上的 getMaidBauble / getAvailableInv / guiOpening，
     * 所以它们必须一起查（字段查类型、方法查返回类型，缺一都算自检失败）。
     */
    private static void probeTlmBaubleApi() {
        java.util.List<String> missing = new java.util.ArrayList<>();
        try {
            Class<?> bauble = Class.forName("com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble");
            bauble.getMethod("onTick",
                    Class.forName("com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid"),
                    ItemStack.class);
            Class<?> manager = Class.forName("com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager");
            manager.getMethod("bind", Item.class, bauble);

            Class<?> maid = Class.forName("com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid");
            Class<?> handler = Class.forName(
                    "com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler");
            probeMethod(missing, maid, "getMaidBauble", handler);
            probeMethod(missing, maid, "getAvailableInv",
                    net.neoforged.neoforge.items.wrapper.CombinedInvWrapper.class, boolean.class);
            probeMethod(missing, maid, "getRestrictRadius", float.class);
            probeField(missing, maid, "guiOpening", boolean.class);
        } catch (LinkageError | ReflectiveOperationException ex) {
            // 类本身都摸不到：整体失败
            missing.add("（车万女仆核心类加载失败：" + ex.getClass().getSimpleName() + "）");
        }
        // 【P5】五个数据组件在不在也一并查（注册表此时已冻结，查得到的才是真状态）。
        // 组件缺失不影响本模组自身运转（自动退回纯 custom_data 单轨），
        // 只影响与原版隙间 GUI 的数据互通，所以单独一行提示、不算接口自检失败。
        java.util.List<String> noComp = TlmIoComponents.missingComponentIds();
        if (!noComp.isEmpty()) {
            LOGGER.warn("[maid_better_gap] 车万女仆缺少数据组件：{}。与原版隙间 GUI 的互通降级为"
                    + "单轨（只用自带 custom_data），女仆搬运不受影响。", String.join("、", noComp));
        }
        if (missing.isEmpty()) {
            // R4（r50 复审）：组件缺着的时候不能再报「对接面完整」——上一行刚 warning 过缺组件，
            // 这一行却说完整，日志自相矛盾，容易让人以为互通没问题。如实分开说两轨的状态。
            if (noComp.isEmpty()) {
                LOGGER.info("[maid_better_gap] 车万女仆饰品接口自检通过：终端、自动搬运、槽位锁定的对接面完整");
            } else {
                LOGGER.info("[maid_better_gap] 车万女仆饰品接口自检通过（终端、自动搬运、槽位锁定）；"
                        + "与原版隙间 GUI 的数据组件互通降级，缺：{}", String.join("、", noComp));
            }
            return;
        }
        // 只是功能停用，不影响开服；测过 1.5.0/1.5.2/1.5.3 是匹配的，跑到这里说明版本变了
        LOGGER.warn("[maid_better_gap] 车万女仆饰品接口自检失败：缺 {}。女仆自动搬运等相关功能会停用，"
                + "其余功能（终端、绑定、补货）不受影响。请把本模组与车万女仆更新到互相匹配的版本。",
                String.join("、", missing));
    }

    /** 探一个方法（名字 + 参数 + 返回类型都要对上）。返回类型对不上单独报，别只说「找不到」。 */
    private static void probeMethod(java.util.List<String> missing, Class<?> owner,
                                    String name, Class<?> returnType, Class<?>... params) {
        try {
            var m = owner.getMethod(name, params);
            if (!returnType.isAssignableFrom(m.getReturnType())) {
                missing.add(name + "() 返回类型变成了 " + m.getReturnType().getSimpleName());
            }
        } catch (NoSuchMethodException ex) {
            missing.add(name + "(" + params.length + " 参)");
        }
    }

    /** 探一个公开字段（guiOpening 这种直接字段访问最容易在重构里改名/转方法）。 */
    private static void probeField(java.util.List<String> missing, Class<?> owner, String name, Class<?> type) {
        try {
            var f = owner.getField(name);
            if (f.getType() != type) {
                missing.add(name + " 字段类型变成了 " + f.getType().getSimpleName());
            }
        } catch (NoSuchFieldException ex) {
            missing.add(name + " 字段");
        }
    }
}
