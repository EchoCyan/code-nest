package com.echocyan.codenest.article.api;

/**
 * 文章互动统计数据投影。
 *
 * @param likeCount     点赞总数
 * @param favoriteCount 收藏总数
 * @param commentCount  评论与回复总数
 * @param viewCount     浏览总量
 */
public record ArticleCounts(long likeCount, long favoriteCount, long commentCount, long viewCount) {
}
