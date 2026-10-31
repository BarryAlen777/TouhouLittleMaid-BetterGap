package cn.blockforge.generated.maidbettergap.gap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 隙间终端里的「链接」数据：记录终端连着哪些女仆（每人 UUID + 展示名），以及当前在管哪一只。
 * 只碰物品 NBT，不引用车万女仆的任何类，没装前置也能安全调用。
 *
 * 老格式（单只女仆）仍然兼容：
 *   MGLinkMaid —— 当前这只女仆的 UUID（也一直跟着当前女仆同步，原逻辑与提示照常可用）
 *   MGLinkName —— 当前这只女仆的展示名
 * 新格式：
 *   MGMaidLinks  —— [{uuid, name}, ...]，终端里保存的女仆列表（顺序 = 界面里的顺序）
 *   MGMaidActive —— 当前正在管理的那只女仆的 UUID
 */
public final class GapLinkData {

    public static final String K_LINK_UUID = "MGLinkMaid";
    public static final String K_LINK_NAME = "MGLinkName";
    private static final String K_MAIDS = "MGMaidLinks";
    private static final String K_ACTIVE = "MGMaidActive";
    private static final String K_M_UUID = "uuid";
    private static final String K_M_NAME = "name";

    /** 列表里的一只女仆。 */
    public record MaidRef(UUID uuid, String name) {}

    private GapLinkData() {}

    // ------------------------------------------------------------------
    // 女仆列表
    // ------------------------------------------------------------------

    /** 终端里存的所有女仆（老格式会当成只有一只）。 */
    public static List<MaidRef> maids(ItemStack stack) {
        List<MaidRef> out = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return out;
        }
        if (tag.contains(K_MAIDS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(K_MAIDS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                UUID u = parse(e.getString(K_M_UUID));
                if (u != null && find(out, u) == null) {
                    out.add(new MaidRef(u, e.getString(K_M_NAME)));
                }
            }
        }
        if (out.isEmpty()) {
            // 老存档：只有一个链接键
            UUID legacy = parse(tag.getString(K_LINK_UUID));
            if (legacy != null) {
                out.add(new MaidRef(legacy, tag.getString(K_LINK_NAME)));
            }
        }
        return out;
    }

    /** 当前正在管理的那只女仆（列表里没有时退回列表第一只）。 */
    @Nullable
    public static UUID activeUuid(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            UUID active = parse(tag.getString(K_ACTIVE));
            if (active != null) {
                return active;
            }
            UUID legacy = parse(tag.getString(K_LINK_UUID));
            if (legacy != null) {
                return legacy;
            }
        }
        List<MaidRef> list = maids(stack);
        return list.isEmpty() ? null : list.get(0).uuid();
    }

    public static boolean hasLink(ItemStack stack) {
        return activeUuid(stack) != null;
    }

    /** 当前女仆的展示名（女仆不在附近时界面也能显示“连的是谁”）。 */
    public static String savedName(ItemStack stack) {
        UUID active = activeUuid(stack);
        if (active != null) {
            for (MaidRef r : maids(stack)) {
                if (r.uuid().equals(active)) {
                    return r.name() == null ? "" : r.name();
                }
            }
        }
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(K_LINK_NAME);
    }

    /**
     * 把一只女仆加进列表（已在列表里就更新她的展示名）并设为当前管理对象。
     *
     * @return false = 列表已满（上限由配置决定），什么都没改
     */
    public static boolean addMaid(ItemStack stack, UUID uuid, String name, int limit) {
        if (uuid == null) {
            return false;
        }
        List<MaidRef> list = maids(stack);
        MaidRef exist = find(list, uuid);
        if (exist == null) {
            if (list.size() >= Math.max(1, limit)) {
                return false;
            }
            list.add(new MaidRef(uuid, name == null ? "" : name));
        } else {
            list.set(list.indexOf(exist), new MaidRef(uuid, name == null ? "" : name));
        }
        writeMaids(stack, list, uuid);
        return true;
    }

    /** 切换当前管理的女仆（不在列表里则什么都不做）。 */
    public static void setActive(ItemStack stack, @Nullable UUID uuid) {
        if (uuid == null) {
            return;
        }
        List<MaidRef> list = maids(stack);
        if (find(list, uuid) == null) {
            return;
        }
        writeMaids(stack, list, uuid);
    }

    /** 把一只女仆移出列表；移掉的是当前那只时，自动切到列表里的下一只。 */
    public static boolean removeMaid(ItemStack stack, @Nullable UUID uuid) {
        if (uuid == null) {
            return false;
        }
        List<MaidRef> list = maids(stack);
        UUID active = activeUuid(stack);
        if (!list.removeIf(r -> r.uuid().equals(uuid))) {
            return false;
        }
        UUID next = active;
        if (uuid.equals(active)) {
            next = list.isEmpty() ? null : list.get(0).uuid();
        }
        writeMaids(stack, list, next);
        return true;
    }

    /** 解除当前这只女仆的链接（列表里还有别的女仆时不影响她们）。 */
    public static void unlink(ItemStack stack) {
        removeMaid(stack, activeUuid(stack));
    }

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    private static void writeMaids(ItemStack stack, List<MaidRef> list, @Nullable UUID active) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag lt = new ListTag();
        for (MaidRef r : list) {
            CompoundTag e = new CompoundTag();
            e.putString(K_M_UUID, r.uuid().toString());
            e.putString(K_M_NAME, r.name() == null ? "" : r.name());
            lt.add(e);
        }
        tag.put(K_MAIDS, lt);
        MaidRef act = active == null ? null : find(list, active);
        if (act == null && !list.isEmpty()) {
            act = list.get(0);
        }
        if (act == null) {
            tag.remove(K_ACTIVE);
            tag.remove(K_LINK_UUID);
            tag.remove(K_LINK_NAME);
        } else {
            // 老键继续同步，其它地方（悬停提示、旧版本存档）照常可读
            tag.putString(K_ACTIVE, act.uuid().toString());
            tag.putString(K_LINK_UUID, act.uuid().toString());
            tag.putString(K_LINK_NAME, act.name() == null ? "" : act.name());
        }
    }

    @Nullable
    private static MaidRef find(List<MaidRef> list, UUID uuid) {
        for (MaidRef r : list) {
            if (r.uuid().equals(uuid)) {
                return r;
            }
        }
        return null;
    }

    @Nullable
    private static UUID parse(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
