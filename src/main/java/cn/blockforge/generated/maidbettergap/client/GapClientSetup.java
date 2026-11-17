package cn.blockforge.generated.maidbettergap.client;

import cn.blockforge.generated.maidbettergap.GeneratedMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

// 【P7（r45 复审）】按键注册改用注解挂到 MOD 总线：扫描器按 Dist 决定加不加载这个类，
// 专用服务器不会碰它，主类 GeneratedMod 里也就不必再 import 任何客户端类。
// 不用写 bus = Bus.MOD：1.21.1 的加载器扫描器自己看监听方法的总线类型
// （RegisterKeyMappingsEvent 实现 IModBusEvent 就自动挂 MOD 总线，loader 4.0.44 字节码核实），
// bus 属性已被废弃标记待移除，写了反而多一条编译警告。
@EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT)
public final class GapClientSetup {

    private GapClientSetup() {}

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(ModKeys.OPEN_TERMINAL);
    }
}
