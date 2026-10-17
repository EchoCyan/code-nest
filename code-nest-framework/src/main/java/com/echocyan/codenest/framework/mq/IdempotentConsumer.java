package com.echocyan.codenest.framework.mq;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RabbitMQ 消息消费幂等注解。
 *
 * <p>标注于消费监听方法上，由 {@link IdempotentConsumerAspect} 基于 {@code (queue, message_id)}
 * 数据库唯一记录拦截重复消息，适用于非天然幂等的消费业务。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentConsumer {
}
