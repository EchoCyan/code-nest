package com.echocyan.codenest.article.api;

import java.time.LocalDateTime;

/**
 * 文章轻量级摘要投影。
 * <p>
 * 用于信息流、通知与卡片等无需展示正文的场景；支持 Redis 缓存。
 *
 * @param id          文章 ID
 * @param authorId    作者用户 ID
 * @param categoryId  所属分类 ID
 * @param title       文章标题
 * @param summary     文章纯文本摘要
 * @param coverUrl    封面图 URL
 * @param status      文章状态
 * @param publishedAt 发布时间；处于草稿状态时为 {@code null}
 */
public record ArticleBrief(
        Long id,
        Long authorId,
        Long categoryId,
        String title,
        String summary,
        String coverUrl,
        ArticleStatus status,
        LocalDateTime publishedAt) {
}
