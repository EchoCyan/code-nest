package com.echocyan.codenest.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户持久化实体。
 * <p>
 * 映射数据库 {@code user} 表，继承 {@link AuditableEntity} 具备创建与更新时间审计字段。
 */
@Getter
@Setter
@TableName("`user`")
public class User extends AuditableEntity {

    /**
     * 用户 ID（雪花算法生成主键）。
     */
    private Long id;

    /**
     * 登录用户名（唯一索引约束，注册后不可修改）。
     */
    private String username;

    /**
     * 密码 BCrypt 哈希值。
     */
    private String passwordHash;

    /**
     * 用户昵称。
     */
    private String nickname;

    /**
     * 头像图片 URL 地址。
     */
    private String avatarUrl;

    /**
     * 个人简介。
     */
    private String bio;
}

