package com.echocyan.codenest.framework.ratelimit;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.framework.ratelimit.RateLimitProperties.RuleProperties;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 限流规则映射注册表与启动期校验器。
 *
 * <p>启动时预编译 Controller 方法与 {@link RateLimit} 规则的映射，并执行 Fail-Fast 校验：
 * 验证配置完整性，且确保 {@link RateLimit.Dimension#USER} 规则不作用于 {@link SaIgnore} 匿名端点。</p>
 */
final class RateLimitRules {

    /**
     * Controller 方法到其绑定的校验后规则列表的映射表。
     */
    private final Map<Method, List<Rule>> rules = new HashMap<>();

    /**
     * 构造限流规则映射表并执行启动期严格校验。
     *
     * @param methods    全站所有 Controller 类的处理方法集合
     * @param configured 配置文件中声明的规则字典（即 {@code rate-limit.rules}）
     * @throws IllegalStateException 若引用的规则未配置、配置项残缺，或匿名公开方法错误配置了用户维度限流
     */
    RateLimitRules(Collection<Method> methods, Map<String, RuleProperties> configured) {
        for (Method method : methods) {
            List<Rule> methodRules = AnnotatedElementUtils.findMergedRepeatableAnnotations(method, RateLimit.class)
                    .stream()
                    .map(rateLimit -> ruleOf(rateLimit.value(), configured.get(rateLimit.value()), method))
                    .toList();
            if (!methodRules.isEmpty()) {
                rules.put(method, methodRules);
            }
        }
    }

    /**
     * 校验并构建单条已解析的不可变运行时规则对象。
     *
     * @param key        规则配置名称
     * @param properties 对应的配置属性对象
     * @param method     声明该注解的目标处理方法
     * @return 经过合法性检验的不可变运行时规则对象 {@link Rule}
     * @throws IllegalStateException 若配置缺失或维度与端点安全属性冲突
     */
    private static Rule ruleOf(String key, RuleProperties properties, Method method) {
        if (properties == null || properties.limit() == null || properties.window() == null
                || properties.dimension() == null) {
            throw new IllegalStateException("Rate limit rule '%s' used on %s is not fully configured under rate-limit.rules"
                    .formatted(key, method.toGenericString()));
        }
        if (properties.dimension() == RateLimit.Dimension.USER && isAnonymous(method)) {
            throw new IllegalStateException("Rate limit rule '%s' counts per user but %s allows anonymous access"
                    .formatted(key, method.toGenericString()));
        }
        return new Rule(key, properties.limit(), properties.window(), properties.dimension());
    }

    /**
     * 检查目标方法或其所属 Controller 类是否标有 {@link SaIgnore} 放行标记。
     *
     * @param method 待检测的处理方法
     * @return {@code true} 表示属于免登录匿名公开端点
     */
    private static boolean isAnonymous(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, SaIgnore.class)
                || AnnotatedElementUtils.hasAnnotation(method.getDeclaringClass(), SaIgnore.class);
    }

    /**
     * 查询指定 Controller 方法绑定的全部限流规则列表。
     *
     * @param method 目标处理方法
     * @return 该方法对应的限流规则列表；若未标注任何 {@link RateLimit} 则返回不可变的空列表
     */
    List<Rule> of(Method method) {
        return rules.getOrDefault(method, List.of());
    }

    /**
     * 经过启动校验后的单个运行时限流规则不可变 Record。
     *
     * @param key       规则全局配置标识 Key
     * @param limit     窗口期内允许的最大请求阈值
     * @param window    窗口时间跨度
     * @param dimension 限流统计维度（用户或客户端 IP）
     */
    record Rule(String key, int limit, Duration window, RateLimit.Dimension dimension) {
    }
}
