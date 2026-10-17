package com.echocyan.codenest.article.api;

import com.echocyan.codenest.user.api.UserBrief;

import java.time.LocalDateTime;

/**
 * 文章列表复合展示项。
 * <p>
 * 聚合文章元数据、分类、作者简要信息与实时互动计数；广泛应用于最新文章、热榜、关注 Feed 及搜索结果列表。
 *
 * @param id          文章 ID
 * @param title       文章标题
 * @param summary     文章纯文本摘要
 * @param coverUrl    封面图 URL
 * @param category    所属分类投影
 * @param status      文章状态
 * @param publishedAt 发布时间；草稿状态下为 {@code null}
 * @param author      作者简要信息；若用户账号已注销则为 {@code null}
 * @param counts      实时互动计数（点赞、收藏、评论、浏览）
 */
public record ArticleItem(Long id,
                          String title,
                          String summary,
                          String coverUrl,
                          CategoryBrief category,
                          ArticleStatus status,
                          LocalDateTime publishedAt,
                          UserBrief author,
                          ArticleCounts counts) {
}
