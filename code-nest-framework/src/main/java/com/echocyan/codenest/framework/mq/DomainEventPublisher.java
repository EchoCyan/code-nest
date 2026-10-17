package com.echocyan.codenest.framework.mq;

import com.echocyan.codenest.common.util.DateTimes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

/**
 * 领域事件发布组件（Transactional Outbox 模式）。
 *
 * <p>保障跨服务/模块事件投递的最终一致性：
 * <ul>
 *   <li><b>事务同批落库：</b>必须在活跃事务中调用，事件载荷同业务操作持久化至 {@code mq_outbox} 表；</li>
 *   <li><b>提交后异步投递：</b>通过 {@code afterCommit} 钩子推入 RabbitMQ，收到 ACK 后异步标记为 {@link OutboxStatus#SENT}；</li>
 *   <li><b>失败补发兜底：</b>投递未成功或未确认的消息由 {@link OutboxRelay} 定时重试。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    /**
     * 等待 RabbitMQ Broker Publisher Confirm 确认的最长超时时限（5 秒）。
     */
    static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Outbox 数据库持久化访问 Mapper。
     */
    private final MqOutboxMapper outboxMapper;

    /**
     * 底层 AMQP 消息发送执行器。
     */
    private final EventSender sender;

    /**
     * 事件 Payload 序列化工具。
     */
    private final JsonMapper jsonMapper;

    /**
     * 已确认消息批量状态更新器。
     */
    private final OutboxSentMarker sentMarker;

    /**
     * 在当前活跃事务中发布领域事件。
     *
     * <p>事件类型必须标注 {@link DomainEvent} 注解以提取 AMQP 路由键。
     * 首次重试延迟将留出事务提交与首次异步发送的窗口期，避免定时任务过早抢发。
     *
     * @param event 待发布的领域事件对象（必须标注了 {@link DomainEvent}）
     * @throws IllegalArgumentException 若事件类缺少 {@link DomainEvent} 注解
     * @throws IllegalStateException    若当前执行线程中不存在活跃的本地数据库事务
     */
    public void publish(Object event) {
        DomainEvent domainEvent = event.getClass().getAnnotation(DomainEvent.class);
        if (domainEvent == null) {
            throw new IllegalArgumentException(event.getClass().getName() + " is not annotated with @DomainEvent");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Domain events must be published in a transaction: " + event.getClass().getName());
        }
        MqOutbox outbox = new MqOutbox();
        outbox.setRoutingKey(domainEvent.value());
        outbox.setEventType(event.getClass().getName());
        outbox.setPayload(jsonMapper.writeValueAsString(event));
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setRetryCount(0);
        // 留出 afterCommit 发送与 confirm 的时间，补发任务不会与之抢发
        outbox.setNextRetryAt(OutboxRelay.nextRetryAt(DateTimes.now(), 0));
        outboxMapper.insert(outbox);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendOutbox(outbox);
            }
        });
    }

    /**
     * 事务成功提交后异步投递 Outbox 消息并注册确认回调。
     *
     * <p>此时本地事务已完全提交，本方法内的任何网络异常仅记录日志，
     * 未确认的消息交由后台补发定时任务处理。
     *
     * @param outbox 待投递的发件箱记录实体
     */
    private void sendOutbox(MqOutbox outbox) {
        long id = outbox.getId();
        sender.send(String.valueOf(id), outbox.getRoutingKey(), outbox.getEventType(), outbox.getPayload())
                .whenComplete((acked, error) -> {
                    if (Boolean.TRUE.equals(acked)) {
                        sentMarker.markSent(id);
                    } else {
                        log.warn("Outbox event not confirmed, left for relay: id={}", id, error);
                    }
                });
    }
}
