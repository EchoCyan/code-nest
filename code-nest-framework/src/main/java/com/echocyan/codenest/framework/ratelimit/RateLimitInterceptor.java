package com.echocyan.codenest.framework.ratelimit;

import com.echocyan.codenest.framework.auth.AuthContext;
import com.echocyan.codenest.framework.ratelimit.SlidingWindowRateLimiter.Quota;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Set;

/**
 * Spring MVC 请求限流拦截器。
 *
 * <p>在 Controller 方法执行前依据注解或配置执行限流判定：
 * <ul>
 *   <li><b>多配额原子判定：</b>支持 IP / 用户等多维度复合限流，超限抛出 {@link RateLimitedException}；</li>
 *   <li><b>Fail-Open：</b>Redis 异常时记录日志并安全放行，保证主链路可用；</li>
 *   <li><b>防 IP 伪造：</b>仅当直连对端为可信代理时，从 {@code X-Forwarded-For} 逆向提取客户端真实 IP。</li>
 * </ul>
 * </p>
 */
@Slf4j
class RateLimitInterceptor implements HandlerInterceptor {

    /**
     * HTTP 标准客户端 IP 转发头名称。
     */
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    /**
     * 底层基于 Redis 的滑动窗口限流器。
     */
    private final SlidingWindowRateLimiter limiter;

    /**
     * 经过启动校验的方法到规则映射表。
     */
    private final RateLimitRules rules;

    /**
     * 可信代理 IP 地址白名单集合。
     */
    private final Set<String> trustedProxies;

    /**
     * 构造限流拦截器。
     *
     * @param limiter    滑动窗口限流器组件
     * @param rules      限流规则映射表
     * @param properties 限流配置属性
     */
    RateLimitInterceptor(SlidingWindowRateLimiter limiter, RateLimitRules rules, RateLimitProperties properties) {
        this.limiter = limiter;
        this.rules = rules;
        this.trustedProxies = Set.copyOf(properties.trustedProxies());
    }

    /**
     * 请求预处理前置拦截。
     *
     * @param request  HTTP 请求对象
     * @param response HTTP 响应对象
     * @param handler  目标处理器对象（仅针对 {@link HandlerMethod} 生效）
     * @return {@code true} 表示允许放行继续执行后续拦截器及 Controller 方法
     * @throws RateLimitedException 若请求频次超出设定的滑动窗口配额
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        List<RateLimitRules.Rule> methodRules = rules.of(handlerMethod.getMethod());
        if (methodRules.isEmpty()) {
            return true;
        }
        List<Quota> quotas = methodRules.stream()
                .map(rule -> new Quota(rule.key() + ":" + subject(rule.dimension(), request), rule.limit(),
                        rule.window()))
                .toList();
        long waitMillis;
        try {
            waitMillis = limiter.tryAcquire(quotas);
        } catch (DataAccessException e) {
            log.warn("限流不可用，放行请求: {} {}", request.getMethod(), request.getRequestURI(), e);
            return true;
        }
        if (waitMillis > 0) {
            throw new RateLimitedException(Math.ceilDiv(waitMillis, 1000));
        }
        return true;
    }

    /**
     * 根据维度类型解析本次请求的限流主体标识字符串。
     *
     * @param dimension 限流统计维度
     * @param request   当前 HTTP 请求
     * @return 格式化后的主体标识（如 {@code user:10001} 或 {@code ip:192.168.1.1}）
     */
    private String subject(RateLimit.Dimension dimension, HttpServletRequest request) {
        return switch (dimension) {
            case USER -> "user:" + AuthContext.currentUserId();
            case IP -> "ip:" + clientIp(request);
        };
    }

    /**
     * 安全提取客户端真实公网 IP 地址。
     *
     * <p>防御伪造算法：
     * <ul>
     *   <li>若对端直接连接 IP 不在可信代理白名单内，说明请求未经合规网关直接到达应用，直接信任其 RemoteAddr；</li>
     *   <li>若来自可信代理，对 {@code X-Forwarded-For} 的各跳 IP 列表进行自右往左逆向遍历，
     *       跳过白名单中已知的可信代理跳数，取遇到的第一个非代理 IP；
     *       这是因为反向代理只会将真实的对端 IP 追加在逗号分隔的右侧，客户端伪造的虚假 IP 只能存在于左侧。</li>
     * </ul>
     *
     * @param request 当前 HTTP 请求对象
     * @return 解析得出的真实客户端 IP
     */
    private String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String forwardedFor = request.getHeader(FORWARDED_FOR);
        if (forwardedFor == null || !trustedProxies.contains(remoteAddr)) {
            return remoteAddr;
        }
        String[] hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].strip();
            if (!hop.isEmpty() && !trustedProxies.contains(hop)) {
                return hop;
            }
        }
        return remoteAddr;
    }
}
