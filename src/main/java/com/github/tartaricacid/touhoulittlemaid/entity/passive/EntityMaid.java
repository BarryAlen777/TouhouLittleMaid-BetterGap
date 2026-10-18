// 编译桩：与 TouhouLittleMaid 真实实体同全限定名，构建时已从 jar 中排除（见 build.gradle）。
// 只声明本模组要用到的方法；tickCount / level / distanceToSqr 等继承自真实 Entity，编译期即可用。
package com.github.tartaricacid.touhoulittlemaid.entity.passive;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

public abstract class EntityMaid extends TamableAnimal {
    /** 女仆界面是否打开着（原版隙间在打开界面时暂停传输）。 */
    public boolean guiOpening;

    public EntityMaid(EntityType<? extends TamableAnimal> type, Level level) {
        super(type, level);
    }

    /** 女仆可用背包（主背包 + 副手），与原版签名一致。 */
    public CombinedInvWrapper getAvailableInv(boolean handsFirst) {
        throw new IllegalStateException("stub only");
    }

    /** 女仆饰品栏（隙间终端远程读写的就是它），与原版签名一致。 */
    public com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler getMaidBauble() {
        throw new IllegalStateException("stub only");
    }

    /** 女仆的活动半径，与原版签名一致。 */
    public float getRestrictRadius() {
        throw new IllegalStateException("stub only");
    }
}
