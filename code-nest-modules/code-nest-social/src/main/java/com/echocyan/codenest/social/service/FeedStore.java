package com.echocyan.codenest.social.service;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleItem;
import com.echocyan.codenest.article.api.ArticleState;
import com.echocyan.codenest.common.result.CursorResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 基于推拉结合架构的 Feed 动态流存储组件。
 * <p>
 * 全量状态维护在 Redis 中，通过作者发件箱与读者收件箱协同工作：
 * <ul>
 *   <li><b>普通作者（推模式）：</b>发文时将文章写入作者发件箱，并通过分页并行推送到活跃粉丝的收件箱（写扩散可控）。</li>
 *   <li><b>大 V 作者（拉模式）：</b>粉丝数达到阈值的大 V 发文时仅落发件箱，不进行全量粉丝推送；读者读取时实时拉取大 V 发件箱合并（避免严重写扩散）。</li>
 *   <li><b>写侧幂等与读侧兜底：</b>发文、删文、关注、取关由 MQ 驱动且操作幂等；事件乱序或延迟导致的失效数据在读取（{@link #read}）时统一依据文章最新发布状态及当前关注关系做二次校验过滤。</li>
 *   <li><b>冷启动与灾备重建：</b>实现 {@link SmartInitializingSingleton}，应用启动时若就绪标记缺失，自动按数据库全量重建发件箱与大 V 粉丝数。</li>
 * </ul>
 */
@Slf4j
@Component
public class FeedStore implements SmartInitializingSingleton {

    /**
     * 推送时每页获取的粉丝数量。
     */
    private static final int FOLLOWER_PAGE = 1000;

    /**
     * 重建发件箱时每批获取的已发布文章数量。
     */
    private static final int REBUILD_BATCH = 1000;

    private final FeedBoxes feedBoxes;
    private final BigAuthors bigAuthors;
    private final FollowerGraph followerGraph;
    private final ArticleApi articleApi;
    private final Timer readTimer;
    private final Timer followListTimer;

    @Autowired
    public FeedStore(StringRedisTemplate redis, FollowerGraph followerGraph, ArticleApi articleApi,
                     MeterRegistry meterRegistry, @Value("${feed.big-author-threshold}") long bigAuthorThreshold) {
        this(redis, followerGraph, articleApi, meterRegistry, "feed", bigAuthorThreshold);
    }

    /**
     * 构造 Feed 存储组件。
     *
     * @param redis              Redis 模板
     * @param followerGraph      关注图谱数据访问接口
     * @param articleApi         文章服务门面
     * @param meterRegistry      监控指标注册表
     * @param keyPrefix          Feed 缓存键通用前缀（默认为 {@code feed}）
     * @param bigAuthorThreshold 判定为大 V 的粉丝数阈值
     */
    public FeedStore(StringRedisTemplate redis, FollowerGraph followerGraph, ArticleApi articleApi,
                     MeterRegistry meterRegistry, String keyPrefix, long bigAuthorThreshold) {
        this.feedBoxes = new FeedBoxes(redis, keyPrefix);
        this.bigAuthors = new BigAuthors(redis, followerGraph, keyPrefix, bigAuthorThreshold);
        this.followerGraph = followerGraph;
        this.articleApi = articleApi;
        this.readTimer = meterRegistry.timer("feed.read");
        this.followListTimer = meterRegistry.timer("feed.read.follow-list");
    }

    /**
     * 处理文章发布：写入作者发件箱；若作者为普通作者，则分页推送到活跃粉丝的收件箱。
     *
     * @param articleId 文章 ID
     * @param authorId  作者用户 ID
     */
    public void addArticle(long articleId, long authorId) {
        feedBoxes.addToOutbox(authorId, articleId);
        if (!bigAuthors.isBig(authorId)) {
            forEachFollowerPage(authorId, followerIds -> feedBoxes.pushToInboxes(followerIds, articleId));
        }
    }

    /**
     * 处理文章删除：从作者发件箱中移除文章（粉丝收件箱中残留通过读时过滤兜底）。
     *
     * @param articleId 文章 ID
     * @param authorId  作者用户 ID
     */
    public void removeArticle(long articleId, long authorId) {
        feedBoxes.removeFromOutbox(authorId, articleId);
    }

    /**
     * 处理用户关注：刷新作者粉丝数；若目标为普通作者，将其发件箱并入读者已存在的收件箱。
     *
     * @param followerId 发起关注的读者 ID
     * @param authorId   被关注的作者 ID
     */
    public void follow(long followerId, long authorId) {
        refreshFollowers(authorId);
        if (!bigAuthors.isBig(authorId)) {
            feedBoxes.mergeOutboxIntoInbox(followerId, authorId);
        }
    }

    /**
     * 处理用户取关：刷新作者粉丝数，并从读者收件箱中剔除该作者发件箱内的文章。
     *
     * @param followerId 发起取关的读者 ID
     * @param authorId   被取关的作者 ID
     */
    public void unfollow(long followerId, long authorId) {
        refreshFollowers(authorId);
        feedBoxes.removeOutboxFromInbox(followerId, authorId);
    }

    /**
     * 游标分页读取当前用户的关注 Feed 动态流。
     *
     * @param userId 读者用户 ID
     * @param cursor 游标文章 ID（首次查询传入 {@code null}）
     * @param size   期望拉取的文章数量
     * @return 包含组装后文章项与下一页游标的分页结果
     */
    public CursorResult<ArticleItem> read(long userId, Long cursor, int size) {
        return readTimer.record(() -> doRead(userId, cursor, size));
    }

    /**
     * 执行 Feed 核心读取逻辑：圈定大 V、续期/重建收件箱、并发抓取 ID、截断排序、批量装配并过滤无效项。
     */
    private CursorResult<ArticleItem> doRead(long userId, Long cursor, int size) {
        List<Long> authorIds = followListTimer.record(() -> followerGraph.listAllFollowedAuthorIds(userId));
        if (authorIds.isEmpty()) {
            return CursorResult.empty();
        }
        Set<Long> big = bigAuthors.among(authorIds);
        feedBoxes.renewOrRebuildInbox(userId, authorIds.stream().filter(id -> !big.contains(id)).toList());
        // 多取一条，只用来判断是否还有下一页
        List<Long> ids = feedBoxes.readBefore(userId, big, cursor, size + 1).stream()
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(size + 1)
                .toList();
        CursorResult<Long> page = CursorResult.ofOverfetched(ids, size, Function.identity());
        Set<Long> followed = Set.copyOf(authorIds);
        List<ArticleItem> items = articleApi.listPublishedItems(page.list()).stream()
                .filter(item -> item.author() != null && followed.contains(item.author().id()))
                .toList();
        return new CursorResult<>(items, page.nextCursor(), page.hasMore());
    }

    /**
     * 刷新作者粉丝数；若作者从大 V 降级为普通作者，将其发件箱补入所有活跃粉丝的收件箱。
     */
    private void refreshFollowers(long authorId) {
        if (bigAuthors.refresh(authorId)) {
            forEachFollowerPage(authorId, followerIds -> feedBoxes.mergeOutboxIntoInboxes(followerIds, authorId));
        }
    }

    /**
     * 按粉丝 ID 升序，每页 {@value #FOLLOWER_PAGE} 条分页遍历作者的全部粉丝。
     */
    private void forEachFollowerPage(long authorId, Consumer<List<Long>> handler) {
        Long after = null;
        List<Long> followerIds;
        do {
            followerIds = followerGraph.listFollowerIds(authorId, after, FOLLOWER_PAGE);
            if (!followerIds.isEmpty()) {
                handler.accept(followerIds);
                after = followerIds.getLast();
            }
        } while (followerIds.size() == FOLLOWER_PAGE);
    }

    /**
     * 单例初始化后钩子：当就绪标记缺失时，按数据库全量重建发件箱和大 V 粉丝数。
     */
    @Override
    public void afterSingletonsInstantiated() {
        rebuildOutboxesIfAbsent();
        bigAuthors.rebuildIfAbsent();
    }

    /**
     * 若发件箱就绪标记不存在，从数据库按文章 ID 升序分批重建所有已发布文章的发件箱。
     */
    private void rebuildOutboxesIfAbsent() {
        if (feedBoxes.outboxesReady()) {
            return;
        }
        long imported = 0;
        List<ArticleState> articles = articleApi.listPublishedStates(null, REBUILD_BATCH);
        while (!articles.isEmpty()) {
            feedBoxes.addToOutboxes(articles);
            imported += articles.size();
            articles = articleApi.listPublishedStates(articles.getLast().id(), REBUILD_BATCH);
        }
        feedBoxes.markOutboxesReady();
        log.info("Rebuilt feed outboxes from {} published articles", imported);
    }
}
