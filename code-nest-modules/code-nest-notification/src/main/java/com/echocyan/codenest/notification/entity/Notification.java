package com.echocyan.codenest.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 站内通知实体。
 * <p>
 * 记录由动作触发者（{@link #actorId}）行为产生的站内消息，通知给接收者（{@link #recipientId}）。
 * 仅存储关联实体的唯一标识，展示详情在读取查询时批量聚合装配。
 */
@Getter
@Setter
@TableName("notification")
public class Notification extends AuditableEntity {

    /**
     * 通知唯一标识。
     */
    private Long id;

    /**
     * 通知接收人用户 ID。
     */
    private Long recipientId;

    /**
     * 动作触发人用户 ID。
     */
    private Long actorId;

    /**
     * 通知类型。
     */
    private NotificationType type;

    /**
     * 关联文章 ID（关注通知为 {@code null}）。
     */
    private Long articleId;

    /**
     * 关联评论或回复 ID（仅评论与回复通知有值）。
     */
    private Long commentId;

    /**
     * 幂等去重键（点赞格式 {@code L:{actor}:{article}}，关注格式 {@code F:{actor}:{author}}，评论与回复为 {@code null}）。
     */
    private String dedupKey;

    /**
     * 是否已读。
     */
    private Boolean isRead;
}
