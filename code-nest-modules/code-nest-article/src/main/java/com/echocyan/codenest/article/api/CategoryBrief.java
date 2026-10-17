package com.echocyan.codenest.article.api;

/**
 * 文章分类简要投影。
 *
 * @param id   分类 ID
 * @param name 分类显示名称
 */
public record CategoryBrief(Long id, String name) {
}
