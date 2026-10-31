package cn.blockforge.generated.maidbettergap.compat.soph;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
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
 * 用到对方两个公开方法（版本 1.0.0-r39 的源码确认过签名）：
 *   MaidBackpackData.of(Entity)          -> Optional&lt;MaidBackpackData&gt;（继承 Forge 的 ItemStackHandler）
 *   SbCompat.inventoryHandler(ItemStack) -> Optional&lt;IItemHandlerModifiable&gt;
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

    private SophBackpackBridge() {}

    /** 两个前置模组是否都装了、反射是否够得着。结果只算一次。 */
    public static boolean usable() {
        resolve();
        return usable;
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
     */
    public static List<IItemHandler> wornBackpacks(Entity maid) {
        List<IItemHandler> out = new ArrayList<>();
        if (maid == null || !usable()) {
            return out;
        }
        try {
            Object result = maidDataOf.invoke(null, maid);
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) {
                return out;
            }
            // MaidBackpackData extends net.minecraftforge.items.ItemStackHandler，
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
