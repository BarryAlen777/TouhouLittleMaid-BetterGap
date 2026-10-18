package cn.blockforge.generated.maidbettergap.gap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家级偏好设置（存在玩家持久化数据里，跟着存档走）：
 * 自动补货开关 + 「用完物品」记忆表 + 「最近用过什么」的短期记录。
 *
 * 注意：容器绑定、白名单、传输方向、拿取数量、槽位锁定都不存在这里——
 * 它们存在「隙间物品自己的 NBT」上（见 {@link GapItemData}），
 * 与车万女仆原版设计一致，女仆戴着隙间就能带着绑定工作。
 */
public final class GapStore {

    private static final String ROOT = "maid_gap_enh";
    private static final String K_REFILL = "auto_refill";
    private static final String K_REFILL_MEM = "refill_memory";

    /** 每种物品最多记这么多条「最近用过」，只在服务端内存里，退出即清。 */
    private static final int RECENT_MAX = 16;

    /**
     * 「最近用过的物品」：按玩家 UUID 记，服务端内存里，不写存档。
     * 只解决一个场景：整组物品被一口气用光（吃、喝、放方块、投掷），
     * 补货扫描（默认 2 秒一次）没赶上它还剩几个的那一瞬间，也要知道该补什么。
     */
    private static final Map<UUID, List<RecentUse>> RECENT_USED = new HashMap<>();

    /** 一条“最近用过”记录：物品样本 + 用掉时的游戏时间（用来判断新不新鲜）。 */
    private record RecentUse(ItemStack stack, long time) {
    }

    private GapStore() {}

    private static CompoundTag root(Player player) {
        return player.getPersistentData().getCompound(ROOT);
    }

    private static void saveRoot(Player player, CompoundTag root) {
        player.getPersistentData().put(ROOT, root);
    }

    public static boolean autoRefillOn(Player player) {
        return root(player).getBoolean(K_REFILL);
    }

    public static void setAutoRefill(Player player, boolean on) {
        CompoundTag root = root(player);
        root.putBoolean(K_REFILL, on);
        saveRoot(player, root);
    }

    // ------------------------------------------------------------------
    // 「用完物品」记忆表：自动补货用它把刚用光（0 个）的物品也记得补
    // ------------------------------------------------------------------

    /** 记忆条目：每种物品一份数量 1 的样本（含 NBT），最多 24 种。 */
    public static List<ItemStack> refillMemory(Player player) {
        List<ItemStack> out = new ArrayList<>();
        ListTag list = root(player).getList(K_REFILL_MEM, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack st = ItemStack.of(list.getCompound(i));
            if (!st.isEmpty()) {
                out.add(st);
            }
        }
        return out;
    }

    public static void setRefillMemory(Player player, List<ItemStack> memory) {
        ListTag list = new ListTag();
        for (ItemStack st : memory) {
            CompoundTag e = new CompoundTag();
            st.save(e);
            list.add(e);
        }
        CompoundTag root = root(player);
        if (list.isEmpty()) {
            root.remove(K_REFILL_MEM);
        } else {
            root.put(K_REFILL_MEM, list);
        }
        saveRoot(player, root);
    }

    /** 死亡重生时把偏好设置完整带到新玩家对象上。 */
    public static void copyOnClone(Player from, Player to) {
        CompoundTag src = from.getPersistentData().getCompound(ROOT);
        if (!src.isEmpty()) {
            to.getPersistentData().put(ROOT, src.copy());
        }
    }

    // ------------------------------------------------------------------
    // 「最近用过的物品」：吃过/喝过/放下去/投掷出去的物品，供自动补货兜底
    // ------------------------------------------------------------------

    /** 记一笔“这个物品刚被用掉”。只记可堆叠的消耗品（工具/盔甲不补）。 */
    public static void rememberUsed(Player player, ItemStack used) {
        if (player == null || used.isEmpty() || used.getMaxStackSize() <= 1) {
            return;
        }
        ItemStack proto = used.copy();
        proto.setCount(1);
        List<RecentUse> list = RECENT_USED.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        list.removeIf(old -> ItemStack.isSameItemSameTags(old.stack(), proto));
        list.add(0, new RecentUse(proto, player.level().getGameTime()));
        while (list.size() > RECENT_MAX) {
            list.remove(list.size() - 1);
        }
    }

    /**
     * 最近 maxAgeTicks 内用过的物品（新的排前面）。
     * 带时效是故意的：很久以前吃过一次的东西，不该在背包用完时又被当成“刚用光”补一整组。
     */
    public static List<ItemStack> recentlyUsed(Player player, int maxAgeTicks) {
        if (player == null) {
            return List.of();
        }
        List<RecentUse> list = RECENT_USED.get(player.getUUID());
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        long now = player.level().getGameTime();
        List<ItemStack> out = new ArrayList<>();
        for (RecentUse use : list) {
            if (now - use.time() <= maxAgeTicks) {
                out.add(use.stack());
            }
        }
        return out;
    }

    /** 玩家退出时清掉，别让内存表一直涨。 */
    public static void forgetRecent(Player player) {
        if (player != null) {
            RECENT_USED.remove(player.getUUID());
        }
    }
}
