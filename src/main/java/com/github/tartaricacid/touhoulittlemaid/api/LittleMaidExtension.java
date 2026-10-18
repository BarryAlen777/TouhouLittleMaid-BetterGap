// 编译桩：与 TouhouLittleMaid 真实类同全限定名，构建时已从 jar 中排除（见 build.gradle），
// 运行时加载的是车万女仆模组自己的类。
package com.github.tartaricacid.touhoulittlemaid.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation lets touhou little maid mod detect mod extension.
 * All {@link ILittleMaid} must have this annotation and a constructor with no arguments.
 *
 * 桩里必须写 RUNTIME：javac 是按「编译期看到的保留策略」决定把注解写进
 * RuntimeVisible 还是 RuntimeInvisible 属性的。如果只写默认的 CLASS，
 * 车万女仆用反射扫注解时就找不到本模组的扩展类，饰品注册不上。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LittleMaidExtension {
}
