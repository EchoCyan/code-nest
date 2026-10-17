package com.echocyan.codenest.framework.mq;

import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.ClassMapper;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.util.ClassUtils;

/**
 * 领域事件类型映射器（{@link ClassMapper}）。
 *
 * <p>发送时将事件类名写入消息属性 {@code type}；
 * 接收时反向加载 Class 并校验必须持有 {@link DomainEvent} 注解以防反序列化风险。</p>
 */
class DomainEventClassMapper implements ClassMapper {

    /**
     * 将领域事件类的全限定类名写入 AMQP 消息属性的 {@code type} 字段。
     *
     * @param clazz      领域事件 Class 对象
     * @param properties AMQP 消息属性对象
     */
    @Override
    public void fromClass(Class<?> clazz, MessageProperties properties) {
        properties.setType(clazz.getName());
    }

    /**
     * 从 AMQP 消息属性的 {@code type} 字段反向解析领域事件 Class，并执行安全白名单注解校验。
     *
     * @param properties AMQP 消息属性对象
     * @return 经过安全校验的领域事件 Class 对象
     * @throws MessageConversionException 若缺少 {@code type} 属性、类不存在或未标注 {@link DomainEvent} 注解
     */
    @Override
    public Class<?> toClass(MessageProperties properties) {
        String type = properties.getType();
        if (type == null) {
            throw new MessageConversionException("Message has no type property");
        }
        Class<?> clazz;
        try {
            clazz = ClassUtils.forName(type, DomainEventClassMapper.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new MessageConversionException("Unknown event type " + type, e);
        }
        if (!clazz.isAnnotationPresent(DomainEvent.class)) {
            throw new MessageConversionException(type + " is not annotated with @DomainEvent");
        }
        return clazz;
    }
}
