package com.echocyan.codenest.framework.mq;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 事务发件箱（Transactional Outbox）记录数据库访问 Mapper 接口。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，提供针对 {@code mq_outbox} 表的增删改查操作，
 * 由 {@link DomainEventPublisher}（写入）、{@link OutboxSentMarker}（批量标记已发送）
 * 以及 {@link OutboxRelay}（加锁扫描与历史归档）协同调用。
 */
@Mapper
public interface MqOutboxMapper extends BaseMapper<MqOutbox> {
}
