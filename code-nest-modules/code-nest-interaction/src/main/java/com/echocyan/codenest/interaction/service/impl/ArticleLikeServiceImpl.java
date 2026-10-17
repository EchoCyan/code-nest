package com.echocyan.codenest.interaction.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.api.ArticleState;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterSource;
import com.echocyan.codenest.counter.api.IdCount;
import com.echocyan.codenest.framework.mq.DomainEventPublisher;
import com.echocyan.codenest.interaction.api.event.LikeCreatedEvent;
import com.echocyan.codenest.interaction.entity.ArticleLike;
import com.echocyan.codenest.interaction.mapper.ArticleLikeMapper;
import com.echocyan.codenest.interaction.service.ArticleLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章点赞服务实现类。
 *
 * <p>基于关系表唯一键保障点赞与取消点赞的幂等性；联动 {@link CounterApi} 更新计数指标，
 * 并实现 {@link CounterSource} 供计数对账系统重新统计。
 */
@Service
@RequiredArgsConstructor
public class ArticleLikeServiceImpl extends ServiceImpl<ArticleLikeMapper, ArticleLike>
        implements ArticleLikeService, CounterSource {

    private final PublishedArticles publishedArticles;
    private final CounterApi counterApi;
    private final DomainEventPublisher eventPublisher;

    @Override
    @Transactional
    public void like(long userId, long articleId) {
        ArticleState article = publishedArticles.require(articleId);
        ArticleLike like = new ArticleLike();
        like.setUserId(userId);
        like.setArticleId(articleId);
        like.setAuthorId(article.authorId());
        try {
            save(like);
        } catch (DuplicateKeyException e) {
            // 已点赞（含并发重复请求）幂等忽略；单条语句回滚不影响外层事务继续执行
            return;
        }
        countLike(articleId, article.authorId(), 1);
        eventPublisher.publish(new LikeCreatedEvent(articleId, userId, article.authorId()));
    }

    @Override
    @Transactional
    public void unlike(long userId, long articleId) {
        ArticleLike like =
                lambdaQuery().eq(ArticleLike::getUserId, userId).eq(ArticleLike::getArticleId, articleId).one();
        // 并发重复取消时，仅成功删除数据行的请求触发计数递减
        if (like != null && removeById(like.getId())) {
            countLike(articleId, like.getAuthorId(), -1);
        }
    }

    /**
     * 联动更新文章点赞数与作者累计获赞数。
     */
    private void countLike(long articleId, long authorId, long delta) {
        counterApi.increment(CounterMetric.ARTICLE_LIKE, articleId, delta);
        counterApi.increment(CounterMetric.USER_LIKE_RECEIVED, authorId, delta);
    }

    @Override
    public Set<CounterMetric> metrics() {
        return Set.of(CounterMetric.ARTICLE_LIKE, CounterMetric.USER_LIKE_RECEIVED);
    }

    @Override
    public List<IdCount> countAfter(CounterMetric metric, long afterId, int limit) {
        return switch (metric) {
            case ARTICLE_LIKE -> baseMapper.countByArticle(afterId, limit);
            case USER_LIKE_RECEIVED -> baseMapper.countByAuthor(afterId, limit);
            default -> throw new IllegalArgumentException("不负责的计数指标: " + metric);
        };
    }

    @Override
    public Set<Long> listLikedArticleIds(long userId, Collection<Long> articleIds) {
        if (articleIds.isEmpty()) {
            return Set.of();
        }
        return lambdaQuery().select(ArticleLike::getArticleId)
                .eq(ArticleLike::getUserId, userId)
                .in(ArticleLike::getArticleId, articleIds)
                .list()
                .stream()
                .map(ArticleLike::getArticleId)
                .collect(Collectors.toSet());
    }
}
