package com.echocyan.codenest.article.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章全量数据快照。
 *
 * @param id          文章 ID
 * @param authorId    作者用户 ID
 * @param categoryId  所属分类 ID
 * @param title       文章标题
 * @param summary     文章摘要
 * @param content     正文 Markdown 内容
 * @param tagIds      关联标签 ID 列表
 * @param tagNames    关联标签名称列表
 * @param status      文章状态
 * @param publishedAt 发布时间；未发布时为 {@code null}
 * @param version     版本号
 * @param deleted     是否软删除
 */
public record ArticleSnapshot(
        Long id,
        Long authorId,
        Long categoryId,
        String title,
        String summary,
        String content,
        List<Long> tagIds,
        List<String> tagNames,
        ArticleStatus status,
        LocalDateTime publishedAt,
        int version,
        boolean deleted) {

    /**
     * 判断当前快照是否满足公开检索条件（已发布且未删除）。
     *
     * @return 符合检索条件返回 {@code true}
     */
    public boolean searchable() {
        return status == ArticleStatus.PUBLISHED && !deleted;
    }
}
