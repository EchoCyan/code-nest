package com.echocyan.codenest.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 文章标签字典持久化实体。
 * <p>
 * 提供系统预置的文章技术标签，通过 {@link ArticleTag} 与文章建立多对多关联。
 */
@Getter
@Setter
@TableName("tag")
public class Tag extends AuditableEntity {

    /**
     * 标签唯一 ID。
     */
    private Long id;

    /**
     * 标签显示名称。
     */
    private String name;
}
