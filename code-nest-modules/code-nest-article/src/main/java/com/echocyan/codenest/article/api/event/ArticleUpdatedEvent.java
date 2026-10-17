package com.echocyan.codenest.article.api.event;

import com.echocyan.codenest.framework.mq.DomainEvent;

/**
 * 文章内容变更领域事件。
 * <p>
 * 当作者修改文章正文或元数据时触发（对草稿与已发布文章均生效），路由键为 {@code article.updated}。
 *
 * @param articleId 发生变更的文章 ID
 * @param authorId  文章作者用户 ID
 */
@DomainEvent("article.updated")
public record ArticleUpdatedEvent(long articleId, long authorId) {
}
