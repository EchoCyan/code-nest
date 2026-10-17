package com.echocyan.codenest.article.vo;

import com.echocyan.codenest.article.api.ArticleCounts;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.article.api.CategoryBrief;
import com.echocyan.codenest.user.api.UserBrief;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章详情完整视图对象。
 * <p>
 * 聚合基础元数据、Markdown 正文、分类、标签集合、作者简要信息及实时交互统计数据。
 *
 * @param id          文章 ID
 * @param title       文章标题
 * @param summary     文章纯文本摘要
 * @param coverUrl    封面图 URL
 * @param content     Markdown 格式正文内容
 * @param status      文章生命周期状态
 * @param publishedAt 发布时间；处于草稿状态时为 {@code null}
 * @param createdAt   创建时间
 * @param updatedAt   最后修改时间
 * @param version     当前乐观锁版本号
 * @param category    所属分类投影
 * @param tags        关联标签列表
 * @param author      作者简要信息；若用户不存在或已注销则为 {@code null}
 * @param counts      实时互动计数（点赞、收藏、评论、浏览）
 */
public record ArticleDetailVO(
        Long id,
        String title,
        String summary,
        String coverUrl,
        String content,
        ArticleStatus status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer version,
        CategoryBrief category,
        List<TagVO> tags,
        UserBrief author,
        ArticleCounts counts) {
}
