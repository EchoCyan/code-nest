package com.echocyan.codenest.framework.mq;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 本地事务消息表（Transactional Outbox）状态枚举。
 *
 * <p>数据库持久化映射 {@code status} 字段（{@link #code}）。
 * 状态流转：{@link #PENDING}（初始落库）&rarr; {@link #SENT}（投递成功）/ {@link #FAILED}（重试超限）。</p>
 *
 * @see MqOutbox
 * @see OutboxRelay
 */
public enum OutboxStatus {

    /**
     * 待投递（初始状态，编码 0）。
     * <p>伴随业务事务提交而落库的初始状态。该状态下的消息等待通过事务同步器即时投递，
     * 或者由补偿定时任务扫描并执行重试投递。</p>
     */
    PENDING(0),

    /**
     * 投递成功（终态，编码 1）。
     * <p>消息已成功发送至 MQ Broker 并收到发布确认（Publisher Confirm ACK）。
     * 处于该状态的记录已脱离投递重试流程，后续由定时归档任务物理删除或迁移。</p>
     */
    SENT(1),

    /**
     * 投递失败（告警终态，编码 2）。
     * <p>补发重试次数达到最大限制（{@code MAX_RETRY_COUNT}）或超出最长补偿时间窗口仍未投递成功。
     * 流转为此状态后将停止自动补发，等待运维监控告警以及人工介入修复。</p>
     */
    FAILED(2);

    /**
     * 数据库持久化存储的数字状态码。
     */
    @EnumValue
    private final int code;

    /**
     * 构造状态枚举项。
     *
     * @param code 数据库映射的状态数值编码
     */
    OutboxStatus(int code) {
        this.code = code;
    }
}
