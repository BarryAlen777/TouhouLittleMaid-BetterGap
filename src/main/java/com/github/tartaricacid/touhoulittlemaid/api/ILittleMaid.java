// 编译桩：与 TouhouLittleMaid 真实接口同全限定名，构建时已从 jar 中排除（见 build.gradle）。
// 真实接口还有大量 default 方法（Task/Backpack/ChestType 等），这里只保留本模组用到的部分；
// 运行时由 Java 的接口默认方法机制补齐，互不影响。
package com.github.tartaricacid.touhoulittlemaid.api;

import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;

public interface ILittleMaid {
    /**
     * 为物品绑定女仆饰品属性
     *
     * @param manager 注册器
     */
    default void bindMaidBauble(BaubleManager manager) {
    }
}
