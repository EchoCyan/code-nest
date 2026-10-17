package com.echocyan.codenest.article.api;


import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文章模块对外暴露的公共门面接口。
 */
public interface ArticleApi {

    /**
     * 查询指定已发布文章的状态与作者信息。
     *
     * @param articleId 文章 ID
     * @return 文章状态；若不存在、为草稿或已删除则返回 {@link Optional#empty()}
     */
    Optional<ArticleState> findPublished(long articleId);

    /**
     * 批量查询文章摘要。
     *
     * @param articleIds 文章 ID 集合
     * @return 文章 ID 与摘要投影的映射
     */
    Map<Long, ArticleBrief> getBriefs(Collection<Long> articleIds);

    /**
     * 批量组装已发布文章的列表项，保持传入 ID 列表的顺序。
     *
     * @param articleIds 文章 ID 列表
     * @return 已发布文章的列表项列表
     */
    List<ArticleItem> listPublishedItems(List<Long> articleIds);

    /**
     * 查询指定文章的完整快照（包含已删除记录）。
     *
     * @param articleId 文章 ID
     * @return 文章快照；若从未创建则返回 {@link Optional#empty()}
     */
    Optional<ArticleSnapshot> findSnapshot(long articleId);

    /**
     * 基于游标批量获取已发布文章的快照，按 ID 升序排列。
     *
     * @param afterId 游标偏移量，为 {@code null} 时从头遍历
     * @param limit   单批最大记录数
     * @return 已发布文章快照列表
     */
    List<ArticleSnapshot> listPublishedSnapshots(Long afterId, int limit);

    /**
     * 基于游标批量获取已发布文章的状态与作者，按 ID 升序排列。
     *
     * @param afterId 游标偏移量，为 {@code null} 时从头遍历
     * @param limit   单批最大记录数
     * @return 已发布文章状态列表
     */
    List<ArticleState> listPublishedStates(Long afterId, int limit);

    /**
     * 基于游标批量获取指定时间后发生变更的文章快照（包含草稿与已删除记录），按 ID 升序排列。
     *
     * @param since   更新时间起始下限
     * @param afterId 游标偏移量，为 {@code null} 时从头遍历
     * @param limit   单批最大记录数
     * @return 变更文章快照列表
     */
    List<ArticleSnapshot> listSnapshotsUpdatedSince(LocalDateTime since, Long afterId, int limit);

    /**
     * 批量查询评论或回复的摘要。
     *
     * @param commentIds 评论或回复 ID 集合
     * @return 评论 ID 与摘要的映射
     */
    Map<Long, CommentBrief> getCommentBriefs(Collection<Long> commentIds);
}
