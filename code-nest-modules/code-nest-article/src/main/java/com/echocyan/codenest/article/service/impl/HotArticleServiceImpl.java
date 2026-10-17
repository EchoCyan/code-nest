package com.echocyan.codenest.article.service.impl;

import com.echocyan.codenest.article.api.ArticleItem;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.service.ArticleReader;
import com.echocyan.codenest.article.service.ArticleService;
import com.echocyan.codenest.article.service.HotArticleService;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.common.util.DateTimes;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.api.Counts;
import com.echocyan.codenest.framework.lock.RedisLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 热门文章榜单服务实现类。
 */
@Slf4j
@Service
class HotArticleServiceImpl implements HotArticleService {

    /** 热榜 Redis 键 */
    private static final String KEY = "hot:articles";

    /** 重算临时键 */
    private static final String TMP_KEY = "hot:articles:tmp";

    /** 定时重算分布式锁键 */
    private static final String LOCK_KEY = "hot:lock";

    /** 分布式锁租期 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(240);

    /** 候选文章时间窗口（7天） */
    private static final Duration CANDIDATE_WINDOW = Duration.ofDays(7);

    /**
     * 计数批量查询的分批批次大小。
     */
    private static final int BATCH = 500;

    private final ArticleService articleService;
    private final ArticleReader articleReader;
    private final CounterApi counterApi;
    private final StringRedisTemplate redis;
    private final RedisLock redisLock;
    private final HotFormula formula;

    HotArticleServiceImpl(ArticleService articleService, ArticleReader articleReader, CounterApi counterApi,
                          StringRedisTemplate redis, RedisLock redisLock,
                          @Value("${hot.weight.like}") double likeWeight,
                          @Value("${hot.weight.favorite}") double favoriteWeight,
                          @Value("${hot.weight.comment}") double commentWeight,
                          @Value("${hot.weight.view}") double viewWeight,
                          @Value("${hot.gravity}") double gravity) {
        this.articleService = articleService;
        this.articleReader = articleReader;
        this.counterApi = counterApi;
        this.redis = redis;
        this.redisLock = redisLock;
        this.formula = new HotFormula(likeWeight, favoriteWeight, commentWeight, viewWeight, gravity);
    }

    /**
     * 定时调度重算任务，每 5 分钟整点触发。
     */
    @Scheduled(cron = "0 */5 * * * *")
    void scheduledRefresh() {
        refresh();
    }

    @Override
    public void refresh() {
        if (!redisLock.tryRun(LOCK_KEY, LOCK_TTL, this::recompute)) {
            log.debug("Hot list is being refreshed on another instance, skipped");
        }
    }

    @Override
    public PageResult<ArticleItem> page(int page) {
        long start = (long) (page - 1) * PAGE_SIZE;
        Set<String> members = redis.opsForZSet().reverseRange(KEY, start, start + PAGE_SIZE - 1);
        Long total = redis.opsForZSet().zCard(KEY);
        List<Long> ids = members == null ? List.of() : members.stream().map(Long::valueOf).toList();
        return new PageResult<>(articleReader.listPublishedItems(ids), total == null ? 0 : total, page, PAGE_SIZE);
    }

    /**
     * 执行全量热度重算并通过 RENAME 原子替换正式缓存键；若候选集为空则清理正式键。
     */
    private void recompute() {
        Set<TypedTuple<String>> top = top(DateTimes.now());
        if (top.isEmpty()) {
            redis.delete(KEY);
            return;
        }
        redis.delete(TMP_KEY);
        redis.opsForZSet().add(TMP_KEY, top);
        redis.rename(TMP_KEY, KEY);
    }

    /**
     * 从时间窗口候选集中评分并筛选 Top {@value #CAPACITY} 文章。
     *
     * @param now 当前时间戳
     * @return 评分有序元组集合
     */
    private Set<TypedTuple<String>> top(LocalDateTime now) {
        // 仅投影计算所需的核心列
        List<Article> candidates = articleService.lambdaQuery()
                .select(Article::getId, Article::getPublishedAt)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .ge(Article::getPublishedAt, now.minus(CANDIDATE_WINDOW))
                .list();
        List<TypedTuple<String>> scored = new ArrayList<>(candidates.size());
        for (int from = 0; from < candidates.size(); from += BATCH) {
            List<Article> batch = candidates.subList(from, Math.min(from + BATCH, candidates.size()));
            Map<Long, Counts> counts = counterApi.get(CounterTarget.ARTICLE,
                    batch.stream().map(Article::getId).toList());
            for (Article article : batch) {
                double hours = Math.max(0, Duration.between(article.getPublishedAt(), now).toMillis() / 3_600_000.0);
                scored.add(TypedTuple.of(String.valueOf(article.getId()),
                        formula.score(counts.get(article.getId()), hours)));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparing(TypedTuple<String>::getScore).reversed())
                .limit(CAPACITY)
                .collect(Collectors.toSet());
    }
}
