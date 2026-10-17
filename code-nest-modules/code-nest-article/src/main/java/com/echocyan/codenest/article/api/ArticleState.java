package com.echocyan.codenest.article.api;

/**
 * 文章状态与归属校验投影。
 * <p>
 * 专供外部模块在执行写操作前校验前置业务合法性（例如仅允许对处于已发布状态的文章执行点赞或收藏操作）。
 *
 * @param id       文章 ID
 * @param authorId 作者用户 ID
 * @param status   当前生命周期状态
 */
public record ArticleState(Long id, Long authorId, ArticleStatus status) {
}
