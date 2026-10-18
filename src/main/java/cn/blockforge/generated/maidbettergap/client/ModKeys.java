package cn.blockforge.generated.maidbettergap.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** 键位：打开隙间终端（默认 G，可在控制设置里改）。 */
public final class ModKeys {

    public static final KeyMapping OPEN_TERMINAL = new KeyMapping(
            "key.maidgap.open_terminal",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.maidgap");

    private ModKeys() {}
}
