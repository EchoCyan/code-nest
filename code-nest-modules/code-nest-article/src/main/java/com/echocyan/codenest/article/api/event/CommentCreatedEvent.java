package com.echocyan.codenest.article.api.event;

import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * 评论或回复创建领域事件。
 *
 * @param commentId       评论或回复 ID
 * @param articleId       所属文章 ID
 * @param userId          发表者用户 ID
 * @param rootId          所属根评论 ID，一级评论为 0
 * @param replyToUserId   被回复目标用户 ID，一级评论为 {@code null}
 * @param articleAuthorId 被评论文章的作者用户 ID
 */
@DomainEvent("comment.created")
public record CommentCreatedEvent(long commentId, long articleId, long userId, long rootId, Long replyToUserId,
                                  long articleAuthorId) {
}
