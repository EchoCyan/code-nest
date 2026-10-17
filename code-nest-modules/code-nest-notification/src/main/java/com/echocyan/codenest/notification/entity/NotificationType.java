package com.echocyan.codenest.notification.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 通知类型枚举。
 * <p>
 * 数据库持久化存储数值代码（{@link #code}），API 接口序列化输出枚举名称。
 */
public enum NotificationType {

    /**
     * 文章被点赞通知（关联文章）。
     */
    LIKE(1),

    /**
     * 文章被评论通知（关联文章与评论）。
     */
    COMMENT(2),

    /**
     * 评论被回复通知（关联文章与回复）。
     */
    REPLY(3),

    /**
     * 用户被关注通知（仅关联动作触发者）。
     */
    FOLLOW(4);

    /**
     * 数据库存储代码。
     */
    @EnumValue
    private final int code;

    NotificationType(int code) {
        this.code = code;
    }
}
