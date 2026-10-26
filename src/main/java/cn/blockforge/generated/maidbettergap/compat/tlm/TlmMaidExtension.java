package cn.blockforge.generated.maidbettergap.compat.tlm;

import cn.blockforge.generated.maidbettergap.MaidGapConfig;
import cn.blockforge.generated.maidbettergap.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
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
 *
 * 注意：本类里的方法都是「别人（车万女仆）调我们」，一旦抛出去就是打断对方的启动流程，
 * 所以每个注册动作都自带兜底（见 bindMaidBauble）。
 */
@LittleMaidExtension
public class TlmMaidExtension implements ILittleMaid {

    public static final String TLM_MOD_ID = "touhou_little_maid";
    public static final ResourceLocation ORIGINAL_GAP_ID =
            ResourceLocation.fromNamespaceAndPath(TLM_MOD_ID, "wireless_io");

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public TlmMaidExtension() {
    }

    @Override
    public void bindMaidBauble(BaubleManager manager) {
        if (manager == null) {
            return;
        }
        try {
            bindAll(manager);
        } catch (LinkageError err) {
            // 车万女仆版本对不上时抛的是 Error 而不是 Exception（mods.toml 里声明的是宽区间 [1.0,)）。
            // 这里如果放出去，会直接打断车万女仆自己的饰品注册扫描：轻则所有女仆饰品失效，
            // 重则开服即崩且日志里只剩一行看不懂的 NoSuchMethodError。丢这一次、留日志即可。
            LOGGER.error("[maid_better_gap] 与车万女仆的饰品接口不匹配，本次饰品注册已跳过；"
                    + "请把本模组与车万女仆更新到互相匹配的版本。", err);
        } catch (Exception ex) {
            LOGGER.error("[maid_better_gap] 注册女仆饰品时出错，本次饰品注册已跳过。", ex);
        }
    }

    private static void bindAll(BaubleManager manager) {
        IMaidBauble bauble = EnhancedGapBauble.INSTANCE;

        // 1) 我们自己的增强终端：可进女仆饰品栏
        bindOne(manager, bauble, ModItems.MAID_GAP_TERMINAL.get());

        // 2) 直接增强原模组隙间（配置可关）
        if (MaidGapConfig.ENHANCE_ORIGINAL_GAP.get()) {
            bindOne(manager, bauble, ForgeRegistries.ITEMS.getValue(ORIGINAL_GAP_ID));
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
                bindOne(manager, bauble, ForgeRegistries.ITEMS.getValue(rl));
            }
        }
    }

    /**
     * 单个物品的绑定：失败只跳过这一个，不牵连其它物品。
     * 物品为 null（对方的物品还没注册、或配置里写了不存在的 id）时直接跳过。
     */
    private static void bindOne(BaubleManager manager, IMaidBauble bauble, Item item) {
        if (item == null) {
            return;
        }
        try {
            manager.bind(item, bauble);
        } catch (LinkageError err) {
            LOGGER.error("[maid_better_gap] 饰品接口不匹配，已跳过 {} 的饰品注册。",
                    ForgeRegistries.ITEMS.getKey(item), err);
        } catch (Exception ex) {
            LOGGER.error("[maid_better_gap] 饰品注册失败，已跳过 {}。",
                    ForgeRegistries.ITEMS.getKey(item), ex);
        }
    }
}
