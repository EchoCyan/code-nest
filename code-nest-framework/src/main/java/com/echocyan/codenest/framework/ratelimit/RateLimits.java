package com.echocyan.codenest.framework.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link RateLimit} 重复注解容器。
 *
 * <p>本注解属于 Java 语言规范中 {@link Repeatable} 的标准容器注解，
 * 由编译器在同一个 Controller 方法上多次声明 {@link RateLimit} 时自动合成与解析，
 * 业务代码中无需手动显式声明。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimits {

    /**
     * 包含的多个 {@link RateLimit} 规则注解实例数组。
     *
     * @return 限流注解数组
     */
    RateLimit[] value();
}
