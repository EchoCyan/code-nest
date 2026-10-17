package com.echocyan.codenest.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.HighlighterEncoder;
import co.elastic.clients.elasticsearch.core.search.HighlighterType;
import co.elastic.clients.util.NamedValue;
import com.echocyan.codenest.common.result.PageResult;
import com.echocyan.codenest.search.dto.SearchSort;
import com.echocyan.codenest.search.service.ArticleIndex.Fields;
import com.echocyan.codenest.search.service.ArticleIndex.Match;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 文章检索执行器。
 * <p>
 * 封装 Elasticsearch 查询 DSL 构建与执行：
 * <ul>
 *     <li>多字段加权匹配：匹配 {@code title}（权重 3）、{@code summary}（权重 1.5）与 {@code content}（权重 1），分词词项需同时命中同字段（AND 算子）；</li>
 *     <li>精准标签提权：搜索关键词与 {@code tags} 完全匹配时提升相关度评分；</li>
 *     <li>布尔过滤：分类 ID 与标签 ID 作为 Filter 条件，不参与相关度评分计算并享用 Filter 缓存；</li>
 *     <li>关键词高亮：标题全量高亮，正文采用 Plain 高亮器截取单一片段（约 100 字符）。</li>
 * </ul>
 */
class ArticleSearcher {

    /**
     * 关键词完全匹配标签名时的加权分值。
     */
    private static final float TAG_BOOST = 5;

    /**
     * 正文高亮片段的最大字符截取长度。
     */
    private static final int CONTENT_FRAGMENT_SIZE = 100;

    private final ElasticsearchClient client;
    private final String alias;

    /**
     * @param client Elasticsearch 客户端
     * @param alias  检索路由的目标别名
     */
    ArticleSearcher(ElasticsearchClient client, String alias) {
        this.client = client;
        this.alias = alias;
    }

    /**
     * 构建全文检索与加权打分子句。
     *
     * @param bool    Bool 查询构建器
     * @param keyword 检索关键词
     * @return 填充后的 Bool 查询构建器
     */
    private static BoolQuery.Builder matching(BoolQuery.Builder bool, String keyword) {
        return bool
                .must(must -> must.multiMatch(match -> match
                        .query(keyword)
                        .fields(Fields.TITLE + "^3", Fields.SUMMARY + "^1.5", Fields.CONTENT)
                        .operator(Operator.And)))
                .should(should -> should.term(term -> term.field(Fields.TAGS).value(keyword).boost(TAG_BOOST)));
    }

    /**
     * 构建分类与标签过滤子句。
     *
     * @param bool       Bool 查询构建器
     * @param categoryId 分类 ID 过滤项，为 {@code null} 时忽略
     * @param tagId      标签 ID 过滤项，为 {@code null} 时忽略
     * @return 填充后的 Bool 查询构建器
     */
    private static BoolQuery.Builder filtered(BoolQuery.Builder bool, Long categoryId, Long tagId) {
        if (categoryId != null) {
            bool.filter(filter -> filter.term(term -> term.field(Fields.CATEGORY_ID).value(categoryId)));
        }
        if (tagId != null) {
            bool.filter(filter -> filter.term(term -> term.field(Fields.TAG_IDS).value(tagId)));
        }
        return bool;
    }

    /**
     * 构建排序规则列表。
     * <p>
     * 相关度模式按得分降序；次级排序统一使用发布时间倒序与文章 ID 倒序，保证相同分值或时间下的分页稳定性。
     *
     * @param sort 业务排序策略
     * @return ES 排序选项列表
     */
    private static List<SortOptions> sortOf(SearchSort sort) {
        List<SortOptions> options = new ArrayList<>();
        if (sort == SearchSort.RELEVANCE) {
            options.add(SortOptions.of(option -> option.score(score -> score.order(SortOrder.Desc))));
        }
        options.add(SortOptions.of(option -> option.field(field -> field
                .field(Fields.PUBLISHED_AT).order(SortOrder.Desc))));
        options.add(SortOptions.of(option -> option.field(field -> field.field(Fields.ID).order(SortOrder.Desc))));
        return options;
    }

    /**
     * 提取检索命中项中指定字段的高亮文本。
     *
     * @param hit   ES 检索命中条目
     * @param field 目标高亮字段名
     * @return 首个高亮片段文本；未命中高亮时返回 {@code null}
     */
    private static String highlightOf(co.elastic.clients.elasticsearch.core.search.Hit<Void> hit, String field) {
        List<String> fragments = hit.highlight().get(field);
        return fragments == null || fragments.isEmpty() ? null : fragments.getFirst();
    }

    /**
     * 执行文章检索并组装高亮分页结果。
     *
     * @param keyword    检索关键词
     * @param categoryId 分类 ID 筛选，为 {@code null} 时不筛选
     * @param tagId      标签 ID 筛选，为 {@code null} 时不筛选
     * @param sort       排序策略
     * @param page       当前页码（从 1 开始）
     * @param size       每页记录数
     * @return 包含命中文章 ID 与高亮片段的分页结果
     * @throws UncheckedIOException 当底层 Elasticsearch 通信发生 I/O 异常时
     */
    PageResult<Match> search(String keyword, Long categoryId, Long tagId, SearchSort sort, long page, long size) {
        SearchResponse<Void> response;
        try {
            response = client.search(search -> search
                    .index(alias)
                    .from((int) ((page - 1) * size))
                    .size((int) size)
                    .source(source -> source.fetch(false))
                    .query(query -> query.bool(bool -> filtered(matching(bool, keyword), categoryId, tagId)))
                    .sort(sortOf(sort))
                    .highlight(highlight -> highlight
                            .preTags("<em>")
                            .postTags("</em>")
                            .encoder(HighlighterEncoder.Html)
                            .fields(NamedValue.of(Fields.TITLE, HighlightField.of(field -> field.numberOfFragments(0))))
                            // plain 高亮器按字符数切片段；默认的 unified 按句切分，没有标点的长句会整句返回
                            .fields(NamedValue.of(Fields.CONTENT, HighlightField.of(field -> field
                                    .type(HighlighterType.Plain)
                                    .fragmentSize(CONTENT_FRAGMENT_SIZE)
                                    .numberOfFragments(1))))), Void.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<Match> list = response.hits().hits().stream()
                .map(hit -> new Match(Long.parseLong(hit.id()), highlightOf(hit, Fields.TITLE),
                        highlightOf(hit, Fields.CONTENT)))
                .toList();
        long total = Objects.requireNonNull(response.hits().total()).value();
        return new PageResult<>(list, total, page, size);
    }
}
