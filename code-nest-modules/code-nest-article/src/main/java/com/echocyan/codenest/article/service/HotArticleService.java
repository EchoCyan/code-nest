package com.echocyan.codenest.article.service;

import com.echocyan.codenest.article.api.ArticleItem;
import com.echocyan.codenest.common.result.PageResult;

/**
 * 热门文章榜单服务接口。
 */
public interface HotArticleService {

    /** 榜单容量 */
    int CAPACITY = 100;

    /** 分页单页大小 */
    int PAGE_SIZE = 20;

    /** 最大访问页码 */
    int MAX_PAGE = 5;

    /**
     * 刷新热门文章榜单。
     */
    void refresh();

    /**
     * 分页查询热门文章列表。
     *
     * @param page 页码（从 1 开始，上限为 {@value #MAX_PAGE}）
     * @return 热门文章分页结果
     */
    PageResult<ArticleItem> page(int page);
}
