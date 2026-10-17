package com.echocyan.codenest.framework.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 限流防刷全局配置属性绑定类。
 *
 * <p>映射应用配置文件中以 {@code rate-limit} 为前缀的配置项集合：
 * <ul>
 *   <li>{@code rate-limit.enabled}：限流总开关（由 {@link RateLimitConfig} 作为条件装配使用）；</li>
 *   <li>{@code rate-limit.trusted-proxies}：网关与反向代理的可信 IP 白名单；</li>
 *   <li>{@code rate-limit.rules}：以规则名称为 Key 的精细化频控规则字典。</li>
 * </ul>
 *
 * @param trustedProxies 可信反向代理或网关的 IP 地址列表；仅当请求的对端直连 IP 存在于该列表中时，才允许解析 {@code X-Forwarded-For} 请求头
 * @param rules          限流规则定义字典；Key 为规则名（与 {@link RateLimit#value()} 一一对应），Value 为规则详细参数
 */
@ConfigurationProperties("rate-limit")
public record RateLimitProperties(List<String> trustedProxies, Map<String, RuleProperties> rules) {

    /**
     * 规范化紧凑构造方法，提供防篡改保护与空值防御。
     *
     * @param trustedProxies 可信代理列表
     * @param rules          规则配置字典
     */
    public RateLimitProperties {
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
        rules = rules == null ? Map.of() : Map.copyOf(rules);
    }

    /**
     * 单条限流规则参数定义属性 Record。
     *
     * @param limit     滑动窗口内允许通过的最大请求次数上限（必须为正整数）
     * @param window    滑动窗口的时间跨度长度（如 {@code 1m}、{@code 1h}、{@code 1d}）
     * @param dimension 限流统计的主体维度（{@link RateLimit.Dimension#USER} 或 {@link RateLimit.Dimension#IP}）
     */
    public record RuleProperties(Integer limit, Duration window, RateLimit.Dimension dimension) {
    }
}
