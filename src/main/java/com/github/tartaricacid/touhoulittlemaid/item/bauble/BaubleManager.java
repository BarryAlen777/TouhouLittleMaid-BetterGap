// 编译桩：与 TouhouLittleMaid 真实类同全限定名，构建时已从 jar 中排除（见 build.gradle）。
// 运行时由真实 BaubleManager 提供实现；这里只保留扩展注册用到的 bind 方法签名。
package com.github.tartaricacid.touhoulittlemaid.item.bauble;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

public final class BaubleManager {
    public void bind(RegistryObject<Item> item, IMaidBauble bauble) {
    }

    public void bind(Item item, IMaidBauble bauble) {
    }
}
