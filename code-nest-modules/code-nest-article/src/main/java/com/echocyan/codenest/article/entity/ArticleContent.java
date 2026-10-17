package com.echocyan.codenest.article.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 文章正文扩展持久化实体。
 * <p>
 * 与 {@link Article} 保持 1:1 垂直分表关系，独立存储大体积 Markdown 正文字符串，避免列表扫描与缓存加载的 IO 放大。
 */
@Getter
@Setter
@TableName("article_content")
public class ArticleContent extends AuditableEntity {

    /**
     * 关联的文章主键 ID（外部输入模式）。
     */
    @TableId(type = IdType.INPUT)
    private Long articleId;

    /**
     * Markdown 格式正文内容，上限 100,000 字符。
     */
    private String content;
}
