package cn.blockforge.generated.maidbettergap;

import cn.blockforge.generated.maidbettergap.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
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

    public GeneratedMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        // 升级迁移：抢在 Forge 读配置之前，把旧版本配置里的 extra_gap_items 改名为 enhanced_gap_items。
        // 放晚了没用——Forge 会把不认识的键直接丢掉，老玩家登记过的隙间物品就失效了（见方法注释）。
        MaidGapConfig.migrateLegacyConfig(FMLPaths.CONFIGDIR.get());
        // 用 COMMON 而不是 SERVER：车万女仆在 common setup 阶段就会调用我们的
        // bindMaidBauble 注册饰品，SERVER 配置那时还没加载，拿不到用户的真实值。
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MaidGapConfig.SPEC, MaidGapConfig.CONFIG_FILE_NAME);
        ModItems.register(bus);
        bus.addListener(ModItems::onBuildContents);
        bus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::init);
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
            LOGGER.info("[maid_better_gap] 已对接车万女仆 {}（mods.toml 里声明 [1.0,)）",
                    tlm.get().getModInfo().getVersion());
            probeTlmBaubleApi();
        }
    }

    /**
     * 启动自检第二步：反射确认车万女仆的饰品接口还在。
     *
     * 我们是「编译桩 + 运行时对接」：如果对方改了这个签名，功能会静默停用（有兜底、不会崩服），
     * 但日志里只会在第一次搬运时出现一行看不懂的 NoSuchMethodError。这里提前探一次，
     * 启动日志里直接写清楚「能用」还是「版本对不上」，排查时一眼就能看出来。
     */
    private static void probeTlmBaubleApi() {
        try {
            Class<?> bauble = Class.forName("com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble");
            Class<?> maid = Class.forName("com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid");
            bauble.getMethod("onTick", maid, ItemStack.class);
            Class<?> manager = Class.forName("com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager");
            manager.getMethod("bind", Item.class, bauble);
            LOGGER.info("[maid_better_gap] 车万女仆饰品接口自检通过：女仆自动搬运可用");
        } catch (LinkageError | ReflectiveOperationException ex) {
            // 只是功能停用，不影响开服；测过 1.5.3 是匹配的，跑到这里说明版本变了
            LOGGER.warn("[maid_better_gap] 车万女仆饰品接口自检失败：女仆自动搬运会停用，"
                    + "其余功能（终端、绑定、补货）不受影响。请把本模组与车万女仆更新到互相匹配的版本。", ex);
        }
    }
}
