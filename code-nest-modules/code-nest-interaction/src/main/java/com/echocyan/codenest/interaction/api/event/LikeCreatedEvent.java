package com.echocyan.codenest.interaction.api.event;

import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * 文章点赞领域事件。
 *
 * <p>当用户成功对文章执行点赞时发布；取消点赞后再次点赞将重新触发此事件。
 *
 * @param articleId 被点赞的文章 ID
 * @param userId    执行点赞的用户 ID
 * @param authorId  被点赞文章的作者 ID
 */
@DomainEvent("like.created")
public record LikeCreatedEvent(long articleId, long userId, long authorId) {
}
