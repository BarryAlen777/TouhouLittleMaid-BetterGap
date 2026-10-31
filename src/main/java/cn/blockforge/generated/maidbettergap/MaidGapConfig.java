package cn.blockforge.generated.maidbettergap;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 通用配置（COMMON，注册阶段即加载，保证车万女仆注册饰品时能读到真实值）：
 * 绑定距离 / 无限距离模式 / 模组白名单 / 拿取预设 / 自动补货 / 反馈开关 / 增强原版隙间。
 */
public final class MaidGapConfig {

    public static final ForgeConfigSpec SPEC;

    // —— 绑定与距离 ——
    public static final ForgeConfigSpec.IntValue MAX_BIND_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue INFINITE_RANGE;
    public static final ForgeConfigSpec.IntValue MAX_BOUND_CONTAINERS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_FLUID_TANKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOD_WHITELIST;

    // —— 取物与补货 ——
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> PICKUP_PRESETS;
    public static final ForgeConfigSpec.BooleanValue AUTO_REFILL_ENABLED;
    public static final ForgeConfigSpec.IntValue AUTO_REFILL_THRESHOLD;
    public static final ForgeConfigSpec.IntValue AUTO_REFILL_INTERVAL;

    // —— 视觉与音效反馈 ——
    public static final ForgeConfigSpec.BooleanValue SOUND_FEEDBACK;
    public static final ForgeConfigSpec.BooleanValue PARTICLE_FEEDBACK;

    // —— 增强目标（兼容原模组的隙间物品） ——
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXTRA_GAP_ITEMS;
    public static final ForgeConfigSpec.BooleanValue ENHANCE_ORIGINAL_GAP;
    public static final ForgeConfigSpec.IntValue MAID_IO_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue SOPH_BACKPACKS;

    // —— 类别匹配（按物品标签归类） ——
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TAG_MATCH_NAMESPACES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TAG_MATCH_BLACKLIST;

    // —— 隙间终端 ——
    public static final ForgeConfigSpec.IntValue TERMINAL_MAID_LIMIT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("隙间容器绑定与距离 (container binding and distance)").push("binding");
        MAX_BIND_DISTANCE = b
                .comment("可绑定容器的最大距离（方块数，1-128）。仅对同维度生效。")
                .defineInRange("max_bind_distance", 32, 1, 128);
        INFINITE_RANGE = b
                .comment("无限距离模式：无视距离上限，还可以跨维度绑定和访问容器。")
                .define("infinite_range", false);
        MAX_BOUND_CONTAINERS = b
                .comment("一个隙间最多可同时绑定的容器数量。")
                .defineInRange("max_bound_containers", 4, 1, 8);
        ALLOW_FLUID_TANKS = b
                .comment("允许绑定流体储罐并抽取液体（每次点击取 1 桶 = 1000 mB）。")
                .define("allow_fluid_tanks", true);
        MOD_WHITELIST = b
                .comment("可绑定容器的模组白名单。留空 = 允许所有模组。例如: [\"sophisticatedstorage\", \"mekanism\"]")
                .defineListAllowEmpty("mod_whitelist", () -> List.<String>of(),
                        o -> o instanceof String s && !s.isBlank());
        b.pop();

        b.comment("取物与自动补货 (pickup and auto refill)").push("logistics");
        PICKUP_PRESETS = b
                .comment("预设拿取数量列表，终端界面里点击按钮循环切换。")
                .defineListAllowEmpty("pickup_presets", () -> List.of(1, 8, 16, 32, 64),
                        o -> o instanceof Integer i && i >= 1 && i <= 64);
        AUTO_REFILL_ENABLED = b
                .comment("自动补货模式的总开关（玩家端仍需在界面里单独开启）。")
                .define("auto_refill_enabled", true);
        AUTO_REFILL_THRESHOLD = b
                .comment("背包物品数量低于或等于这个值时，视为“快耗尽”，自动从绑定容器补充。")
                .defineInRange("auto_refill_threshold", 8, 1, 64);
        AUTO_REFILL_INTERVAL = b
                .comment("自动补货扫描间隔（游戏刻，20 tick = 1 秒）。")
                .defineInRange("auto_refill_interval_ticks", 40, 10, 600);
        b.pop();

        b.comment("视觉与音效反馈（沿用原版隙间的传送风格）").push("feedback");
        SOUND_FEEDBACK = b.comment("绑定 / 解绑 / 取物时播放原版音效。").define("sounds", true);
        PARTICLE_FEEDBACK = b.comment("绑定 / 解绑 / 取物时显示原版传送粒子。").define("particles", true);
        b.pop();

        b.comment("增强目标：把原模组的隙间物品也变成增强终端").push("compat");
        ENHANCE_ORIGINAL_GAP = b
                .comment("直接增强车万女仆原版隙间 (touhou_little_maid:wireless_io)：",
                        "用本模组的增强逻辑替换原版饰品逻辑（多容器绑定、可调距离、跨维度、传送反馈），",
                        "原版 GUI 与 NBT 数据完全兼容。关掉则原版隙间保持原样。")
                .define("enhance_original_gap", true);
        MAID_IO_INTERVAL = b
                .comment("女仆戴着隙间自动搬运的间隔（游戏刻，20 tick = 1 秒；原版是 100）。")
                .defineInRange("maid_io_interval_ticks", 100, 20, 600);
        SOPH_BACKPACKS = b
                .comment("把女仆身上「精妙背包」里的物品也算进自动搬运（需要同时装「女仆精妙」maid_sophisticated",
                        "和「精妙背包」sophisticatedbackpacks，缺任何一个就自动跳过，不会报错）。",
                        "开启后：女仆->箱子 会把背包里的东西也送进容器；",
                        "箱子->女仆 会先塞满女仆自己的背包，塞不下的才溢出进精妙背包。",
                        "注意：槽位锁定只管女仆自己的 38 格，不约束精妙背包的内容物；",
                        "想让女仆完全停搬，关掉总开关或把搬运方向切走。")
                .define("sophisticated_backpacks", true);
        EXTRA_GAP_ITEMS = b
                .comment("这些物品 ID 也会被当作“隙间”：可进女仆饰品栏、潜行+右键绑定、按 G 打开增强终端。",
                        "默认包含车万女仆原版隙间。例如: [\"touhou_little_maid:wireless_io\", \"some_maid_mod:maid_gap\"]")
                .defineListAllowEmpty("enhanced_gap_items",
                        () -> List.of("touhou_little_maid:wireless_io"),
                        o -> o instanceof String s && s.indexOf(':') > 0);
        b.pop();

        b.comment("类别匹配（按物品标签归类）").push("tag_match");
        TAG_MATCH_NAMESPACES = b
                .comment("哪些命名空间的物品标签参与类别匹配。留空 = 全部参与。",
                        "默认只认原版与通用约定标签，避免别的模组的私有标签造成意外归类；",
                        "需要时往里加即可，例如 [\"minecraft\", \"forge\", \"c\", \"some_furniture_mod\"]。")
                .defineListAllowEmpty("tag_match_namespaces", () -> List.of("minecraft", "forge", "c"),
                        o -> o instanceof String s && !s.isBlank());
        TAG_MATCH_BLACKLIST = b
                .comment("不参与类别匹配的标签 ID。默认排除\"行为用途\"标签",
                        "（它们把不相干的物品凑在一起，比如\"能破坏装饰罐的物品\"= 斧头+锻造模板）。")
                .defineListAllowEmpty("tag_match_blacklist",
                        () -> List.of(
                                "minecraft:completes_find_tree_tutorial",
                                "minecraft:cluster_max_harvestables",
                                "minecraft:breaks_decorated_pots",
                                "minecraft:beacon_payment_items",
                                "minecraft:smelts_to_glass",
                                "minecraft:dampens_vibrations",
                                "minecraft:axolotl_tempt_items",
                                "minecraft:fox_food",
                                "minecraft:piglin_food",
                                "minecraft:piglin_loved",
                                "minecraft:piglin_repellents",
                                "minecraft:ignored_by_piglin_babies",
                                "minecraft:sniffer_food",
                                "minecraft:villager_plantable_seeds",
                                "minecraft:freeze_immune_wearables",
                                "minecraft:creeper_igniters",
                                "minecraft:creeper_drop_music_discs",
                                "minecraft:tradeable",
                                "minecraft:enchantable/armor",
                                "minecraft:enchantable/crossbow",
                                "minecraft:enchantable/durability",
                                "minecraft:enchantable/equippable",
                                "minecraft:enchantable/fishing",
                                "minecraft:enchantable/weapon",
                                "forge:spawn_egg"),
                        o -> o instanceof String s && s.indexOf(':') > 0);
        b.pop();

        b.comment("隙间终端 (gap terminal)").push("terminal");
        TERMINAL_MAID_LIMIT = b
                .comment("一个隙间终端最多能链接多少只女仆（终端界面左侧列表的上限）。",
                        "女仆多了就调大这个值；界面里最多一屏显示 16 只，多出来的用滚轮上下翻。")
                .defineInRange("terminal_maid_limit", 16, 1, 64);
        b.pop();

        SPEC = b.build();
    }

    private MaidGapConfig() {}

    public static List<Integer> pickupPresets() {
        List<Integer> out = new ArrayList<>();
        List<? extends Integer> raw = PICKUP_PRESETS.get();
        if (raw != null) {
            for (Integer i : raw) {
                if (i != null && i > 0) out.add(i);
            }
        }
        if (out.isEmpty()) out.add(64);
        return out;
    }

    public static int pickupAmount(int index) {
        List<Integer> presets = pickupPresets();
        return presets.get(Math.floorMod(index, presets.size()));
    }

    /** 模组白名单检查：空名单 = 全部允许。 */
    public static boolean modAllowed(String namespace) {
        List<? extends String> wl = MOD_WHITELIST.get();
        if (wl == null || wl.isEmpty()) return true;
        for (String s : wl) {
            if (s != null && s.toLowerCase(Locale.ROOT).equals(namespace.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
