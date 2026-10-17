package com.echocyan.codenest.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 文章分类字典持久化实体。
 * <p>
 * 提供系统预置的文章类别，支持按自定义排序权重 {@link #sort} 升序展现。
 */
@Getter
@Setter
@TableName("category")
public class Category extends AuditableEntity {

    /**
     * 分类唯一 ID。
     */
    private Long id;

    /**
     * 分类显示名称。
     */
    private String name;

    /**
     * 排序权重值，数值越小越靠前。
     */
    private Integer sort;
}
