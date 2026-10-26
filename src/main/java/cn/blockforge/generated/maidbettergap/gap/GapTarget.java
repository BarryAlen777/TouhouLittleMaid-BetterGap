package cn.blockforge.generated.maidbettergap.gap;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * 终端界面要操作的「目标隙间」：
 * - 手持隙间物品时 = 手里那件（与旧行为一致）；
 * - 手持隙间终端时 = 链接女仆饰品栏里的那件隙间。
 *   stack 是饰品栏里的“活引用”，直接改它的 NBT，女仆下一次搬运立刻生效（实时更新）。
 *
 * maid 用 Object 承载（实际是 EntityMaid）：本类不允许引用车万女仆的类，
 * 没装前置时相关分支根本不会执行。
 */
public final class GapTarget {

    public final ItemStack stack;
    /** 实际类型是 EntityMaid；非终端模式为 null。 */
    @Nullable
    public final Object maid;
    public final int baubleSlot;
    /** true = 经由「隙间终端」远程操作女仆身上的隙间。 */
    public final boolean terminal;
    /** 展示用女仆名（链接丢失时用终端 NBT 里存的名字）。 */
    public final String maidName;
    /** 终端没有可用链接（没右键过女仆 / 女仆不在已加载区块）。 */
    public final boolean linkMissing;
    /** 女仆找到了，但她饰品栏里没有隙间。 */
    public final boolean maidHasNoGap;
    /** 链接到的女仆不是这位玩家的（终端转手给别人后，这条链接自动失效）。 */
    public final boolean notOwner;

    private GapTarget(ItemStack stack, @Nullable Object maid, int baubleSlot, boolean terminal,
                      String maidName, boolean linkMissing, boolean maidHasNoGap, boolean notOwner) {
        this.stack = stack;
        this.maid = maid;
        this.baubleSlot = baubleSlot;
        this.terminal = terminal;
        this.maidName = maidName;
        this.linkMissing = linkMissing;
        this.maidHasNoGap = maidHasNoGap;
        this.notOwner = notOwner;
    }

    public static GapTarget held(ItemStack stack) {
        return new GapTarget(stack, null, -1, false, "", stack.isEmpty(), false, false);
    }

    public static GapTarget emptyTerminal(String savedName) {
        return new GapTarget(ItemStack.EMPTY, null, -1, true, savedName, true, false, false);
    }

    public static GapTarget maidWithoutGap(Object maid, String name) {
        return new GapTarget(ItemStack.EMPTY, maid, -1, true, name, false, true, false);
    }

    /** 链接的女仆是别人的：既不给改，也别提示“她没戴隙间”误导玩家。 */
    public static GapTarget ofOtherPlayer(String savedName) {
        return new GapTarget(ItemStack.EMPTY, null, -1, true, savedName, false, false, true);
    }

    public static GapTarget ofMaid(ItemStack stack, Object maid, int baubleSlot, String name) {
        return new GapTarget(stack, maid, baubleSlot, true, name, false, false, false);
    }
}
