package cn.blockforge.generated.maidbettergap;

import cn.blockforge.generated.maidbettergap.network.ModNetwork;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

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

    public GeneratedMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        // 用 COMMON 而不是 SERVER：车万女仆在 common setup 阶段就会调用我们的
        // bindMaidBauble 注册饰品，SERVER 配置那时还没加载，拿不到用户的真实值。
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MaidGapConfig.SPEC, "maid_gap_enhanced-common.toml");
        ModItems.register(bus);
        bus.addListener(ModItems::onBuildContents);
        bus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::init);
    }
}
