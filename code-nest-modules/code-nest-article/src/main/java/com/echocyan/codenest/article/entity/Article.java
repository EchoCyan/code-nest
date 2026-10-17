package com.echocyan.codenest.article.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.echocyan.codenest.article.api.ArticleStatus;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 文章实体。
 */
@Getter
@Setter
@TableName("article")
public class Article extends AuditableEntity {

    /**
     * 文章分布式唯一 ID。
     */
    private Long id;

    /**
     * 作者用户 ID。
     */
    private Long authorId;

    /**
     * 所属分类 ID。
     */
    private Long categoryId;

    /**
     * 文章标题。
     */
    private String title;

    /**
     * 文章纯文本摘要。
     */
    private String summary;

    /**
     * 封面图 URL。更新时强制覆盖，允许传入 {@code null} 以清空封面。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String coverUrl;

    /**
     * 文章生命周期状态。
     */
    private ArticleStatus status;

    /**
     * 正式发布时间；草稿态下为 {@code null}。
     */
    private LocalDateTime publishedAt;

    /**
     * 乐观锁版本号。编辑、发布与软删除时自动比对并递增，同时作为同步外部系统的版本标尺。
     */
    @Version
    private Integer version;

    /**
     * 逻辑删除标识（0: 正常, 1: 已删除）。
     */
    @TableLogic
    private Integer deleted;
}
