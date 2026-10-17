package com.echocyan.codenest.framework.ratelimit;

import com.echocyan.codenest.framework.web.WebMvcConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 限流组件自动装配配置。
 *
 * <p>当 {@code rate-limit.enabled=true} 时生效，扫描 Controller 方法构建规则映射，
 * 注册限流拦截器（{@code order = 1}，位于认证拦截器之后）。</p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "rate-limit.enabled", havingValue = "true")
@EnableConfigurationProperties(RateLimitProperties.class)
@RequiredArgsConstructor
public class RateLimitConfig implements WebMvcConfigurer {

    /**
     * 拦截器执行优先级顺序号（{@code order = 1}），晚于 Sa-Token 认证拦截器（默认 order 0）。
     */
    private static final int ORDER = 1;

    private final SlidingWindowRateLimiter limiter;
    private final RateLimitProperties properties;
    private final ListableBeanFactory beanFactory;

    /**
     * 注册并配置限流拦截器。
     *
     * @param registry Spring MVC 拦截器注册中心
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        RateLimitRules rules = new RateLimitRules(controllerMethods(), properties.rules());
        registry.addInterceptor(new RateLimitInterceptor(limiter, rules, properties))
                .addPathPatterns(WebMvcConfig.API_PREFIX + "/**")
                .order(ORDER);
    }

    /**
     * 内省获取全站所有 Controller 类的唯一处理方法集合。
     *
     * <p>仅基于 BeanFactory 元数据中的类型信息进行方法反射收集，
     * 绝不触发 Controller Bean 的提前实例化，保障 Spring 上下文加载时序的安全。
     *
     * @return 业务 Controller 声明的唯一方法列表
     */
    private List<Method> controllerMethods() {
        return Stream.of(beanFactory.getBeanNamesForAnnotation(Controller.class))
                .map(beanFactory::getType)
                .filter(Objects::nonNull)
                .map(ClassUtils::getUserClass)
                .flatMap(type -> Stream.of(ReflectionUtils.getUniqueDeclaredMethods(type)))
                .toList();
    }
}
