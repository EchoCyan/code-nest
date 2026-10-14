package com.echocyan.codenest.framework.mq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 跨模块事件消息队列声明工具类。
 * <p>
 * 集中管理系统消息拓扑的默认规范：
 * <ul>
 *   <li>事件分发：所有领域事件发布到统一的 Topic 交换机 {@link #EXCHANGE}（{@value #EXCHANGE}）；</li>
 *   <li>死信隔离：消费重试耗尽的消息经 Direct 交换机 {@link #DEAD_LETTER_EXCHANGE}（{@value #DEAD_LETTER_EXCHANGE}）转入专属死信队列 {@code
 *   <queue>.dlq}；</li>
 *   <li>可靠存储：交换机和队列均持久化，队列采用 Classic 模式。</li>
 * </ul>
 * 业务模块无需手动配置繁琐的 Binding，通常直接调用 {@link #declare(String, Class)} 即可由监听方法自动推导出订阅关系。
 */
public final class EventQueues {

    public static final String EXCHANGE = "codenest.events";

    public static final String DEAD_LETTER_EXCHANGE = "codenest.dlx";

    private EventQueues() {
    }

    /**
     * 声明一个业务消费队列及其专属死信队列，并自动解析监听器订阅的事件完成交换机绑定。
     * <p>
     * 监听器识别规则：
     * <ul>
     *   <li>类上标注 {@link RabbitListener} 时：扫描其内部标注了 {@link RabbitHandler} 的方法；</li>
     *   <li>类上未标注时：扫描方法级别标注了 {@link RabbitListener} 且包含当前队列名的方法。</li>
     * </ul>
     * <p>
     * <b>用法：</b>在消费模块的监听器类中声明为静态 Bean：
     * <pre>{@code
     * @Bean
     * static Declarables notificationQueue() {
     *     return EventQueues.declare(QUEUE, NotificationListener.class);
     * }
     * }</pre>
     *
     * @param queue    队列名，格式为 {@code <消费模块>.<用途>}
     * @param listener 处理该队列消息的监听器类 Class
     * @return 包含主队列、死信队列、死信绑定及所有事件路由绑定的 {@link Declarables} 容器
     * @throws IllegalStateException 若类未监听该队列、未找到任何处理方法、或方法中缺少标注了 {@link DomainEvent} 的事件参数
     */
    public static Declarables declare(String queue, Class<?> listener) {
        return declareEvents(queue, eventTypesOf(queue, listener).toArray(Class<?>[]::new));
    }

    /**
     * 声明消费队列及其专属死信队列，并显式指定订阅的事件类型进行交换机绑定。
     * <p>
     * 若不传 {@code eventTypes}，则只声明主队列和死信队列并建立死信绑定，不绑定主交换机。
     *
     * @param queue      队列名，格式为 {@code <消费模块>.<用途>}
     * @param eventTypes 要订阅的一个或多个事件类（必须标注了 {@link DomainEvent}）
     * @return 包含主队列、死信队列及对应事件路由绑定的 {@link Declarables} 容器
     * @throws IllegalStateException 若某个事件类未标注 {@link DomainEvent}
     */
    public static Declarables declareEvents(String queue, Class<?>... eventTypes) {
        String deadLetterQueue = queue + ".dlq";
        Queue main = QueueBuilder.durable(queue).classic()
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(deadLetterQueue)
                .build();
        Queue dead = QueueBuilder.durable(deadLetterQueue).classic().build();
        List<Declarable> declarables = new ArrayList<>(List.of(main, dead,
                new Binding(deadLetterQueue, Binding.DestinationType.QUEUE, DEAD_LETTER_EXCHANGE, deadLetterQueue,
                        null)));
        for (Class<?> eventType : eventTypes) {
            declarables.add(new Binding(queue, Binding.DestinationType.QUEUE, EXCHANGE, routingKeyOf(eventType),
                    null));
        }
        return new Declarables(declarables);
    }

    /**
     * 读取事件类上声明的路由键。
     *
     * @param eventType 事件类 Class（必须标注了 {@link DomainEvent}）
     * @return 路由键字符串
     * @throws IllegalStateException 若事件类未标注 {@link DomainEvent}
     */
    static String routingKeyOf(Class<?> eventType) {
        DomainEvent domainEvent = eventType.getAnnotation(DomainEvent.class);
        if (domainEvent == null) {
            throw new IllegalStateException(eventType.getName() + " is not annotated with @DomainEvent");
        }
        return domainEvent.value();
    }

    private static List<Class<?>> eventTypesOf(String queue, Class<?> listener) {
        RabbitListener classListener = AnnotatedElementUtils.findMergedAnnotation(listener, RabbitListener.class);
        if (classListener != null && !List.of(classListener.queues()).contains(queue)) {
            throw new IllegalStateException(listener.getName() + " does not listen to " + queue);
        }
        List<Class<?>> eventTypes = Stream.of(ReflectionUtils.getUniqueDeclaredMethods(listener))
                .filter(method -> classListener != null
                        ? AnnotatedElementUtils.hasAnnotation(method, RabbitHandler.class)
                        : listensTo(method, queue))
                .<Class<?>>map(EventQueues::eventTypeOf)
                .distinct()
                .toList();
        if (eventTypes.isEmpty()) {
            throw new IllegalStateException(listener.getName() + " has no handler for " + queue);
        }
        return eventTypes;
    }

    private static boolean listensTo(Method method, String queue) {
        RabbitListener rabbitListener = AnnotatedElementUtils.findMergedAnnotation(method, RabbitListener.class);
        return rabbitListener != null && List.of(rabbitListener.queues()).contains(queue);
    }

    private static Class<?> eventTypeOf(Method method) {
        return Stream.of(method.getParameterTypes())
                .filter(type -> type.isAnnotationPresent(DomainEvent.class))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        method.toGenericString() + " has no parameter annotated with @DomainEvent"));
    }

    static TopicExchange exchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    static DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }
}
