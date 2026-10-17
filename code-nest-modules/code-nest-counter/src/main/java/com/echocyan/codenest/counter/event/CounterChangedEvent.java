package com.echocyan.codenest.counter.event;

import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.framework.mq.DomainEvent;

import java.util.List;

/**
 * 计数变更领域事件。
 *
 * <p>聚合单个业务事务内产生的所有计数变动，随事务经 Outbox 可靠投递，并由计数模块内部消费处理。
 *
 * @param changes 该事务内累积的所有计数变更列表
 */
@DomainEvent("counter.changed")
public record CounterChangedEvent(List<Change> changes) {

    /**
     * 单项计数变更明细。
     *
     * @param metric   计数指标
     * @param targetId 目标对象 ID
     * @param delta    变动增量（正数递增，负数递减）
     */
    public record Change(CounterMetric metric, long targetId, long delta) {
    }
}
