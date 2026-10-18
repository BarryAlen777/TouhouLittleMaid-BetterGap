package cn.blockforge.generated.maidbettergap;

import cn.blockforge.generated.maidbettergap.item.GapTerminalItem;
import cn.blockforge.generated.maidbettergap.item.MaidGapTerminalItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, GeneratedMod.MOD_ID);

    /** 更好的隙间：放进女仆饰品栏自动搬运；拿在手里也能直接配置（绑定数据存在物品 NBT）。 */
    public static final RegistryObject<Item> MAID_GAP_TERMINAL =
            ITEMS.register("maid_gap_terminal", MaidGapTerminalItem::new);

    /** 隙间终端：不用取下隙间，直接远程设置女仆饰品栏里的「更好的隙间」，实时生效。 */
    public static final RegistryObject<Item> GAP_TERMINAL =
            ITEMS.register("gap_terminal", GapTerminalItem::new);

    private ModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }

    /** 加入原版「工具与装备」创造标签。 */
    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(MAID_GAP_TERMINAL.get());
            event.accept(GAP_TERMINAL.get());
        }
    }
}
