package cn.blockforge.generated.maidbettergap.gap;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * 物品自定义数据的读写入口（对应 1.20.1 时代的 {@code stack.getTag()} 系列）。
 *
 * 1.21.1（NeoForge 21.1 后期构建）删掉了 ItemStack 上的旧版 NBT 兼容层，
 * 模组数据一律走 {@link DataComponents#CUSTOM_DATA} 组件——键名与数据结构不变，
 * 只是存取通道换了。语义对齐：
 * <ul>
 *   <li>{@link #get} ≈ 旧 getTag()：没有数据返回 null（多数调用点按 null 判空）；</li>
 *   <li>{@link #has} ≈ 旧 hasTag()：存在且非空；</li>
 *   <li>{@link #update} ≈ 旧 getOrCreateTag() 后连续改写：改完整块写回。</li>
 * </ul>
 * 注意 get() 返回的是<b>副本</b>：在它上面改写不会落到物品上，要改请用 update()。
 */
public final class StackData {

    private StackData() {}

    @Nullable
    public static CompoundTag get(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    public static boolean has(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && !data.isEmpty();
    }

    /** 读→改→写一步完成（mutator 拿到的是可改写的当前数据副本，空数据时是空 CompoundTag）。 */
    public static void update(ItemStack stack, Consumer<CompoundTag> mutator) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, mutator);
    }

    /** 整块替换（旧代码里 tag 换掉的场景）；传 null/空 = 删掉整块数据。 */
    public static void set(ItemStack stack, @Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        }
    }
}
