// 编译桩：与 TouhouLittleMaid 真实接口同全限定名，构建时已从 jar 中排除（见 build.gradle）。
// 真实接口还有 onInjured/onDeath/onPutOn 等 default 方法，运行时由真实接口提供，互不影响。
package com.github.tartaricacid.touhoulittlemaid.api.bauble;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;

public interface IMaidBauble {
    /**
     * 当女仆装备饰品时，每 tick 都会执行此方法
     *
     * @param maid       EntityMaid
     * @param baubleItem ItemStack
     */
    default void onTick(EntityMaid maid, ItemStack baubleItem) {
    }
}
