package com.echocyan.codenest.framework.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controller 方法级滑动窗口限流注解。
 *
 * <p>通过 {@link #value()} 关联配置文件中的 {@code rate-limit.rules.<name>} 规则。
 * 支持重复标注以实现多周期复合限流，超限时抛出 {@link RateLimitedException}。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimits.class)
public @interface RateLimit {

    /**
     * 限流规则配置名称。
     *
     * <p>必须在配置文件中的 {@code rate-limit.rules} 节点下显式定义。
     * 若应用启动时发现引用的规则名未配置或属性缺失，将触发强校验阻断服务启动。
     *
     * @return 规则名标识
     */
    String value();

    /**
     * 限流统计的主体维度枚举。
     */
    enum Dimension {

        /**
         * 按当前登录用户 ID 统计限额。
         *
         * <p>仅适用于受保护的登录接口；禁止用于标有 {@link cn.dev33.satoken.annotation.SaIgnore} 的匿名公开接口
         * （启动时会有强校验拦截）。
         */
        USER,

        /**
         * 按客户端公网 IP 地址统计限额。
         *
         * <p>专用于注册、登录或匿名公开接口的防刷与防爆破拦截。
         * 仅在请求直接来源自可信代理（{@code trusted-proxies}）时，才会逆向解析 {@code X-Forwarded-For} 请求头提取真实客户端 IP。
         */
        IP
    }
}
