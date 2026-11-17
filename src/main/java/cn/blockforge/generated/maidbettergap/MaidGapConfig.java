package cn.blockforge.generated.maidbettergap;

import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 通用配置（COMMON，注册阶段即加载，保证车万女仆注册饰品时能读到真实值）：
 * 绑定距离 / 无限距离模式 / 模组白名单 / 拿取预设 / 自动补货 / 反馈开关 / 增强原版隙间。
 */
public final class MaidGapConfig {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 配置文件在 config 目录里的名字（和 GeneratedMod 注册时保持一致）。 */
    public static final String CONFIG_FILE_NAME = "maid_gap_enhanced-common.toml";
    /** 现在的键名：哪些物品也算「隙间」。 */
    private static final String EXTRA_KEY = "enhanced_gap_items";
    /** 1.3.1 及以前的旧键名（升级时自动改名，不能让老配置里的物品悄悄失效）。 */
    private static final String LEGACY_EXTRA_KEY = "extra_gap_items";

    public static final ModConfigSpec SPEC;

    // —— 绑定与距离 ——
    public static final ModConfigSpec.IntValue MAX_BIND_DISTANCE;
    public static final ModConfigSpec.BooleanValue INFINITE_RANGE;
    public static final ModConfigSpec.IntValue MAX_BOUND_CONTAINERS;
    public static final ModConfigSpec.BooleanValue ALLOW_FLUID_TANKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MOD_WHITELIST;

    // —— 取物与补货 ——
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> PICKUP_PRESETS;
    public static final ModConfigSpec.BooleanValue AUTO_REFILL_ENABLED;
    public static final ModConfigSpec.IntValue AUTO_REFILL_THRESHOLD;
    public static final ModConfigSpec.IntValue AUTO_REFILL_INTERVAL;

    // —— 视觉与音效反馈 ——
    public static final ModConfigSpec.BooleanValue SOUND_FEEDBACK;
    public static final ModConfigSpec.BooleanValue PARTICLE_FEEDBACK;

    // —— 增强目标（兼容原模组的隙间物品） ——
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXTRA_GAP_ITEMS;
    public static final ModConfigSpec.BooleanValue ENHANCE_ORIGINAL_GAP;
    public static final ModConfigSpec.IntValue MAID_IO_INTERVAL;
    public static final ModConfigSpec.BooleanValue SOPH_BACKPACKS;

    // —— 类别匹配（按物品标签归类） ——
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TAG_MATCH_NAMESPACES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> TAG_MATCH_BLACKLIST;

    // —— 隙间终端 ——
    public static final ModConfigSpec.IntValue TERMINAL_MAID_LIMIT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

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
                        "默认包含车万女仆原版隙间。例如: [\"touhou_little_maid:wireless_io\", \"some_maid_mod:maid_gap\"]",
                        "（旧版本的键名是 extra_gap_items；升级时会自动改名迁移，不用手动搬。）")
                .defineListAllowEmpty(EXTRA_KEY,
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

    /**
     * 升级迁移：把老配置里的 {@code extra_gap_items} 原地改名成 {@code enhanced_gap_items}。
     *
     * 为什么必须在「配置被读取之前」动文件：Forge 读配置时只认 SPEC 里定义过的键，
     * 文件里多出来的 {@code extra_gap_items} 会被当成多余的键丢掉（下次保存就彻底没了），
     * 玩家自己加进去的隙间物品就会悄悄失效——不能进饰品栏、不能潜行绑定、按 G 也不开终端。
     * 那时改内存里的值已经没用（旧键根本没被读进来），所以只能抢在读取之前把文件里的键名换掉：
     * 值原样保留，玩家一个字都不用改。
     *
     * 由 GeneratedMod 在模组构造阶段调用（那时配置还没加载）。整个方法不抛异常，
     * 读不到文件、写不进去都只记一条日志，绝不影响开服。
     *
     * 两个边界保护（复审指出，均已核实）：
     * <ul>
     *   <li>值块完整性：Forge 的 TOML 把长列表写成多行数组（{@code key = [} 换行逐条 {@code ]} 收尾）。
     *       注释旧键时必须连值块整块注释，只注释首行会留下 {@code "物品id",} 和 {@code ]} 孤儿行，
     *       整个配置文件解析失败、全部设置被重置回默认；</li>
     *   <li>重复出现：同一文件里出现多条旧键时（手改、合并配置导致），全部改名会造出重复键、
     *       只处理最后一条会留下死键。这里改成：第一条改名生效，其余整块注释。</li>
     *   <li>双键合并（审查报告 P3）：新键已经存在时不能再简单地把旧键注释掉——旧键里可能有
     *       玩家自己登记、而新键（多半还是自动生成的默认值）里没有的隙间物品，直接注释会让它们
     *       静默失效。现在改成先把旧键里新键缺的条目并进新键，再注释旧块，一个都不丢。</li>
     * </ul>
     */
    public static void migrateLegacyConfig(Path configDir) {
        if (configDir == null) {
            return;
        }
        Path file = configDir.resolve(CONFIG_FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int newStart = -1;
            int newEnd = -1;
            List<int[]> legacyRanges = new ArrayList<>(); // 每条旧键的 {起始行, 结束行}（含多行值块）
            int i = 0;
            while (i < lines.size()) {
                KeyEntry e = parseEntry(lines, i);
                if (e == null) {
                    i++;
                    continue;
                }
                if (e.key().equals(EXTRA_KEY)) {
                    if (newStart < 0) {
                        newStart = e.start();
                        newEnd = e.end();
                    }
                } else if (e.key().equals(LEGACY_EXTRA_KEY)) {
                    legacyRanges.add(new int[] {e.start(), e.end()});
                }
                i = e.end() + 1; // 整块跳过：数组值里的行不会被当成配置键来解析
            }
            if (legacyRanges.isEmpty()) {
                return; // 没有旧键，什么都不用做（升级过、或全新安装）
            }
            String note = "   # 旧键名，已由 " + EXTRA_KEY + " 取代，本模组不再读取";
            if (newStart >= 0) {
                // 两个键都在（1.3.2 迁移过一次、玩家又手工加回旧键，或新旧配置被合并）：
                // 旧键的值不能直接扔——里面可能有玩家自己登记的隙间物品（审查报告 P3）。
                // 顺序很讲究：① 先把两边的值都读出来（只读）；② 注释旧块（原地改文本，行号不动）；
                // ③ 最后才往新块里插行——插完就不再依赖任何旧行号了。
                List<String> existing = collectQuoted(lines, newStart, newEnd);
                List<String> missing = new ArrayList<>();
                for (int[] r : legacyRanges) {
                    for (String id : collectQuoted(lines, r[0], r[1])) {
                        if (!existing.contains(id) && !missing.contains(id)) {
                            missing.add(id);
                        }
                    }
                }
                for (int[] r : legacyRanges) {
                    commentOut(lines, r[0], r[1], note);
                }
                insertIntoArray(lines, newStart, newEnd, missing);
                Files.write(file, lines, StandardCharsets.UTF_8);
                if (missing.isEmpty()) {
                    LOGGER.warn("[maid_better_gap] 配置文件里同时有 {} 和 {}，旧键的值新键里全都有，"
                            + "已把旧键注释掉；以后只改 {} 即可。", LEGACY_EXTRA_KEY, EXTRA_KEY, EXTRA_KEY);
                } else {
                    LOGGER.warn("[maid_better_gap] 配置文件里同时有 {} 和 {}，已把旧键里新键没有的条目"
                            + "（{}）并进新键，并注释掉旧键；以后只改 {} 即可。",
                            LEGACY_EXTRA_KEY, EXTRA_KEY, String.join(", ", missing), EXTRA_KEY);
                }
                return;
            }
            // 只改键名，后面的值（物品 ID 列表，含多行数组）整块原样不动
            int[] first = legacyRanges.get(0);
            String line = lines.get(first[0]);
            int at = line.indexOf(LEGACY_EXTRA_KEY);
            lines.set(first[0], line.substring(0, at) + EXTRA_KEY
                    + line.substring(at + LEGACY_EXTRA_KEY.length()));
            // 万一文件里有多条旧键：保留刚改名的第一条，其余整块注释，避免造出重复键
            for (int k = 1; k < legacyRanges.size(); k++) {
                commentOut(lines, legacyRanges.get(k)[0], legacyRanges.get(k)[1], note);
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
            LOGGER.info("[maid_better_gap] 已把旧配置键 {} 迁移为 {}，里面登记的隙间物品照旧生效。",
                    LEGACY_EXTRA_KEY, EXTRA_KEY);
        } catch (IOException | RuntimeException ex) {
            // 配置文件损坏、只读之类的情况：提示一次即可，配置本身还有默认值兜底
            LOGGER.warn("[maid_better_gap] 旧配置键迁移失败（不影响开服），"
                    + "如需手动迁移，请把 {} 的内容复制到 {}。", LEGACY_EXTRA_KEY, EXTRA_KEY, ex);
        }
    }

    /** 一条「键 = 值」配置项：键名 + 它的完整行范围（多行数组值块也整体算在内）。 */
    private record KeyEntry(String key, int start, int end) {}

    /**
     * 解析第 i 行是不是一个配置键；是则返回它连同多行值块的完整范围。
     * 空行、注释、小节头、以及多行数组的中间行都会返回 null（不是键，跳过即可）。
     */
    private static KeyEntry parseEntry(List<String> lines, int i) {
        String trimmed = lines.get(i).trim();
        // 跳过空行和注释行：注释里出现的键名不算真的配置项
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        int eq = trimmed.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        String key = unquoteKey(trimmed.substring(0, eq).trim());
        int end = i;
        String value = trimmed.substring(eq + 1).trim();
        // 多行数组：同行没等到收尾的 ]，往下找到 ] 那行为止（找不到就按单行处理，文件本身坏了不自责）
        // 【R6，r52 复审】] 只认「引号外、行注释前」的那一个：数组块里夹一行含 ] 的注释
        // （# 示例见附录 B]）或串内 ]（"a]b"）时，整行 contains 会把 end 提前定在那，
        // 真收尾行之后的条目 collectQuoted 读不到，迁移旧键时会被当 missing 再插一遍。
        if (value.startsWith("[") && arrayCloseIndex(trimmed) < 0) {
            for (int j = i + 1; j < lines.size(); j++) {
                if (arrayCloseIndex(lines.get(j)) >= 0) {
                    end = j;
                    break;
                }
            }
        }
        return new KeyEntry(key, i, end);
    }

    /** 把 [start, end] 范围的行整块注释掉，起始行末尾附一句说明。 */
    private static void commentOut(List<String> lines, int start, int end, String note) {
        for (int j = start; j <= end; j++) {
            lines.set(j, "# " + lines.get(j) + (j == start ? note : ""));
        }
    }

    /**
     * 抠出一个字符串数组值块里所有带引号的条目（按出现顺序）。
     * 够简单场合用：隙间物品 ID 一律是 "ns:path" 形式，不含内嵌引号/转义，
     * 逐字符配对双引号即可，不必上完整的 TOML 解析器。
     * 【P3-3】先把每行截到行注释之前再配对：Forge 在数组块前写的说明注释、
     * 玩家手加的注释里常带 "物品id" 这种示例引号，不过滤会被当成真条目并进新键。
     */
    private static List<String> collectQuoted(List<String> lines, int start, int end) {
        List<String> out = new ArrayList<>();
        for (int j = start; j <= end; j++) {
            String raw = lines.get(j);
            String line = raw.substring(0, commentIndex(raw));
            int from = 0;
            while (true) {
                int open = line.indexOf('"', from);
                if (open < 0) {
                    break;
                }
                int close = line.indexOf('"', open + 1);
                if (close < 0) {
                    break; // 落单的引号（行被手改坏了）：到这就停，后面的内容不可信
                }
                String v = line.substring(open + 1, close).trim();
                if (!v.isEmpty()) {
                    out.add(v);
                }
                from = close + 1;
            }
        }
        return out;
    }

    /**
     * 一行 TOML 里「注释 #」的起始下标（引号里的 # 不算注释）。
     * 返回行长本身表示没有注释。物品 ID 形式的字符串不含 #，按引号对跳过即可。
     */
    private static int commentIndex(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                int close = line.indexOf('"', i + 1);
                if (close < 0) {
                    return line.length(); // 落单引号：后面不再可信，当整行都是内容处理
                }
                i = close;
            } else if (c == '#') {
                return i;
            }
        }
        return line.length();
    }

    /**
     * 一行里数组收尾 `]` 的下标（取引号外、注释前的最后一个），没有则 -1。
     * 【R6，r52 复审】与 commentIndex 同一口径：串内 `]`（"a]b"）与注释里的 `]`
     * （# 示例见附录 B]）都不算收尾。落单引号之后的内容不可信，就此打住。
     */
    private static int arrayCloseIndex(String line) {
        int found = -1;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                int close = line.indexOf('"', i + 1);
                if (close < 0) {
                    return found; // 落单引号：引号里的 ] 不敢认，返回已找到的结果
                }
                i = close;
            } else if (c == '#') {
                return found; // 其后全是注释
            } else if (c == ']') {
                found = i;
            }
        }
        return found;
    }

    /**
     * 把若干字符串条目插进一个已定位的字符串数组值块（[start, end] 由 parseEntry 给出）。
     * 单行数组在收尾 ] 前追加；多行数组在 ] 那行前面逐条插行。
     * missing 为空时什么都不做（也绝不重排文件里已有的行）。
     *
     * 【P3】两个必须守住的 TOML 语法边界：
     * <ul>
     *   <li>单行数组已带尾逗号（{@code ["a",]}）：先剥掉尾逗号再决定用什么分隔，
     *       否则会写成 {@code ["a",, "b" ]}，整个配置文件解析失败、全部设置回滚默认；</li>
     *   <li>多行数组最后一项没有尾逗号：直接在 ] 前插行会写成
     *       {@code "a:one"⏎ "b:two",}——两行之间缺逗号，同样解析失败。
     *       所以插行之前先给块内最后一个真条目行补上逗号。</li>
     * </ul>
     */
    private static void insertIntoArray(List<String> lines, int start, int end, List<String> missing) {
        if (missing.isEmpty()) {
            return;
        }
        StringBuilder add = new StringBuilder();
        for (String id : missing) {
            if (add.length() > 0) {
                add.append(", ");
            }
            add.append('"').append(id).append('"');
        }
        String first = lines.get(start);
        int eq = first.indexOf('=');
        String value = eq < 0 ? "" : first.substring(eq + 1).trim();
        boolean singleLine = start == end && value.startsWith("[") && arrayCloseIndex(first) >= 0;
        if (singleLine) {
            // 【R6-1】找收尾 ] 必须跳过行尾注释（arr = ["a"]  # 见附录 B]）里的 ]，
            // 整行 lastIndexOf 会命中注释里那个、从中间把行截成不可解析的两半；
            // 【R6，r52 复审】arrayCloseIndex 在此之上还跳过引号里的 ]（"a]b"），与 parseEntry 同口径。
            int close = arrayCloseIndex(first);
            if (close < 0) {
                return; // ] 只存在于注释里：这一行本身已畸形，宁可不动也不写坏
            }
            String head = first.substring(0, close).stripTrailing();
            String tail = first.substring(close);
            // 已有尾逗号先剥掉（"a", → "a"），统一走“缺什么补什么”的分支
            if (head.endsWith(",")) {
                head = head.substring(0, head.length() - 1).stripTrailing();
            }
            // 空数组 [ ] 直接填；非空（["a"]）用逗号续上
            String sep = head.endsWith("[") ? " " : ", ";
            lines.set(start, head + sep + add + " " + tail);
            return;
        }
        // 多行数组：插行前保证块内最后一个条目以逗号收尾（P3-1），再在 ] 行前逐条插入。
        // 【R6 防线，r52 复审】parseEntry 扫完全文件没找到 ] 时会按单行返回（end == start）——
        // 这种数组块本身就缺收尾，往 end 前插行会把条目插到键行前面、造出非法顶层行。
        // 文件已坏，宁可一个条目都不插，也不在坏文件上再写一笔更坏的。
        if (end <= start) {
            return;
        }
        ensureTrailingCommaOnLastEntry(lines, start, end);
        String indent = "  ";
        for (int j = start + 1; j < end; j++) {
            String l = lines.get(j);
            if (l.substring(0, commentIndex(l)).contains("\"")) {
                indent = l.substring(0, l.length() - l.stripLeading().length());
                break;
            }
        }
        for (int k = missing.size() - 1; k >= 0; k--) {
            lines.add(end, indent + "\"" + missing.get(k) + "\",");
        }
    }

    /**
     * 从 ] 行往上找块内最后一个「真条目行」（跳空行与注释行），若它以引号收尾就补一个逗号。
     * 逗号补在行内最后一个引号之后（而不是行尾），行尾注释不会被挤进非法位置。
     *
     * 【R6，r52 复审】循环下界取到 start（原为 j > start）：合法形态
     * {@code enhanced_gap_items = ["a:one"␎]} 的唯一条目落在 start 行、] 独占 end 行，
     * 旧界把 start 排除在外 → 不补逗号 → 插行后 "a:one" 与 "x:y" 之间缺逗号 → 整文件解析失败、
     * 全部设置回滚默认且坏文件留着回不来。改成含 start 后：
     * start 行 body 以 [ 收尾（纯开括号）时 continue 跳过，安全；
     * 以 " 收尾（条目就在这一行）时正确补逗号，覆盖住这个真会写坏的形态。
     */
    private static void ensureTrailingCommaOnLastEntry(List<String> lines, int start, int end) {
        for (int j = end - 1; j >= start; j--) {
            String l = lines.get(j);
            int c = commentIndex(l);
            String body = l.substring(0, c).stripTrailing();
            if (body.isEmpty() || body.endsWith("[")) {
                continue; // 空行 / 注释行 / 以 [ 收尾的开行
            }
            if (body.endsWith("\"")) {
                int q = l.lastIndexOf('"', c);
                if (q >= 0) {
                    lines.set(j, l.substring(0, q + 1) + "," + l.substring(q + 1));
                }
            }
            return; // 最后一个条目行处理过（本来就带逗号时什么都不用做）
        }
    }

    /** TOML 的键名可能带引号（"key" = ...），比较前先去掉。 */
    private static String unquoteKey(String key) {
        if (key.length() >= 2 && key.startsWith("\"") && key.endsWith("\"")) {
            return key.substring(1, key.length() - 1);
        }
        return key;
    }

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
