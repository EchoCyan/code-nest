package com.echocyan.codenest.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.IndexState;
import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleSnapshot;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.common.util.DateTimes;
import com.echocyan.codenest.framework.lock.RedisLock;
import com.echocyan.codenest.search.dto.SearchSort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Elasticsearch 文章索引管理器。
 * <p>
 * 负责文章索引的生命周期管理、全量重建、增量同步与检索代理：
 * <ul>
 *     <li>别名路由与蓝绿部署：物理索引按 {@code <alias>_v{n}} 递增命名，外部读写统一路由至逻辑别名 {@value #ALIAS}；</li>
 *     <li>外部版本号并发控制：以 {@link ArticleSnapshot#version()} 作为外部版本号（{@link VersionType#External}），
 *         Elasticsearch 仅接受大于当前版本的写入与删除；低版本或乱序到达时触发 HTTP 409（{@code VERSION_CONFLICT}）并予以忽略；</li>
 *     <li>零停机平滑重建：写入新版本物理索引，导入完成后在单次请求中原子切换别名，并基于时间戳追补重建窗口期间的增量数据；</li>
 *     <li>容器启动自愈：实现 {@link SmartInitializingSingleton}，容器启动若检测到别名不存在，通过分布式锁互斥执行全量构建；
 *         全量导入完成前不绑定别名，避免残留脏索引。</li>
 * </ul>
 */
@Slf4j
@Component
public class ArticleIndex implements SmartInitializingSingleton {

    /**
     * 文章搜索默认逻辑别名。
     */
    public static final String ALIAS = "article";

    /**
     * 索引 Mapping 结构定义文件路径（ClassPath 相对路径）。
     */
    private static final String MAPPING = "search/article-index.json";

    /**
     * 全量导入时单批次游标读取的文章数量。
     */
    private static final int IMPORT_BATCH_SIZE = 500;

    /**
     * Elasticsearch 版本冲突 HTTP 状态码。
     */
    private static final int VERSION_CONFLICT = 409;

    /**
     * 重建分布式锁持有过期时间（需覆盖单次全量重建最大耗时）。
     */
    private static final Duration REBUILD_LOCK_TTL = Duration.ofHours(1);

    private final ElasticsearchClient client;
    private final ArticleApi articleApi;
    private final RedisLock redisLock;
    private final String alias;
    private final String indexPrefix;
    private final String rebuildLockKey;
    private final ArticleSearcher searcher;

    @Autowired
    public ArticleIndex(ElasticsearchClient client, ArticleApi articleApi, RedisLock redisLock) {
        this(client, articleApi, redisLock, ALIAS);
    }

    /**
     * @param client     Elasticsearch 客户端
     * @param articleApi 文章领域 API 门面
     * @param redisLock  Redis 分布式锁组件
     * @param alias      读写路由的逻辑别名；真实物理索引为 {@code <alias>_v{n}}，分布式重建锁为 {@code search:<alias>:rebuild:lock}
     */
    public ArticleIndex(ElasticsearchClient client, ArticleApi articleApi, RedisLock redisLock, String alias) {
        this.client = client;
        this.articleApi = articleApi;
        this.redisLock = redisLock;
        this.alias = alias;
        this.indexPrefix = alias + "_v";
        this.rebuildLockKey = "search:" + alias + ":rebuild:lock";
        this.searcher = new ArticleSearcher(client, alias);
    }

    /**
     * 在已发布文章中按关键词检索，支持分类与标签筛选、多维排序与片段高亮。
     *
     * @param keyword    检索关键词
     * @param categoryId 分类 ID 筛选，为 {@code null} 时不筛选
     * @param tagId      标签 ID 筛选，为 {@code null} 时不筛选
     * @param sort       排序策略
     * @param page       当前页码（从 1 开始）
     * @param size       每页记录数
     * @return 检索命中结果分页对象
     */
    public PageResult<Match> search(String keyword, Long categoryId, Long tagId, SearchSort sort, long page,
                                    long size) {
        return searcher.search(keyword, categoryId, tagId, sort, page, size);
    }

    /**
     * 容器单例初始化完成后执行别名就绪自检。
     * <p>
     * 若逻辑别名不存在，尝试获取分布式重建锁执行全量初始化；未获取到锁的实例跳过并正常启动。
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (aliasExists()) {
            return;
        }
        // 拿到锁后再判断一次：别的实例可能刚建好并释放了锁
        boolean ran = redisLock.tryRun(rebuildLockKey, REBUILD_LOCK_TTL, () -> {
            if (!aliasExists()) {
                rebuildLocked();
            }
        });
        if (!ran) {
            log.info("Another instance is building search index for alias {}, skipped", alias);
        }
    }

    /**
     * 执行索引零停机平滑重建。
     * <p>
     * 流程：
     * <ol>
     *     <li>创建递增版本的物理索引（{@code <alias>_v{n+1}}）；</li>
     *     <li>按主键 ID 游标分批全量导入已发布文章，并刷新索引使其立即可搜；</li>
     *     <li>通过单次原子请求将别名从旧索引解绑并绑定到新索引；</li>
     *     <li>增量追补重建窗口期间更新或删除的文章；</li>
     *     <li>物理清理废弃的历史版本索引及此前中断残留的索引。</li>
     * </ol>
     * 多实例间通过 Redis 锁互斥，同一时刻仅允许单个重建任务运行。
     *
     * @return 重建结果统计（新索引版本、导入数、追补数）；已有重建任务在运行时返回 {@link Optional#empty()}
     */
    public Optional<RebuildResult> rebuild() {
        Optional<RebuildResult> result = redisLock.tryRun(rebuildLockKey, REBUILD_LOCK_TTL, this::rebuildLocked);
        if (result.isEmpty()) {
            log.info("Search index rebuild is already running, skipped");
        }
        return result;
    }

    /**
     * 持有分布式锁时执行具体重建流程。
     *
     * @return 索引重建结果统计
     */
    private RebuildResult rebuildLocked() {
        try {
            // updated_at 是秒级 DATETIME，写入时四舍五入；向下取整到秒，不漏掉开始那一秒内的变更
            LocalDateTime startedAt = DateTimes.now().truncatedTo(ChronoUnit.SECONDS);
            Map<String, IndexState> existing = client.indices().get(get -> get.index(indexPrefix + "*")).indices();
            Set<String> oldIndices = existing.entrySet().stream()
                    .filter(entry -> entry.getValue().aliases().containsKey(alias))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            String index = indexPrefix + nextVersion(existing.keySet());
            create(index);
            long imported = importAll(index);
            client.indices().refresh(refresh -> refresh.index(index));
            client.indices().updateAliases(update -> {
                oldIndices.forEach(old -> update.actions(action -> action
                        .remove(remove -> remove.index(old).alias(alias))));
                return update.actions(action -> action.add(add -> add.index(index).alias(alias)));
            });
            long caughtUp = catchUp(startedAt);
            if (!existing.isEmpty()) {
                client.indices().delete(delete -> delete.index(List.copyOf(existing.keySet())));
            }
            log.info("Rebuilt search index {}: imported {}, caught up {}, deleted {}", index, imported, caughtUp,
                    existing.keySet());
            return new RebuildResult(index, imported, caughtUp);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 按文章最新快照执行增量同步。
     * <p>
     * 查得快照若处于可搜索状态（已发布且未删除）则写入索引，否则从索引中移除。
     *
     * @param articleId 文章 ID
     */
    public void sync(long articleId) {
        articleApi.findSnapshot(articleId).ifPresent(this::sync);
    }

    /**
     * 执行快照同步路由。
     *
     * @param snapshot 文章快照
     */
    private void sync(ArticleSnapshot snapshot) {
        if (snapshot.searchable()) {
            save(snapshot);
        } else {
            remove(snapshot.id(), snapshot.version());
        }
    }

    /**
     * 写入或更新文章索引文档。
     * <p>
     * 使用外部版本号（{@link VersionType#External}）写入。若索引中已存在相同或更新版本的文档，
     * ES 返回版本冲突异常并由 {@link #ignoringVersionConflict(EsCall)} 安全忽略。
     *
     * @param snapshot 文章快照
     */
    private void save(ArticleSnapshot snapshot) {
        ignoringVersionConflict(() -> client.index(index -> index
                .index(alias)
                .requireAlias(true)
                .id(String.valueOf(snapshot.id()))
                .version((long) snapshot.version())
                .versionType(VersionType.External)
                .document(ArticleDocument.of(snapshot))));
    }

    /**
     * 从索引中移除文章文档。
     * <p>
     * 使用删除后的递增版本号作为外部版本号执行删除。若索引中已存在相同或更新版本，操作被忽略。
     *
     * @param articleId 文章 ID
     * @param version   删除动作触发后的版本号
     */
    private void remove(long articleId, long version) {
        ignoringVersionConflict(() -> client.delete(delete -> delete
                .index(alias)
                .id(String.valueOf(articleId))
                .version(version)
                .versionType(VersionType.External)));
    }

    /**
     * 判断配置的目标别名是否存在。
     *
     * @return 别名存在返回 {@code true}，否则返回 {@code false}
     * @throws UncheckedIOException 当底层通信发生 I/O 异常时
     */
    private boolean aliasExists() {
        try {
            return client.indices().existsAlias(exists -> exists.name(alias)).value();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 根据本地 Mapping 结构定义创建物理索引（暂不绑定别名）。
     *
     * @param index 待创建的物理索引名称
     * @throws IOException 当读取 Mapping 资源或调用 ES 创建索引发生 I/O 异常时
     */
    private void create(String index) throws IOException {
        try (InputStream mapping = new ClassPathResource(MAPPING).getInputStream()) {
            client.indices().create(create -> create.withJson(mapping).index(index));
        }
        log.info("Created search index {}", index);
    }

    /**
     * 计算下一个物理索引版本序号。
     *
     * @param indices 当前已存在的同前缀物理索引名称集合
     * @return 最大版本号 + 1；若无已有索引则返回 1
     */
    private int nextVersion(Set<String> indices) {
        return indices.stream()
                .mapToInt(name -> Integer.parseInt(name.substring(indexPrefix.length())))
                .max()
                .orElse(0) + 1;
    }

    /**
     * 按主键 ID 游标分批读取全部已发布文章并批量导入目标物理索引。
     *
     * @param index 目标物理索引名称
     * @return 全量导入的文章总数
     */
    private long importAll(String index) {
        long total = forEachBatch(articleApi::listPublishedSnapshots, batch -> saveAll(index, batch));
        log.info("Imported {} published articles into search index {}", total, index);
        return total;
    }

    /**
     * 增量追补自指定时间戳之后发生变更的文章。
     * <p>
     * 包含草稿与已删除状态，逐篇经由逻辑别名写入新索引。由于采用外部版本号，重复写入具备幂等性。
     *
     * @param since 变更起始时间戳
     * @return 追补处理的文章总数
     */
    private long catchUp(LocalDateTime since) {
        return forEachBatch((afterId, limit) -> articleApi.listSnapshotsUpdatedSince(since, afterId, limit),
                batch -> batch.forEach(this::sync));
    }

    /**
     * 基于主键 ID 游标分批遍历读取并消费数据。
     *
     * @param reader  批次读取函数，入参为 (afterId, limit)，afterId 为 {@code null} 表示起始游标
     * @param handler 批次数据消费函数
     * @return 处理的记录总数
     */
    private long forEachBatch(BiFunction<Long, Integer, List<ArticleSnapshot>> reader,
                              Consumer<List<ArticleSnapshot>> handler) {
        long total = 0;
        Long cursor = null;
        List<ArticleSnapshot> batch;
        do {
            batch = reader.apply(cursor, IMPORT_BATCH_SIZE);
            if (!batch.isEmpty()) {
                handler.accept(batch);
                total += batch.size();
                cursor = batch.getLast().id();
            }
        } while (batch.size() == IMPORT_BATCH_SIZE);
        return total;
    }

    /**
     * 执行 Bulk 批量文档写入。
     * <p>
     * 忽略单条记录的版本冲突（HTTP 409），其余错误均抛出异常阻断流程。
     *
     * @param indexName 目标物理索引名称
     * @param snapshots 待批量写入的文章快照列表
     * @throws IllegalStateException 当批量写入响应中包含除版本冲突以外的错误时
     * @throws UncheckedIOException  当发生 I/O 异常时
     */
    private void saveAll(String indexName, List<ArticleSnapshot> snapshots) {
        BulkResponse response;
        try {
            response = client.bulk(bulk -> {
                for (ArticleSnapshot snapshot : snapshots) {
                    bulk.operations(operation -> operation.index(index -> index
                            .index(indexName)
                            .id(String.valueOf(snapshot.id()))
                            .version((long) snapshot.version())
                            .versionType(VersionType.External)
                            .document(ArticleDocument.of(snapshot))));
                }
                return bulk;
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (BulkResponseItem item : response.items()) {
            if (item.error() != null && item.status() != VERSION_CONFLICT) {
                throw new IllegalStateException("Failed to index article " + item.id() + ": " + item.error().reason());
            }
        }
    }

    /**
     * 执行 ES 调用并安全忽略版本冲突异常（HTTP 409）。
     *
     * @param call 待执行的 ES 操作函数
     * @throws UncheckedIOException 当发生非版本冲突的 I/O 异常时
     */
    private void ignoringVersionConflict(EsCall call) {
        try {
            call.run();
        } catch (ElasticsearchException e) {
            if (e.status() != VERSION_CONFLICT) {
                throw e;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * ES 调用执行函数式接口。
     */
    private interface EsCall {
        void run() throws IOException;
    }

    /**
     * 检索命中条目封装。
     *
     * @param articleId        文章 ID
     * @param titleHighlight   标题高亮 HTML 片段；未命中时为 {@code null}
     * @param contentHighlight 正文高亮 HTML 片段；未命中时为 {@code null}
     */
    public record Match(long articleId, String titleHighlight, String contentHighlight) {
    }

    /**
     * 索引重建统计结果。
     *
     * @param index    新创建的物理索引名称
     * @param imported 全量导入的文章总数
     * @param caughtUp 追补同步的文章总数
     */
    public record RebuildResult(String index, long imported, long caughtUp) {
    }

    /**
     * 索引文档字段名常量集，与 {@link ArticleDocument} 组件名及 {@code search/article-index.json} 的 Mapping 保持一致。
     */
    public static final class Fields {

        public static final String ID = "id";
        public static final String TITLE = "title";
        public static final String SUMMARY = "summary";
        public static final String CONTENT = "content";
        public static final String TAGS = "tags";
        public static final String TAG_IDS = "tagIds";
        public static final String CATEGORY_ID = "categoryId";
        public static final String AUTHOR_ID = "authorId";
        public static final String PUBLISHED_AT = "publishedAt";

        private Fields() {
        }
    }

    /**
     * Elasticsearch 索引文档实体 Record。
     * <p>
     * 仅索引静态与低频变更内容；作者动态信息与高频计数指标不入索引。
     *
     * @param id          文章 ID
     * @param title       文章标题
     * @param summary     文章摘要
     * @param content     Markdown 正文原文
     * @param tags        标签名称列表（用于搜索词精准匹配加权）
     * @param tagIds      标签 ID 列表（用于标签筛选）
     * @param categoryId  分类 ID
     * @param authorId    作者用户 ID
     * @param publishedAt 发布时间
     */
    record ArticleDocument(Long id,
                           String title,
                           String summary,
                           String content,
                           List<String> tags,
                           List<Long> tagIds,
                           Long categoryId,
                           Long authorId,
                           LocalDateTime publishedAt) {

        static ArticleDocument of(ArticleSnapshot snapshot) {
            return new ArticleDocument(
                    snapshot.id(),
                    snapshot.title(),
                    snapshot.summary(),
                    snapshot.content(),
                    snapshot.tagNames(),
                    snapshot.tagIds(),
                    snapshot.categoryId(),
                    snapshot.authorId(),
                    snapshot.publishedAt());
        }
    }
}
