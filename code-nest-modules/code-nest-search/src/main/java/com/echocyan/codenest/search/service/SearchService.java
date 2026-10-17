package com.echocyan.codenest.search.service;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleItem;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.search.SearchErrorCode;
import com.echocyan.codenest.search.dto.SearchSort;
import com.echocyan.codenest.search.vo.SearchArticleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文章搜索领域服务。
 * <p>
 * 负责前置深度分页校验、委托 {@link ArticleIndex} 执行倒排检索，并通过 {@link ArticleApi#listPublishedItems}
 * 批量回查组装文章列表项元数据。同步未完成的已删除文章会在组装阶段被过滤，命中总数 {@code total} 仍按 ES 返回值为准。
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    /**
     * 最大深度分页窗口限制（{@code from + size} 上限）。
     */
    private static final long MAX_WINDOW = 1000;

    private final ArticleIndex articleIndex;
    private final ArticleApi articleApi;

    /**
     * 在已发布文章中按关键词执行分页检索。
     *
     * @param keyword    搜索关键词
     * @param categoryId 分类 ID 筛选，为 {@code null} 表示不筛选
     * @param tagId      标签 ID 筛选，为 {@code null} 表示不筛选
     * @param sort       排序策略
     * @param page       当前页码（从 1 开始）
     * @param size       每页条数
     * @return 包含高亮信息的文章分页视图列表
     * @throws BizException 当 {@code page * size > MAX_WINDOW} 时抛出 {@link SearchErrorCode#PAGE_TOO_DEEP}
     */
    public PageResult<SearchArticleVO> search(String keyword,
                                              Long categoryId,
                                              Long tagId,
                                              SearchSort sort,
                                              long page,
                                              long size) {
        // from + size = page * size；使用除法比较，防止 page 极大时发生整数乘法溢出
        if (page > MAX_WINDOW / size) {
            throw new BizException(SearchErrorCode.PAGE_TOO_DEEP);
        }
        PageResult<ArticleIndex.Match> hits = articleIndex.search(keyword.strip(), categoryId, tagId, sort, page, size);
        Map<Long, ArticleItem> items =
                articleApi.listPublishedItems(hits.list().stream().map(ArticleIndex.Match::articleId).toList())
                        .stream()
                        .collect(Collectors.toMap(ArticleItem::id, Function.identity()));
        return new PageResult<>(hits.list()
                .stream()
                .filter(hit -> items.containsKey(hit.articleId()))
                .map(hit -> new SearchArticleVO(items.get(hit.articleId()), hit.titleHighlight(),
                        hit.contentHighlight()))
                .toList(), hits.total(), page, size);
    }
}
