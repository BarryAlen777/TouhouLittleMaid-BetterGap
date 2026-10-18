// 编译桩：与 TouhouLittleMaid 真实类同全限定名，构建时已从 jar 中排除（见 build.gradle）。
// 运行时用的就是车万女仆自己的实现；这里只保证本模组能编译。
package com.github.tartaricacid.touhoulittlemaid.inventory.handler;

import net.minecraftforge.items.ItemStackHandler;

/** 女仆饰品栏（真实类继承 ItemStackHandler，槽位读写方法都继承自它）。 */
public class BaubleItemHandler extends ItemStackHandler {
    public BaubleItemHandler() {
        super(1);
    }
}
