package com.echocyan.codenest.framework.mq;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 消息消费防重记录持久化实体类。
 *
 * <p>映射底层数据表 {@code mq_consume_record}，作为 {@link IdempotentConsumer} 切面的防重媒介：
 * <ul>
 *   <li><b>复合唯一索引保障：</b>表内对 {@code (message_id, consumer)} 建立复合唯一索引，
 *       同一条消息在同一个消费队列下仅允许成功插入一次；</li>
 *   <li><b>审计追踪：</b>继承自 {@link AuditableEntity}，自动记录消费时间戳（{@code created_at}）。</li>
 * </ul>
 */
@Getter
@Setter
@TableName("mq_consume_record")
public class MqConsumeRecord extends AuditableEntity {

    /**
     * 记录主键 ID，采用 MyBatis-Plus 雪花算法自动生成。
     */
    private Long id;

    /**
     * AMQP 消息唯一标识 ID（对应发布端生成的 {@code mq_outbox.id}）。
     */
    private String messageId;

    /**
     * 消费队列名称（如 {@code notification.create}），用于标识具体的业务消费方。
     */
    private String consumer;
}
