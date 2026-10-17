package com.echocyan.codenest.search.dto;

/**
 * 文章搜索排序策略。
 */
public enum SearchSort {

    /**
     * 按相关度得分降序（默认排序）。
     */
    RELEVANCE,

    /**
     * 按文章发布时间降序。
     */
    LATEST
}
