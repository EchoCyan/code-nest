package com.echocyan.codenest.interaction.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 文章点赞实体。
 *
 * <p>映射 {@code article_like} 关系表，业务上由 {@code (user_id, article_id)} 唯一键约束。
 */
@Getter
@Setter
@TableName("article_like")
public class ArticleLike extends AuditableEntity {

    /**
     * 点赞记录主键 ID。
     */
    private Long id;

    /**
     * 点赞用户 ID。
     */
    private Long userId;

    /**
     * 被点赞的文章 ID。
     */
    private Long articleId;

    /**
     * 被点赞文章的作者 ID（冗余存储，供对账直接按作者统计获赞数）。
     */
    private Long authorId;
}
