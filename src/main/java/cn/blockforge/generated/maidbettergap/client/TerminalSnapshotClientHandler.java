package cn.blockforge.generated.maidbettergap.client;

import cn.blockforge.generated.maidbettergap.network.TerminalSnapshotPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.network.NetworkEvent;

/**
 * 终端快照包的【客户端】处理逻辑。
 *
 * 【为什么单独放这个类】专用服务器没有画面，Forge 禁止服务端加载 Minecraft 的界面类
 * （Screen 等）。如果 TerminalSnapshotPacket 自己的代码里直接引用这些类，服务端在注册
 * 网络包、校验这个类的字节码时就会崩溃。把界面操作挪到只有客户端才会加载的类里，
 * 服务端的通用代码永远不碰客户端类，就安全了。
 */
public final class TerminalSnapshotClientHandler {

    private TerminalSnapshotClientHandler() {}

    /** consumerMainThread：已经处于主线程，可以直接开/刷新界面。 */
    public static void handle(TerminalSnapshotPacket packet, NetworkEvent.Context ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (packet.open) {
            if (!(mc.screen instanceof GapTerminalScreen)) {
                mc.setScreen(new GapTerminalScreen(packet.snapshot));
            } else {
                ((GapTerminalScreen) mc.screen).updateSnapshot(packet.snapshot);
            }
        } else if (mc.screen instanceof GapTerminalScreen screen) {
            screen.updateSnapshot(packet.snapshot);
        }
        ctx.setPacketHandled(true);
    }
}
