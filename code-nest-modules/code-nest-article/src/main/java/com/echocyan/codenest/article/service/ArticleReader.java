package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.article.api.ArticleCounts;
import com.echocyan.codenest.article.api.ArticleErrorCode;
import com.echocyan.codenest.article.api.ArticleItem;
import com.echocyan.codenest.article.api.ArticleSnapshot;
import com.echocyan.codenest.article.api.ArticleState;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.article.api.CategoryBrief;
import com.echocyan.codenest.article.api.CommentBrief;
import com.echocyan.codenest.article.convert.ArticleConverter;
import com.echocyan.codenest.article.convert.CategoryConverter;
import com.echocyan.codenest.article.convert.CommentConverter;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.entity.ArticleContent;
import com.echocyan.codenest.article.entity.Category;
import com.echocyan.codenest.article.entity.Tag;
import com.echocyan.codenest.article.vo.ArticleDetailVO;
import com.echocyan.codenest.article.vo.TagVO;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.api.Counts;
import com.echocyan.codenest.user.api.UserApi;
import com.echocyan.codenest.user.api.UserBrief;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章读服务聚合组件，实现 {@link ArticleApi}。
 * <p>
 * 提供文章详情、列表检索与跨模块数据装配。
 */
@Component
@RequiredArgsConstructor
public class ArticleReader implements ArticleApi, SmartInitializingSingleton {

    /**
     * 重建布隆过滤器时每批读取的文章 ID 数。
     */
    private static final int REBUILD_BATCH = 1000;

    private final ArticleService articleService;
    private final ArticleContentService articleContentService;
    private final ArticleTagService articleTagService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final CommentService commentService;
    private final ArticleCache articleCache;
    private final ArticleConverter articleConverter;
    private final CategoryConverter categoryConverter;
    private final CommentConverter commentConverter;
    private final UserApi userApi;
    private final CounterApi counterApi;

    private static ArticleCounts countsOf(Counts counts) {
        return new ArticleCounts(counts.get(CounterMetric.ARTICLE_LIKE), counts.get(CounterMetric.ARTICLE_FAVORITE),
                counts.get(CounterMetric.ARTICLE_COMMENT), counts.get(CounterMetric.ARTICLE_VIEW));
    }

    /**
     * 为文章查询追加基于 ID 游标的增量范围限制与升序排序。
     */
    private static LambdaQueryChainWrapper<Article> idsAfter(LambdaQueryChainWrapper<Article> query,
                                                             Long afterId,
                                                             int limit) {
        return query.gt(afterId != null, Article::getId, afterId).orderByAsc(Article::getId).last("LIMIT " + limit);
    }

    @Override
    public Optional<ArticleState> findPublished(long articleId) {
        return articleService.findPublished(articleId).map(articleConverter::toState);
    }

    @Override
    public Map<Long, ArticleBrief> getBriefs(Collection<Long> articleIds) {
        return articleCache.getBriefs(articleIds, missing -> articleService.listByIds(missing)
                .stream()
                .map(articleConverter::toBrief)
                .collect(Collectors.toMap(ArticleBrief::id, Function.identity())));
    }

    @Override
    public List<ArticleItem> listPublishedItems(List<Long> articleIds) {
        Map<Long, ArticleBrief> briefs = getBriefs(articleIds);
        return toItems(articleIds.stream()
                .map(briefs::get)
                .filter(brief -> brief != null && brief.status() == ArticleStatus.PUBLISHED)
                .toList());
    }

    @Override
    public Optional<ArticleSnapshot> findSnapshot(long articleId) {
        return Optional.ofNullable(articleService.getIncludingDeleted(articleId))
                .map(article -> toSnapshots(List.of(article)).getFirst());
    }

    @Override
    public List<ArticleSnapshot> listPublishedSnapshots(Long afterId, int limit) {
        return toSnapshots(listPublishedAfter(afterId, limit));
    }

    @Override
    public List<ArticleState> listPublishedStates(Long afterId, int limit) {
        return listPublishedAfter(afterId, limit).stream().map(articleConverter::toState).toList();
    }

    @Override
    public List<ArticleSnapshot> listSnapshotsUpdatedSince(LocalDateTime since, Long afterId, int limit) {
        return toSnapshots(articleService.listUpdatedSinceIncludingDeleted(since, afterId, limit));
    }

    @Override
    public Map<Long, CommentBrief> getCommentBriefs(Collection<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return Map.of();
        }
        return commentService.listByIds(commentIds)
                .stream()
                .map(commentConverter::toBrief)
                .collect(Collectors.toMap(CommentBrief::id, Function.identity()));
    }

    /**
     * 查询文章详情。草稿仅作者可见，已发布文章递增浏览量。
     *
     * @param id       文章 ID
     * @param viewerId 当前访问者用户 ID；匿名访问时为 {@code null}
     * @return 文章详情完整视图对象
     * @throws BizException 若文章不存在、已删除或非作者访问草稿抛出 {@link ArticleErrorCode#ARTICLE_NOT_FOUND}
     */
    public ArticleDetailVO getDetail(long id, Long viewerId) {
        ArticleCache.Detail detail = articleCache.getDetail(id, this::loadDetail);
        if (detail == null) {
            throw new BizException(ArticleErrorCode.ARTICLE_NOT_FOUND);
        }
        Article article = detail.article();
        if (article.getStatus() == ArticleStatus.DRAFT && !Objects.equals(article.getAuthorId(), viewerId)) {
            throw new BizException(ArticleErrorCode.ARTICLE_NOT_FOUND);
        }
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            counterApi.increment(CounterMetric.ARTICLE_VIEW, id, 1);
        }
        return articleConverter.toDetailVO(article, detail.content(), detail.category(), detail.tags(),
                userApi.getBriefs(List.of(article.getAuthorId())).get(article.getAuthorId()),
                countsOf(counterApi.get(CounterTarget.ARTICLE, List.of(id)).get(id)));
    }

    /**
     * 分页查询最新发布的有效文章，按发布时间降序排列（同秒发布的按 ID 降序）。
     *
     * @param categoryId 分类筛选条件；为 {@code null} 时不过滤
     * @param tagId      标签筛选条件；为 {@code null} 时不过滤
     * @param page       页码（从 1 开始）
     * @param size       每页记录数
     * @return 文章列表复合展示项的分页结果
     */
    public PageResult<ArticleItem> pageLatest(Long categoryId, Long tagId, long page, long size) {
        Page<Article> result = articleService.lambdaQuery()
                .eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .eq(categoryId != null, Article::getCategoryId, categoryId)
                // 经由 article_tag 的反向索引 idx_tag_article 找出文章；tagId 是 Long，拼接不会注入
                .inSql(tagId != null, Article::getId, "SELECT article_id FROM article_tag WHERE tag_id = " + tagId)
                .orderByDesc(Article::getPublishedAt)
                .orderByDesc(Article::getId)
                .page(new Page<>(page, size));
        return new PageResult<>(toItems(toBriefs(result.getRecords())), result.getTotal(), page, size);
    }

    /**
     * 基于游标分页查询指定作者已发布的有效文章列表，按文章 ID 降序排列。
     *
     * @param authorId 作者用户 ID
     * @param cursor   游标偏移量（上一页最后一条记录的 ID），首批传 {@code null}
     * @param size     单页记录数
     * @return 游标分页结果
     */
    public CursorResult<ArticleItem> listPublishedByAuthor(long authorId, Long cursor, int size) {
        return toCursorResult(authorArticles(authorId, ArticleStatus.PUBLISHED, cursor, size), size);
    }

    /**
     * 基于游标分页查询当前作者自己的草稿列表，按文章 ID 降序排列。
     *
     * @param authorId 作者用户 ID
     * @param cursor   游标偏移量（上一页最后一条记录的 ID），首批传 {@code null}
     * @param size     单页记录数
     * @return 游标分页结果
     */
    public CursorResult<ArticleItem> listDrafts(long authorId, Long cursor, int size) {
        return toCursorResult(authorArticles(authorId, ArticleStatus.DRAFT, cursor, size), size);
    }

    @Override
    public void afterSingletonsInstantiated() {
        articleCache.rebuildBloomFilterIfAbsent(this::listIdsAfter);
    }

    /**
     * 查询指定作者处于指定状态的文章列表，超量获取 1 条以支持游标分页边界判定。
     */
    private List<Article> authorArticles(long authorId, ArticleStatus status, Long cursor, int size) {
        return articleService.lambdaQuery()
                .eq(Article::getAuthorId, authorId)
                .eq(Article::getStatus, status)
                .lt(cursor != null, Article::getId, cursor)
                .orderByDesc(Article::getId)
                .last("LIMIT " + (size + 1))
                .list();
    }

    /**
     * 基于游标批量获取已发布文章列表，按文章 ID 升序排列。
     *
     * @param afterId 游标偏移量，仅返回 ID 大于该值的记录；为 {@code null} 时从起始遍历
     * @param limit   单批返回的最大记录数
     * @return 已发布文章列表
     */
    private List<Article> listPublishedAfter(Long afterId, int limit) {
        return idsAfter(articleService.lambdaQuery().eq(Article::getStatus, ArticleStatus.PUBLISHED), afterId,
                limit).list();
    }

    /**
     * 基于游标批量获取有效文章（包含草稿，未软删除）的 ID 列表，专用于布隆过滤器全量初始化。
     *
     * @param afterId 游标偏移量，仅返回 ID 大于该值的记录；为 {@code null} 时从起始遍历
     * @return 文章 ID 列表
     */
    private List<Long> listIdsAfter(Long afterId) {
        return idsAfter(articleService.lambdaQuery().select(Article::getId), afterId, REBUILD_BATCH).list()
                .stream()
                .map(Article::getId)
                .toList();
    }

    /**
     * 将超量拉取的实体列表封装为包含 {@code nextCursor} 与 {@code hasMore} 的游标分页结果。
     */
    private CursorResult<ArticleItem> toCursorResult(List<Article> fetched, int size) {
        CursorResult<Article> page = CursorResult.ofOverfetched(fetched, size, Article::getId);
        return new CursorResult<>(toItems(toBriefs(page.list())), page.nextCursor(), page.hasMore());
    }

    /**
     * 将文章实体列表批量转换为摘要投影列表。
     */
    private List<ArticleBrief> toBriefs(List<Article> articles) {
        return articles.stream().map(articleConverter::toBrief).toList();
    }

    /**
     * 批量补齐分类、作者简要信息与实时互动统计，生成文章列表项投影（严格保持入参顺序）。
     */
    private List<ArticleItem> toItems(List<ArticleBrief> articles) {
        if (articles.isEmpty()) {
            return List.of();
        }
        Map<Long, CategoryBrief> categories =
                categoryService.listByIds(articles.stream().map(ArticleBrief::categoryId).distinct().toList())
                        .stream()
                        .collect(Collectors.toMap(Category::getId, categoryConverter::toBrief));
        Map<Long, UserBrief> authors =
                userApi.getBriefs(articles.stream().map(ArticleBrief::authorId).distinct().toList());
        Map<Long, Counts> counts =
                counterApi.get(CounterTarget.ARTICLE, articles.stream().map(ArticleBrief::id).toList());
        return articles.stream()
                .map(article -> articleConverter.toItem(article, categories.get(article.categoryId()),
                        authors.get(article.authorId()), countsOf(counts.get(article.id()))))
                .toList();
    }

    /**
     * 批量补齐 Markdown 正文全文与关联标签，生成完整快照列表（支持包含已删除文章，严格保持入参顺序）。
     */
    private List<ArticleSnapshot> toSnapshots(List<Article> articles) {
        if (articles.isEmpty()) {
            return List.of();
        }
        List<Long> ids = articles.stream().map(Article::getId).toList();
        Map<Long, String> contents = articleContentService.listByIds(ids)
                .stream()
                .collect(Collectors.toMap(ArticleContent::getArticleId, ArticleContent::getContent));
        Map<Long, List<Long>> tagIds = articleTagService.listTagIds(ids);
        Map<Long, String> tagNames =
                tagService.listInOrder(tagIds.values().stream().flatMap(List::stream).distinct().toList())
                        .stream()
                        .collect(Collectors.toMap(Tag::getId, Tag::getName));
        return articles.stream().map(article -> {
            List<Long> ownTagIds = tagIds.getOrDefault(article.getId(), List.of());
            return articleConverter.toSnapshot(article, contents.get(article.getId()), ownTagIds,
                    ownTagIds.stream().map(tagNames::get).toList());
        }).toList();
    }

    /**
     * 回源查询文章详情中适合放入多级缓存的核心数据（元数据、正文、分类与标签）。
     *
     * @param id 文章 ID
     * @return 文章详情缓存项；若文章不存在或已软删除则返回 {@code null}
     */
    private ArticleCache.Detail loadDetail(long id) {
        Article article = articleService.getById(id);
        if (article == null) {
            return null;
        }
        ArticleSnapshot snapshot = toSnapshots(List.of(article)).getFirst();
        List<TagVO> tags = new ArrayList<>(snapshot.tagIds().size());
        for (int i = 0; i < snapshot.tagIds().size(); i++) {
            tags.add(new TagVO(snapshot.tagIds().get(i), snapshot.tagNames().get(i)));
        }
        return new ArticleCache.Detail(article, snapshot.content(),
                categoryConverter.toBrief(categoryService.getById(article.getCategoryId())), tags);
    }
}
