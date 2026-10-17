package com.echocyan.codenest.notification.vo;

import com.echocyan.codenest.notification.entity.NotificationType;
import com.echocyan.codenest.user.api.UserBrief;

import java.time.LocalDateTime;

/**
 * 站内通知视图对象。
 * <p>
 * 聚合通知基础数据、动作触发人资料与关联内容摘要；当关联文章或评论已被删除时，展示占位文本。
 *
 * @param id             通知唯一标识
 * @param type           通知类型
 * @param actor          动作触发人简要信息
 * @param articleId      关联文章 ID（关注通知为 {@code null}）
 * @param articleTitle   关联文章标题（文章已删除时为占位提示文本）
 * @param commentId      关联评论或回复 ID（非评论/回复通知为 {@code null}）
 * @param commentSummary 评论或回复内容摘要（非评论/回复通知为 {@code null}，已删除时为占位提示文本）
 * @param read           是否已读
 * @param createdAt      通知创建时间
 */
public record NotificationVO(
        Long id,
        NotificationType type,
        UserBrief actor,
        Long articleId,
        String articleTitle,
        Long commentId,
        String commentSummary,
        boolean read,
        LocalDateTime createdAt) {
}
