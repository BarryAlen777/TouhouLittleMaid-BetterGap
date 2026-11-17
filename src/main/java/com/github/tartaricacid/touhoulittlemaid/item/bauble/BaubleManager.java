// 编译桩：与 TouhouLittleMaid 真实类同全限定名，构建时已从 jar 中排除（见 build.gradle）。
// 运行时由真实 BaubleManager 提供实现；这里只保留扩展注册用到的 bind 方法签名。
// （旧版桩里还有个 bind(RegistryObject/DeferredHolder, …) 重载：本模组只用 Item 直签那一个，
//  且 1.21.1 的 RegistryObject 已改名 DeferredHolder，删掉免得编译期误用。）
package com.github.tartaricacid.touhoulittlemaid.item.bauble;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import net.minecraft.world.item.Item;

public final class BaubleManager {
    public void bind(Item item, IMaidBauble bauble) {
    }
}
