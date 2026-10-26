package cn.blockforge.generated.maidbettergap.gap;

import cn.blockforge.generated.maidbettergap.MaidGapConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 类别匹配（按物品标签归类）：
 * Minecraft 的物品自带分类标签（如 #minecraft:fishes 包含鳕鱼/鲑鱼/热带鱼/河豚，
 * #forge:glass_panes 包含所有玻璃板），模组物品只要注册进同一个标签就会被视为一类。
 * 白名单里放一条鳕鱼，开启类别匹配后就能覆盖所有模组的“鱼”。
 *
 * 为避免误伤，只有满足以下条件的标签参与匹配：
 * 1. 命名空间在配置 tag_match_namespaces 里（默认 minecraft / forge / c）；
 * 2. 不在配置 tag_match_blacklist 里（排除“狐食物”这类行为标签造成的意外归类）。
 */
public final class TagMatcher {

    private TagMatcher() {}

    /** 该物品参与类别匹配的全部标签（已按配置过滤）。 */
    public static List<TagKey<Item>> categoryTags(ItemStack stack) {
        List<TagKey<Item>> out = new ArrayList<>();
        if (stack.isEmpty()) {
            return out;
        }
        stack.getTags().forEach(key -> {
            if (allowed(key.location())) {
                out.add(key);
            }
        });
        return out;
    }

    /** 该物品参与类别匹配的标签 ID（给界面提示用）。 */
    public static List<ResourceLocation> categoryTagIds(ItemStack stack) {
        List<ResourceLocation> out = new ArrayList<>();
        if (stack.isEmpty()) {
            return out;
        }
        stack.getTags().forEach(key -> {
            if (allowed(key.location())) {
                out.add(key.location());
            }
        });
        out.sort(ResourceLocation::compareNamespaced);
        return out;
    }

    /** 两个物品是否共享至少一个类别标签。 */
    public static boolean sharesCategory(ItemStack a, ItemStack b) {
        Set<TagKey<Item>> tags = tagSet(a);
        if (tags.isEmpty() || b.isEmpty()) {
            return false;
        }
        return b.getTags().anyMatch(tags::contains);
    }

    /** 预取标签集合（批量匹配时避免重复解析名单条目的标签）。 */
    public static Set<TagKey<Item>> tagSet(ItemStack a) {
        Set<TagKey<Item>> tags = new HashSet<>();
        if (!a.isEmpty()) {
            a.getTags().forEach(key -> {
                if (allowed(key.location())) {
                    tags.add(key);
                }
            });
        }
        return tags;
    }

    /** 由标签 ID 构造 TagKey（网络/界面用）。 */
    public static TagKey<Item> key(ResourceLocation rl) {
        return TagKey.create(Registries.ITEM, rl);
    }

    /**
     * 配置解析结果缓存：标签匹配在热路径上（每次搬东西都要过一遍名单），
     * 而 allowed() 每次都会把配置里的字符串小写化再逐条比较，很浪费。
     * 配置对象本身在重载时会被替换，所以用「引用是否变了」来判断要不要重新解析。
     */
    private static List<? extends String> nsRaw;
    private static Set<String> nsSet = Set.of();
    private static List<? extends String> blRaw;
    private static Set<String> blSet = Set.of();

    private static Set<String> namespaces() {
        List<? extends String> raw = MaidGapConfig.TAG_MATCH_NAMESPACES.get();
        if (raw != nsRaw) {
            nsRaw = raw;
            nsSet = lowerSet(raw);
        }
        return nsSet;
    }

    private static Set<String> blacklist() {
        List<? extends String> raw = MaidGapConfig.TAG_MATCH_BLACKLIST.get();
        if (raw != blRaw) {
            blRaw = raw;
            blSet = lowerSet(raw);
        }
        return blSet;
    }

    private static Set<String> lowerSet(List<? extends String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new HashSet<>();
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static boolean allowed(ResourceLocation rl) {
        Set<String> namespaces = namespaces();
        // 空名单 = 全部命名空间都参与（与原逻辑一致）
        if (!namespaces.isEmpty() && !namespaces.contains(rl.getNamespace())) {
            return false;
        }
        return !blacklist().contains(rl.toString());
    }
}
