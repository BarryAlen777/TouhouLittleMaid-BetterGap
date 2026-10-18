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

    private static boolean allowed(ResourceLocation rl) {
        List<? extends String> namespaces = MaidGapConfig.TAG_MATCH_NAMESPACES.get();
        boolean nsOk = namespaces == null || namespaces.isEmpty();
        if (!nsOk) {
            for (String ns : namespaces) {
                if (ns != null && ns.trim().toLowerCase(Locale.ROOT).equals(rl.getNamespace())) {
                    nsOk = true;
                    break;
                }
            }
        }
        if (!nsOk) {
            return false;
        }
        List<? extends String> blacklist = MaidGapConfig.TAG_MATCH_BLACKLIST.get();
        if (blacklist != null) {
            String id = rl.toString();
            for (String s : blacklist) {
                if (s != null && s.trim().toLowerCase(Locale.ROOT).equals(id)) {
                    return false;
                }
            }
        }
        return true;
    }
}
