package com.echocyan.codenest.framework.mq;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 事务发件箱（Transactional Outbox）记录持久化实体类。
 *
 * <p>映射底层数据表 {@code mq_outbox}，负责在生产端本地事务中完整留存待发布或已发布的领域事件元数据与载荷：
 * <ul>
 *   <li><b>消息 ID 一致性：</b>主键 {@link #getId()} 兼作发送至 RabbitMQ 的 AMQP 标准属性 {@code message_id}；</li>
 *   <li><b>状态生命周期：</b>初始状态为 {@link OutboxStatus#PENDING}；
 *       收到发布确认后由 {@link OutboxSentMarker} 异步更新为 {@link OutboxStatus#SENT}；
 *       多次补发失败或超时后由 {@link OutboxRelay} 标记为 {@link OutboxStatus#FAILED}。</li>
 * </ul>
 */
@Getter
@Setter
@TableName("mq_outbox")
public class MqOutbox extends AuditableEntity {

    /**
     * 发件箱主键雪花 ID，全局唯一，兼作投递消息的 {@code message_id}。
     */
    private Long id;

    /**
     * AMQP Topic 交换机路由键（格式为 {@code <模块>.<事件>}）。
     */
    private String routingKey;

    /**
     * 领域事件类的全限定类名，用于消费端反序列化时映射目标 Class。
     */
    private String eventType;

    /**
     * 领域事件序列化后的 JSON 字符串载荷（Payload）。
     */
    private String payload;

    /**
     * 消息投递的当前生命周期状态。
     */
    private OutboxStatus status;

    /**
     * 累计补发失败重试次数。
     */
    private Integer retryCount;

    /**
     * 下次允许执行补发的时间戳。
     */
    private LocalDateTime nextRetryAt;
}
