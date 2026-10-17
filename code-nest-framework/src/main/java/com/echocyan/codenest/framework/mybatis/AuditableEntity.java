package com.echocyan.codenest.framework.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 包含基础审计时间属性的持久化实体抽象基类。
 *
 * <p>全站所有数据库映射实体（内容表、关系表、状态记录表等）均统一继承本抽象类，
 * 标准化维护行记录的生命周期时间审计维度：
 * <ul>
 *   <li>数据库字段均映射为 {@code DATETIME} 类型；</li>
 *   <li>字段值由 {@link AuditMetaObjectHandler} 在 SQL 执行前自动拦截并填充，业务层无需手动设值。</li>
 * </ul>
 */
@Getter
@Setter
public abstract class AuditableEntity {

    /**
     * 实体记录首次创建时间。在执行数据插入时自动填充当前系统时刻，后续更新保持不变。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 实体记录最后修改时间。在数据插入及每次业务字段更新时均由处理器强制刷新。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
