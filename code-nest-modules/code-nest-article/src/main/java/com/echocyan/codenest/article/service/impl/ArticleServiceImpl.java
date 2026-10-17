package com.echocyan.codenest.article.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.api.ArticleErrorCode;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.article.api.event.ArticleDeletedEvent;
import com.echocyan.codenest.article.api.event.ArticlePublishedEvent;
import com.echocyan.codenest.article.api.event.ArticleUpdatedEvent;
import com.echocyan.codenest.article.dto.ArticleRequest;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.entity.ArticleContent;
import com.echocyan.codenest.article.entity.Tag;
import com.echocyan.codenest.article.mapper.ArticleMapper;
import com.echocyan.codenest.article.service.ArticleCache;
import com.echocyan.codenest.article.service.ArticleContentService;
import com.echocyan.codenest.article.service.ArticleService;
import com.echocyan.codenest.article.service.ArticleTagService;
import com.echocyan.codenest.article.service.CategoryService;
import com.echocyan.codenest.article.service.TagService;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.exception.CommonErrorCode;
import com.echocyan.codenest.common.util.DateTimes;
import com.echocyan.codenest.common.util.Texts;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterSource;
import com.echocyan.codenest.counter.api.IdCount;
import com.echocyan.codenest.framework.mq.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 文章写服务实现类。
 */
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService, CounterSource {

    /**
     * 自动截取正文起始文本作为摘要的最大字符数。
     */
    private static final int AUTO_SUMMARY_LENGTH = 100;

    private final ArticleContentService articleContentService;
    private final ArticleTagService articleTagService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final CounterApi counterApi;
    private final DomainEventPublisher eventPublisher;
    private final ArticleCache articleCache;

    /**
     * 根据请求构建可由作者编辑的文章主表实体。
     */
    private static Article articleOf(ArticleRequest request) {
        Article article = new Article();
        article.setTitle(request.title());
        article.setSummary(summaryOf(request));
        article.setCoverUrl(request.coverUrl());
        article.setCategoryId(request.categoryId());
        return article;
    }

    /**
     * 构建正文扩展表实体。
     */
    private static ArticleContent contentOf(long articleId, ArticleRequest request) {
        ArticleContent content = new ArticleContent();
        content.setArticleId(articleId);
        content.setContent(request.content());
        return content;
    }

    /**
     * 提取文章摘要；未显式指定时自动截取正文起始纯文本。
     */
    private static String summaryOf(ArticleRequest request) {
        if (request.summary() != null && !request.summary().isBlank()) {
            return request.summary();
        }
        return Texts.head(request.content().strip(), AUTO_SUMMARY_LENGTH);
    }

    private static List<Long> tagIdsOf(ArticleRequest request) {
        return request.tagIds() == null ? List.of() : request.tagIds().stream().distinct().toList();
    }

    @Override
    @Transactional
    public Article create(long authorId, ArticleRequest request) {
        List<Long> tagIds = tagIdsOf(request);
        checkCategoryAndTags(request.categoryId(), tagIds);
        Article article = articleOf(request);
        article.setAuthorId(authorId);
        article.setStatus(ArticleStatus.DRAFT);
        article.setVersion(0);
        save(article);
        articleCache.added(article.getId());

        articleContentService.save(contentOf(article.getId(), request));
        articleTagService.replaceTags(article.getId(), tagIds);
        return article;
    }

    @Override
    @Transactional
    public Article update(long id, long userId, int version, ArticleRequest request) {
        getOwned(id, userId);
        List<Long> tagIds = tagIdsOf(request);
        checkCategoryAndTags(request.categoryId(), tagIds);
        Article article = articleOf(request);
        article.setId(id);
        article.setVersion(version);
        updateOrConflict(article);

        articleContentService.updateById(contentOf(id, request));
        articleTagService.replaceTags(id, tagIds);
        articleCache.evict(id);
        eventPublisher.publish(new ArticleUpdatedEvent(id, userId));
        return article;
    }

    @Override
    @Transactional
    public Article publish(long id, long userId) {
        Article article = getOwned(id, userId);
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            return article;
        }
        article.setStatus(ArticleStatus.PUBLISHED);
        article.setPublishedAt(DateTimes.now());
        updateOrConflict(article);
        counterApi.increment(CounterMetric.USER_ARTICLE, userId, 1);
        articleCache.evict(id);
        eventPublisher.publish(new ArticlePublishedEvent(id, userId));
        return article;
    }

    @Override
    @Transactional
    public void delete(long id, long userId) {
        Article article = getOwned(id, userId);
        // 带上读到的版本号：期间被发布或删除时按冲突处理，保证按读到的状态增减文章数是正确的。
        // 乐观锁插件比对版本号并把它 +1，搜索同步以此让删除覆盖此前的写入
        Article deletion = new Article();
        deletion.setVersion(article.getVersion());
        boolean deleted = lambdaUpdate().set(Article::getDeleted, 1).eq(Article::getId, id).update(deletion);
        if (!deleted) {
            throw new BizException(ArticleErrorCode.VERSION_CONFLICT);
        }
        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            counterApi.increment(CounterMetric.USER_ARTICLE, userId, -1);
        }
        articleCache.evict(id);
        eventPublisher.publish(new ArticleDeletedEvent(id, userId));
    }

    @Override
    public Optional<Article> findPublished(long id) {
        return Optional.ofNullable(getById(id)).filter(article -> article.getStatus() == ArticleStatus.PUBLISHED);
    }

    @Override
    public Article getIncludingDeleted(long id) {
        return baseMapper.selectByIdIncludingDeleted(id);
    }

    @Override
    public List<Article> listUpdatedSinceIncludingDeleted(LocalDateTime since, Long afterId, int limit) {
        return baseMapper.selectUpdatedSinceIncludingDeleted(since, afterId == null ? 0 : afterId, limit);
    }

    @Override
    public Set<CounterMetric> metrics() {
        return Set.of(CounterMetric.USER_ARTICLE);
    }

    @Override
    public List<IdCount> countAfter(CounterMetric metric, long afterId, int limit) {
        return baseMapper.countPublishedByAuthor(afterId, limit);
    }

    /**
     * 校验当前操作人是否为目标文章的作者。
     *
     * @param id     文章 ID
     * @param userId 操作人用户 ID
     * @return 归属校验通过的文章实体
     * @throws BizException 若文章不存在抛出 {@link ArticleErrorCode#ARTICLE_NOT_FOUND}；
     *                      若非作者本人操作抛出 {@link CommonErrorCode#FORBIDDEN}
     */
    private Article getOwned(long id, long userId) {
        Article article = getById(id);
        if (article == null) {
            throw new BizException(ArticleErrorCode.ARTICLE_NOT_FOUND);
        }
        if (article.getAuthorId() != userId) {
            throw new BizException(CommonErrorCode.FORBIDDEN);
        }
        return article;
    }

    /**
     * 执行带乐观锁版本校验的更新；更新失败抛出版本冲突异常。
     *
     * @param article 待更新的文章实体
     * @throws BizException 若版本号不一致导致更新影响行数为 0 抛出 {@link ArticleErrorCode#VERSION_CONFLICT}
     */
    private void updateOrConflict(Article article) {
        if (!updateById(article)) {
            throw new BizException(ArticleErrorCode.VERSION_CONFLICT);
        }
    }

    /**
     * 校验指定分类及标签集合的合法性。
     *
     * @param categoryId 分类 ID
     * @param tagIds     标签 ID 列表
     * @throws BizException 若分类不存在抛出 {@link ArticleErrorCode#CATEGORY_NOT_FOUND}；
     *                      若存在无效标签抛出 {@link ArticleErrorCode#TAG_NOT_FOUND}
     */
    private void checkCategoryAndTags(long categoryId, List<Long> tagIds) {
        if (categoryService.getById(categoryId) == null) {
            throw new BizException(ArticleErrorCode.CATEGORY_NOT_FOUND);
        }
        if (!tagIds.isEmpty() && tagService.lambdaQuery().in(Tag::getId, tagIds).count() < tagIds.size()) {
            throw new BizException(ArticleErrorCode.TAG_NOT_FOUND);
        }
    }
}
