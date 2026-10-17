package com.echocyan.codenest.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 站内通知数据访问层映射器。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 统计指定接收人的未读通知数（设置最大扫描上限）。
     * <p>
     * 利用子查询 {@code LIMIT} 命中 {@code (recipient_id, is_read)} 联合索引，扫描达到上限即停止，避免大用户全量未读扫描。
     *
     * @param recipientId 接收人用户 ID
     * @param limit       最大扫描行数上限
     * @return 未读通知条数（不超过 limit）
     */
    @Select("""
            SELECT COUNT(*) FROM (
                SELECT 1 FROM notification WHERE recipient_id = #{recipientId} AND is_read = 0 LIMIT #{limit}
            ) t
            """)
    int countUnread(long recipientId, int limit);
}
