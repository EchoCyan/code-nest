package com.echocyan.codenest.article.api.event;

import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * 文章软删除领域事件。
 * <p>
 * 当作者删除草稿或已发布文章时触发，路由键为 {@code article.deleted}。
 *
 * @param articleId 被删除的文章 ID
 * @param authorId  文章作者用户 ID
 */
@DomainEvent("article.deleted")
public record ArticleDeletedEvent(long articleId, long authorId) {
}
