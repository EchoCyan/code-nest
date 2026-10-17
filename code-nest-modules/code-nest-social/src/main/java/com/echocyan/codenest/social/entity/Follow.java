package com.echocyan.codenest.social.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.echocyan.codenest.framework.mybatis.AuditableEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户关注关系实体。
 * <p>
 * 对应 {@code follow} 关系表，通过唯一索引 {@code uk_follower_author(follower_id, author_id)} 保证单向关注关系唯一。
 * 用户取消关注时执行物理删除。
 */
@Getter
@Setter
@TableName("follow")
public class Follow extends AuditableEntity {

    /**
     * 关注记录唯一标识。
     */
    private Long id;

    /**
     * 关注人（粉丝）用户 ID。
     */
    private Long followerId;

    /**
     * 被关注的作者用户 ID。
     */
    private Long authorId;
}
