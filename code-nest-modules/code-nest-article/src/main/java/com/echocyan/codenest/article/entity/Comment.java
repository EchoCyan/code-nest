package com.echocyan.codenest.article.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 评论实体。
 */
@Getter
@Setter
@TableName("comment")
public class Comment extends AuditableEntity {

    /**
     * 根评论的父级标识常量（{@code 0L}），用于区分一级评论与二级回复。
     */
    public static final long NO_ROOT = 0L;

    /**
     * 评论或回复唯一 ID。
     */
    private Long id;

    /**
     * 所属文章 ID。
     */
    private Long articleId;

    /**
     * 发表者用户 ID。
     */
    private Long userId;

    /**
     * 所属根评论 ID；一级评论自身为 {@value #NO_ROOT}。
     */
    private Long rootId;

    /**
     * 被回复的目标用户 ID；直接回复根评论时为 {@code null}，仅在指定 @某人 或回复楼中楼时有效。
     */
    private Long replyToUserId;

    /**
     * 评论或回复文本内容。
     */
    private String content;

    /**
     * 逻辑删除标识（0: 正常, 1: 已删除）。
     */
    @TableLogic
    private Integer deleted;

    /**
     * 判断当前实体是否为二级回复。
     *
     * @return 当 {@link #rootId} 不等于 {@value #NO_ROOT} 时返回 {@code true}
     */
    public boolean isReply() {
        return rootId != NO_ROOT;
    }
}
