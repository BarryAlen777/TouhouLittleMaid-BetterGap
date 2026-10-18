package cn.blockforge.generated.maidbettergap.gap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 隙间终端里的「链接」数据：记录终端当前连着哪只女仆（UUID + 展示名）。
 * 只碰物品 NBT，不引用车万女仆的任何类，没装前置也能安全调用。
 */
public final class GapLinkData {

    public static final String K_LINK_UUID = "MGLinkMaid";
    public static final String K_LINK_NAME = "MGLinkName";

    private GapLinkData() {}

    public static boolean hasLink(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(K_LINK_UUID);
    }

    @Nullable
    public static UUID linkUuid(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(K_LINK_UUID)) {
            return null;
        }
        try {
            return UUID.fromString(tag.getString(K_LINK_UUID));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 链接时记下女仆的展示名，女仆不在附近时界面也能显示“连的是谁”。 */
    public static String savedName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(K_LINK_NAME);
    }

    public static void link(ItemStack stack, UUID maidUuid, String displayName) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(K_LINK_UUID, maidUuid.toString());
        tag.putString(K_LINK_NAME, displayName == null ? "" : displayName);
    }

    public static void unlink(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(K_LINK_UUID);
            tag.remove(K_LINK_NAME);
        }
    }
}
