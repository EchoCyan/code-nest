package com.echocyan.codenest.article.service.impl;

import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.Counts;

/**
 * 热度评分算法模型。
 * <p>
 * 计分公式：{@code score = 互动加权和 / (发布小时数 + 2) ^ gravity}
 *
 * @param likeWeight     点赞权重
 * @param favoriteWeight 收藏权重
 * @param commentWeight  评论权重
 * @param viewWeight     浏览量权重
 * @param gravity        时间衰减系数
 */
record HotFormula(double likeWeight, double favoriteWeight, double commentWeight, double viewWeight, double gravity) {

    /**
     * 计算指定文章的综合热度得分。
     *
     * @param counts              各维度互动计数汇总
     * @param hoursSincePublished 文章正式发布至今的小时数（浮点数）
     * @return 综合计算所得的热度分值
     */
    double score(Counts counts, double hoursSincePublished) {
        double weighted = likeWeight * counts.get(CounterMetric.ARTICLE_LIKE) + favoriteWeight * counts.get(
                CounterMetric.ARTICLE_FAVORITE) + commentWeight * counts.get(CounterMetric.ARTICLE_COMMENT)
                + viewWeight * counts.get(CounterMetric.ARTICLE_VIEW);
        return weighted / Math.pow(hoursSincePublished + 2, gravity);
    }
}
