package com.echocyan.codenest.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 文章与标签的多对多关联持久化实体。
 * <p>
 * 联合主键为 {@code (article_id, tag_id)}，并基于 {@code tag_id} 建立反向索引以加速标签维度的文章检索。
 */
@Getter
@Setter
@TableName("article_tag")
public class ArticleTag extends AuditableEntity {

    /**
     * 文章 ID。
     */
    private Long articleId;

    /**
     * 标签 ID。
     */
    private Long tagId;
}
