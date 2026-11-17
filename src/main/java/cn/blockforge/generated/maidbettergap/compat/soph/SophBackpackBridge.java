package cn.blockforge.generated.maidbettergap.compat.soph;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 与「女仆精妙」(maid_sophisticated) / 「精妙背包」(sophisticatedbackpacks) 的桥。
 *
 * 为什么是反射：本工程的依赖只有 Forge + 车万女仆，编译期拿不到那两个模组的 jar。
 * 而女仆身上那三个精妙背包槽是 maid_sophisticated 自己的 capability，背包内部物品栏
 * 又要经它的 SbCompat.inventoryHandler 才拿得到，所以这里只在「两个模组都装了」时
 * 才用反射去够，够不到就安静地当没这个功能（绝不因为缺模组而崩溃）。
 *
 * 用到对方两个公开方法（1.21.1 的 1.3.0-r19 源码确认过签名）：
 *   MaidBackpackData.of(Entity)          -> Optional&lt;MaidBackpackData&gt;（继承 Forge 的 ItemStackHandler）
 *   SbCompat.inventoryHandler(ItemStack) -> Optional&lt;IItemHandlerModifiable&gt;
 *
 * 另外探测第三个入口（并入补丁，见 {@link #mergedTailSlots}）：
 *   MaidBackpackInv.of(EntityMaid)       -> IItemHandlerModifiable（拼起来的背包视图，无背包时 null）
 *   MaidBackpackInv.hookMerged()         -> boolean（补丁真的往 getAvailableInv 上拼过格子）
 * 女仆精妙 1.3.0 起用 Mixin 把精妙背包并进 EntityMaid.getAvailableInv 的返回值<b>末尾</b>
 * （顺序：女仆自己的格子 → 主手 → 副手 → 背包内容）。我们的搬运如果照常扫整个
 * getAvailableInv 再叠加自己的背包通道，同一批背包物品每轮会被搬两遍，
 * 槽位锁的「主手/副手挪到数组末尾」重排也会把手部锁定错挪到背包格上——
 * 所以搬运前先用 {@link #mergedTailSlots} 量出尾部并入的格数，把那段从女仆侧视野里切掉，
 * 背包内容继续只走我们自己的 wornBackpacks 通道，一遍不多、一遍不少。
 */
public final class SophBackpackBridge {

    private static final String SB_MOD_ID = "sophisticatedbackpacks";
    private static final String SOPH_MOD_ID = "maid_sophisticated";

    private static final Logger LOGGER = LoggerFactory.getLogger("maid_better_gap/soph");

    private static boolean resolved = false;
    private static boolean usable = false;
    private static boolean failureLogged = false;

    /** MaidBackpackData.of(Entity) */
    private static Method maidDataOf;
    /** SbCompat.inventoryHandler(ItemStack) */
    private static Method inventoryHandler;
    /** MaidBackpackInv.of(EntityMaid) —— 并入补丁用的拼接视图（可能不存在 = 旧版精妙） */
    private static Method mergedOf;
    /** MaidBackpackInv.hookMerged() —— 补丁是否真的成功拼过（可能不存在） */
    private static Method hookMerged;
    /** 并入补丁的探测只做一次（独立于 usable：找不到探测方法只说明对方是没带补丁的老版本）。 */
    private static boolean mergeProbed = false;

    private SophBackpackBridge() {}

    /** 两个前置模组是否都装了、反射是否够得着。结果只算一次。 */
    public static boolean usable() {
        resolve();
        return usable;
    }

    /**
     * 精妙背包是否已经被并入 {@code maid.getAvailableInv(...)} 的返回值尾部。
     *
     * <p>女仆精妙 1.3.0+ 用 Mixin 把背包拼进 getAvailableInv（服务端）。一旦并进来，
     * 调用方再自己拿 {@link #wornBackpacks} 单独搬一遍就会<b>每轮搬两遍</b>，
     * 而且 {@code getAvailableInv().getSlots()} 会把背包格也算进去、让「主手/副手在末尾」的
     * 槽位锁重排错位。本方法返回并入的背包总格数，调用方据此把尾部从女仆自身视野里切掉：
     * 女仆侧只扫 {@code getSlots() - 返回值} 格，剩下的背包格仍走 {@link #wornBackpacks} 专通道。</p>
     *
     * <p>判定用 {@code MaidBackpackInv.hookMerged()}——它只有 mixin <b>真的</b>把格子拼上后才置位，
     * 能区分「装了新精妙但注入点没接上」和「确实并入了」。调用前 doTick 已经调过一次
     * {@code getAvailableInv}，注入点若生效此时必已置位，没有启动竞态。探测不到该方法 = 旧版精妙
     * 没有并入补丁 → 返回 0，一切照旧。任何异常都退回 0（当没并入处理，最坏是重复搬运，不崩）。</p>
     *
     * <p>注意与 {@code SOPH_BACKPACKS} 配置无关：配置只是决定我们<b>要不要搬</b>背包里的东西，
     * 并入是女仆精妙自己塞进 {@code getAvailableInv} 的，配置关了尾段照样存在，必须照样切掉。</p>
     */
    public static int mergedTailSlots(Entity maid) {
        // 【P2】这里不能过 usable()：并入检测只依赖 MaidBackpackInv 一个类，
        // 和 wornBackpacks 用的两个旧入口（MaidBackpackData / SbCompat）是两套反射目标。
        // 精妙未来若重构掉旧入口，usable() 变 false 会把并入检测一起杀掉——
        // ownEnd 不切尾 → 背包内容双倍搬运、槽位锁「手在末尾」重排错位到背包格。
        // 探测本身自带 ClassNotFound 兜底（probeMerge 静默置空），未装精妙时恒 0，不走弯路。
        if (maid == null) {
            return 0;
        }
        probeMerge();
        if (hookMerged == null || mergedOf == null) {
            return 0; // 旧版精妙 / 未安装：没有并入这回事
        }
        try {
            if (!Boolean.TRUE.equals(hookMerged.invoke(null))) {
                return 0;
            }
            // of(maid) 就是被拼到尾部的那份视图；null = 她其实没背背包，没东西被拼上
            Object composed = mergedOf.invoke(null, maid);
            if (composed instanceof IItemHandler h) {
                return Math.max(0, h.getSlots());
            }
            return 0;
        } catch (Throwable t) {
            logOnce(t);
            return 0;
        }
    }

    /** 并入补丁的探测：只做一次，独立于 usable。 */
    private static void probeMerge() {
        if (mergeProbed) {
            return;
        }
        mergeProbed = true;
        try {
            Class<?> invClass = Class.forName("dev.barryalen.maidsophisticated.MaidBackpackInv");
            Class<?> maidClass = Class.forName(
                    "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid");
            mergedOf = invClass.getMethod("of", maidClass);
            hookMerged = invClass.getMethod("hookMerged");
        } catch (Throwable t) {
            // 类/方法不存在 = 旧版精妙没有并入补丁；静默当作没并入
            mergedOf = null;
            hookMerged = null;
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            if (!ModList.get().isLoaded(SB_MOD_ID) || !ModList.get().isLoaded(SOPH_MOD_ID)) {
                return;
            }
            Class<?> dataClass = Class.forName("dev.barryalen.maidsophisticated.MaidBackpackData");
            maidDataOf = dataClass.getMethod("of", Entity.class);
            Class<?> compatClass = Class.forName("dev.barryalen.maidsophisticated.SbCompat");
            inventoryHandler = compatClass.getMethod("inventoryHandler", ItemStack.class);
            usable = true;
        } catch (Throwable t) {
            usable = false;
            logOnce(t);
        }
    }

    /**
     * 女仆身上所有「精妙背包」的内部物品栏（通常 0~3 个）。
     * 没有前置模组、或女仆身上没背包时返回空列表。
     *
     * 【R5 补全，r52 复审】两个旧入口（MaidBackpackData.of / SbCompat.inventoryHandler）整个失效、
     * 而并入补丁那套反射（MaidBackpackInv）还活着时，不要空手而归：
     * {@code MaidBackpackInv.of(maid)} 返回的拼接视图正是被并进 getAvailableInv 尾段的那份
     * IItemHandler，内容一一对应——拿它当专通道，尾段照切、槽位锁照对、终端显示照切，
     * 三处口径重新一致（EnhancedGapBauble 的「专通道空着就不切尾」兜底退居最后防线）。
     */
    public static List<IItemHandler> wornBackpacks(Entity maid) {
        List<IItemHandler> out = new ArrayList<>();
        if (maid == null) {
            return out;
        }
        if (!usable()) {
            return composedBackpacks(maid);
        }
        try {
            Object result = maidDataOf.invoke(null, maid);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                return out;
            }
            // MaidBackpackData extends net.neoforged.neoforge.items.ItemStackHandler，
            // 所以这里可以按 Forge 自己的类型来用，不需要引用对方的类
            if (!(optional.get() instanceof ItemStackHandler slots)) {
                return out;
            }
            for (int i = 0; i < slots.getSlots(); i++) {
                IItemHandler inv = backpackInventory(slots.getStackInSlot(i));
                if (inv != null) {
                    out.add(inv);
                }
            }
        } catch (Throwable t) {
            logOnce(t);
        }
        return out;
    }

    /**
     * R5 降级兜底：旧入口失效时改拿并入视图。只在 {@code hookMerged()} 为真时给出——
     * 那一刻它才与实际拼上尾段的格子一一对应；没并入却返回拼接视图，反而会和女仆侧视野重复搬运。
     * 任何异常都当没有（最坏退回 EnhancedGapBauble 的「不切尾」兜底，不会更糟）。
     */
    private static List<IItemHandler> composedBackpacks(Entity maid) {
        List<IItemHandler> out = new ArrayList<>();
        probeMerge();
        if (hookMerged == null || mergedOf == null) {
            return out; // 连并入探测也没有：真没法子，交给调用侧兜底
        }
        try {
            if (!Boolean.TRUE.equals(hookMerged.invoke(null))) {
                return out;
            }
            Object composed = mergedOf.invoke(null, maid);
            if (composed instanceof IItemHandler h && h.getSlots() > 0) {
                out.add(h);
            }
        } catch (Throwable t) {
            logOnce(t);
        }
        return out;
    }

    /** 单个精妙背包物品的内部物品栏；不是背包 / 没装精妙背包时返回 null。 */
    public static IItemHandler backpackInventory(ItemStack backpackStack) {
        if (backpackStack == null || backpackStack.isEmpty() || !usable()) {
            return null;
        }
        try {
            Object result = inventoryHandler.invoke(null, backpackStack);
            if (result instanceof Optional<?> optional && optional.isPresent()) {
                Object handler = optional.get();
                if (handler instanceof IItemHandler items) {
                    return items;
                }
            }
        } catch (Throwable t) {
            logOnce(t);
        }
        return null;
    }

    private static void logOnce(Throwable t) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        LOGGER.error("[maid_better_gap] 读取「女仆精妙」的精妙背包失败，本次搬运跳过背包部分。"
                + " 若女仆精妙更新过，可能需要同步更新本模组。", t);
    }
}
