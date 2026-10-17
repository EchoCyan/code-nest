package com.echocyan.codenest.counter.api;

/**
 * 计数指标枚举。
 *
 * <p>每个指标隶属于特定的 {@link CounterTarget}，命名以目标实体类型为前缀。
 */
public enum CounterMetric {

    /**
     * 文章点赞数。
     */
    ARTICLE_LIKE(CounterTarget.ARTICLE),

    /**
     * 文章收藏数。
     */
    ARTICLE_FAVORITE(CounterTarget.ARTICLE),

    /**
     * 文章评论数（含一级评论与回复）。
     */
    ARTICLE_COMMENT(CounterTarget.ARTICLE),

    /**
     * 文章浏览量（近似计数：不按访客去重，亦不参与周期对账）。
     */
    ARTICLE_VIEW(CounterTarget.ARTICLE),

    /**
     * 用户粉丝数。
     */
    USER_FOLLOWER(CounterTarget.USER),

    /**
     * 用户关注数。
     */
    USER_FOLLOWING(CounterTarget.USER),

    /**
     * 用户已发布文章数。
     */
    USER_ARTICLE(CounterTarget.USER),

    /**
     * 用户累计获赞数。
     */
    USER_LIKE_RECEIVED(CounterTarget.USER),

    /**
     * 评论回复数（该评论下的回复总数）。
     */
    COMMENT_REPLY(CounterTarget.COMMENT);

    private final CounterTarget target;

    CounterMetric(CounterTarget target) {
        this.target = target;
    }

    /**
     * 获取该指标所属的目标实体类型。
     *
     * @return 目标实体类型
     */
    public CounterTarget target() {
        return target;
    }
}
