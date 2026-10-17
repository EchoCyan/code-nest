package com.echocyan.codenest.framework.mq;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息消费防重记录数据库访问 Mapper 接口。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，提供针对 {@code mq_consume_record} 表的 CRUD 基础数据操作，
 * 主要由 {@link IdempotentConsumerAspect} 用于原子插入防重消费记录。
 */
@Mapper
public interface MqConsumeRecordMapper extends BaseMapper<MqConsumeRecord> {
}
