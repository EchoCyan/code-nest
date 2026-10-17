package com.echocyan.codenest.framework.mq;

import java.lang.annotation.*;

/**
 * 领域事件契约注解。
 *
 * <p>标注于不可变事件 Record 上。{@link #value()} 指定路由键，
 * 同时作为消费端反序列化的安全白名单凭证。</p>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DomainEvent {

    /**
     * 领域事件在统一交换机中的 AMQP 路由键（Routing Key）。
     *
     * <p>标准命名规范为 {@code <生产模块>.<事件名称>}（如 {@code article.published}、{@code comment.created}）。
     *
     * @return 路由键字符串
     */
    String value();
}
