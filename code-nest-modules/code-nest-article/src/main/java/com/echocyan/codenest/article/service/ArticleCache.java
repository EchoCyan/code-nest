package com.echocyan.codenest.article.service;

import com.echocyan.codenest.article.api.ArticleBrief;
import com.echocyan.codenest.article.api.CategoryBrief;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.vo.TagVO;
import com.echocyan.codenest.framework.cache.BloomFilter;
import com.echocyan.codenest.framework.cache.TwoLevelCache;
import com.echocyan.codenest.framework.cache.TwoLevelCaches;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 文章缓存管理组件，维护文章详情（Caffeine + Redis）与摘要缓存。
 */
@Component
public class ArticleCache {

    private final BloomFilter bloomFilter;
    private final TwoLevelCache<Detail> details;
    private final TwoLevelCache<ArticleBrief> briefs;

    ArticleCache(TwoLevelCaches caches) {
        this.bloomFilter = caches.bloomFilter("article");
        this.details = caches.createTwoLevel("article:detail", Detail.class, bloomFilter);
        this.briefs = caches.create("article:brief", ArticleBrief.class);
    }

    /**
     * 读取文章详情缓存。
     *
     * @param id     文章 ID
     * @param loader 缓存未命中时的回源加载函数
     * @return 文章详情缓存项；未命中且加载为空时返回 {@code null}
     */
    public Detail getDetail(long id, Function<Long, Detail> loader) {
        return details.get(id, loader);
    }

    /**
     * 批量读取文章摘要缓存。
     *
     * @param ids    文章 ID 集合
     * @param loader 缺失键的回源加载函数
     * @return 文章 ID 与摘要投影的映射
     */
    public Map<Long, ArticleBrief> getBriefs(Collection<Long> ids,
                                             Function<Collection<Long>, Map<Long, ArticleBrief>> loader) {
        return briefs.getAll(ids, loader);
    }

    /**
     * 将文章 ID 添加至布隆过滤器。
     *
     * @param id 文章 ID
     */
    public void added(long id) {
        bloomFilter.add(id);
    }

    /**
     * 淘汰指定文章的详情与摘要缓存。
     *
     * @param id 文章 ID
     */
    public void evict(long id) {
        details.evict(id);
        briefs.evict(id);
    }

    /**
     * 当布隆过滤器不存在时执行全量重建。
     *
     * @param idsAfter 游标分页拉取文章 ID 的回调函数
     */
    public void rebuildBloomFilterIfAbsent(Function<Long, List<Long>> idsAfter) {
        bloomFilter.rebuildIfAbsent(idsAfter);
    }

    /**
     * 文章详情缓存结构。
     *
     * @param article  文章实体
     * @param content  Markdown 正文
     * @param category 所属分类
     * @param tags     关联标签列表
     */
    public record Detail(Article article, String content, CategoryBrief category, List<TagVO> tags) {
    }
}
