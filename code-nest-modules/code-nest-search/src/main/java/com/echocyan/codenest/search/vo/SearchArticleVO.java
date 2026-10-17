package com.echocyan.codenest.search.vo;

import com.echocyan.codenest.article.api.ArticleItem;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * 搜索文章结果视图对象。
 * <p>
 * 扁平展开文章列表项元数据（{@link ArticleItem}），并附加检索命中字段的高亮片段。
 *
 * @param article          文章列表项元数据
 * @param titleHighlight   高亮后的标题 HTML 片段；未命中时为 {@code null}
 * @param contentHighlight 正文中命中关键词的高亮 HTML 片段；未命中时为 {@code null}
 */
public record SearchArticleVO(@JsonUnwrapped ArticleItem article, String titleHighlight, String contentHighlight) {
}
