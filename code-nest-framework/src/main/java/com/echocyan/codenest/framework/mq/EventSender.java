package com.echocyan.codenest.framework.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * 领域事件底层 AMQP 发送执行器。
 *
 * <p>将持久化消息推入 RabbitMQ Topic 交换机（{@value EventQueues#EXCHANGE}），
 * 并在收到 Broker ACK 且未发生路由退回时异步完成 {@link CompletableFuture}。</p>
 */
@Component
@RequiredArgsConstructor
class EventSender {

    /**
     * Spring AMQP RabbitMQ 客户端交互模板。
     */
    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送单条领域事件消息并返回投递确认 Future。
     *
     * @param messageId  全局唯一消息标识 ID（对应 {@code mq_outbox.id}）
     * @param routingKey AMQP 路由键（格式为 {@code <模块>.<事件>}）
     * @param eventType  领域事件类的全限定类名（写入 {@code type} 属性）
     * @param payload    事件序列化后的 JSON 字符串
     * @return 异步确认结果 Future：当且仅当 Broker 成功 ACK 且未发生路由退回时结果为 {@code true}；
     * 若被 Broker NACK 或发生无队列退回则结果为 {@code false}；若发送期网络故障则 Future 异常完成
     */
    CompletableFuture<Boolean> send(String messageId, String routingKey, String eventType, String payload) {
        Message message = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setMessageId(messageId)
                .setType(eventType)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
        CorrelationData correlation = new CorrelationData(messageId);
        try {
            rabbitTemplate.send(EventQueues.EXCHANGE, routingKey, message, correlation);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        // 退回先于 confirm 到达，confirm 完成时已能看到退回结果
        return correlation.getFuture().thenApply(confirm -> confirm.ack() && correlation.getReturned() == null);
    }
}
