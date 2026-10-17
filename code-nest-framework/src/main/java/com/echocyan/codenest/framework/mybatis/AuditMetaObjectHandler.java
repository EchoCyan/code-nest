package com.echocyan.codenest.framework.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.echocyan.codenest.common.util.DateTimes;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 实体审计字段自动填充处理器。
 *
 * <p>统一拦截数据库插入与更新操作，自动化填充基类 {@link AuditableEntity} 中约定的时间戳：
 * <ul>
 *   <li><b>新增插入阶段（Insert）：</b>同时为 {@code createdAt}（创建时间）与 {@code updatedAt}（更新时间）
 *       填充当前全站标准系统时间（{@link DateTimes#now()}）；采用严格填充模式（{@code strictInsertFill}），
 *       若业务代码显式指定了时间则予以尊重。</li>
 *   <li><b>修改更新阶段（Update）：</b>强制刷新 {@code updatedAt} 为当前系统时间；
 *       避开 MyBatis-Plus 默认 {@code strictUpdateFill} 在字段非空时拒绝覆盖的限制，
 *       采用 {@code setFieldValByName} 确保任意实体的单行更新都能精确反映真实更新时刻。</li>
 * </ul>
 */
public class AuditMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时自动填充创建时间与初始更新时间。
     *
     * @param metaObject MyBatis 实体反射元数据对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = DateTimes.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    /**
     * 更新时强制刷新最后修改时间。
     *
     * @param metaObject MyBatis 实体反射元数据对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // strictUpdateFill 在字段已有值时不覆盖，这里要求每次业务更新都强制刷新
        setFieldValByName("updatedAt", DateTimes.now(), metaObject);
    }
}
