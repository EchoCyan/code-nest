package com.echocyan.codenest.interaction.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 文章收藏实体。
 *
 * <p>映射 {@code favorite} 关系表，业务上由 {@code (user_id, article_id)} 唯一键约束。
 */
@Getter
@Setter
@TableName("favorite")
public class Favorite extends AuditableEntity {

    /**
     * 收藏记录主键 ID。
     */
    private Long id;

    /**
     * 收藏用户 ID。
     */
    private Long userId;

    /**
     * 被收藏的文章 ID。
     */
    private Long articleId;
}
