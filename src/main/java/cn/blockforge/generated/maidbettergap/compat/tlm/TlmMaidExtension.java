package cn.blockforge.generated.maidbettergap.compat.tlm;

import cn.blockforge.generated.maidbettergap.MaidGapConfig;
import cn.blockforge.generated.maidbettergap.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;

/**
 * 车万女仆（TouhouLittleMaid）扩展入口。
 * 本类由车万女仆自己通过 @LittleMaidExtension 注解扫描加载，
 * 只在装了车万女仆的客户端/服务端才会被实例化；没装时本模组照常独立运行。
 *
 * 在这里完成两件事：
 * 1. 把我们的「隙间·增强终端」注册成女仆饰品 —— 可以放进女仆的饰品栏，由女仆佩戴工作；
 * 2. 把原版「隙间」(touhou_little_maid:wireless_io) 的饰品逻辑替换成增强版
 *    （扩展注册发生在原版注册之后，同一个物品的绑定会被我们覆盖）。
 */
@LittleMaidExtension
public class TlmMaidExtension implements ILittleMaid {

    public static final String TLM_MOD_ID = "touhou_little_maid";
    public static final ResourceLocation ORIGINAL_GAP_ID =
            ResourceLocation.fromNamespaceAndPath(TLM_MOD_ID, "wireless_io");

    public TlmMaidExtension() {
    }

    @Override
    public void bindMaidBauble(BaubleManager manager) {
        // 1) 我们自己的增强终端：可进女仆饰品栏
        manager.bind(ModItems.MAID_GAP_TERMINAL.get(), EnhancedGapBauble.INSTANCE);

        // 2) 直接增强原模组隙间（配置可关）
        if (MaidGapConfig.ENHANCE_ORIGINAL_GAP.get()) {
            Item original = ForgeRegistries.ITEMS.getValue(ORIGINAL_GAP_ID);
            if (original != null) {
                manager.bind(original, EnhancedGapBauble.INSTANCE);
            }
        }

        // 3) 配置里额外登记的隙间物品也一并变成饰品
        List<? extends String> extra = MaidGapConfig.EXTRA_GAP_ITEMS.get();
        if (extra != null) {
            for (String id : extra) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                ResourceLocation rl = ResourceLocation.tryParse(id.trim().toLowerCase(Locale.ROOT));
                if (rl == null || rl.equals(ORIGINAL_GAP_ID)) {
                    continue;
                }
                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item != null) {
                    manager.bind(item, EnhancedGapBauble.INSTANCE);
                }
            }
        }
    }
}
