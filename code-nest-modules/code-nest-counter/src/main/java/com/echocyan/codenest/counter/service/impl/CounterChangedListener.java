package com.echocyan.codenest.counter.service.impl;

import com.echocyan.codenest.counter.event.CounterChangedEvent;
import com.echocyan.codenest.framework.mq.EventQueues;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 计数变更事件消费者。
 *
 * <p>监听并消费 {@link CounterChangedEvent}，通过 Redis Lua 脚本实现更新与去重的原子操作，
 * 避免依赖关系型数据库防重表带来的额外锁开销。
 */
@Component
@RequiredArgsConstructor
class CounterChangedListener {

    /**
     * 计数更新队列名称。
     */
    static final String QUEUE = "counter.update";

    private final RedisCounterStore redisCounterStore;

    /**
     * 声明计数更新专用的 RabbitMQ 队列、交换机及路由绑定。
     *
     * @return AMQP 声明对象集合
     */
    @Bean
    static Declarables counterUpdateQueue() {
        return EventQueues.declare(QUEUE, CounterChangedListener.class);
    }

    /**
     * 消费计数变更事件。
     *
     * <p>按 {@code messageId:index} 为事件内每项变更生成细粒度去重 Key，
     * 确保因中途异常重试时，已执行成功的变更项不会被重复累加。
     *
     * @param event     计数变更领域事件
     * @param messageId AMQP 消息全局唯一标识
     */
    @RabbitListener(queues = QUEUE)
    public void consume(CounterChangedEvent event, @Header(AmqpHeaders.MESSAGE_ID) String messageId) {
        List<CounterChangedEvent.Change> changes = event.changes();
        for (int i = 0; i < changes.size(); i++) {
            CounterChangedEvent.Change change = changes.get(i);
            redisCounterStore.increment(change.metric(), change.targetId(), change.delta(), messageId + ":" + i);
        }
    }
}
