package cn.blockforge.generated.maidbettergap.gap;

import net.minecraft.core.BlockPos;

/**
 * 一条绑定记录：某个维度某个坐标的容器（物品或流体）。
 */
public record Binding(String dimension, BlockPos pos, byte kind) {

    public static final byte KIND_ITEM = 0;
    public static final byte KIND_FLUID = 1;

    public boolean isFluid() {
        return kind == KIND_FLUID;
    }

    public String key() {
        return dimension + ";" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
