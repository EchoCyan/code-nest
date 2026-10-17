package com.echocyan.codenest.article.api.event;

import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * 文章发布领域事件。
 * <p>
 * 当作者首次将草稿正式发布为公开文章时触发，路由键为 {@code article.published}；已发布文章的重复发布或后续编辑不触发此事件。
 *
 * @param articleId 首次发布的文章 ID
 * @param authorId  文章作者用户 ID
 */
@DomainEvent("article.published")
public record ArticlePublishedEvent(long articleId, long authorId) {
}
