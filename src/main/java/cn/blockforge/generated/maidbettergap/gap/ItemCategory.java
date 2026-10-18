package cn.blockforge.generated.maidbettergap.gap;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * 智能分类：把容器里的物品归到标签页（全部/武器/工具/食物/方块/装备/其他）。
 */
public enum ItemCategory {

    ALL,
    WEAPON,
    TOOL,
    FOOD,
    BLOCK,
    ARMOR,
    OTHER;

    public static ItemCategory of(ItemStack stack) {
        if (stack.isEmpty()) {
            return OTHER;
        }
        if (stack.isEdible()) {
            return FOOD;
        }
        // 工具要先判断：斧、镐这类同时属于 SwordItem / 工具标签
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES) || stack.getItem() instanceof FishingRodItem
                || stack.getItem() instanceof ShearsItem) {
            return TOOL;
        }
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof TridentItem) {
            return WEAPON;
        }
        if (stack.getItem() instanceof ArmorItem || stack.getItem() instanceof ElytraItem) {
            return ARMOR;
        }
        if (stack.getItem() instanceof BlockItem) {
            return BLOCK;
        }
        return OTHER;
    }

    public boolean matches(ItemStack stack) {
        return this == ALL || of(stack) == this;
    }
}
