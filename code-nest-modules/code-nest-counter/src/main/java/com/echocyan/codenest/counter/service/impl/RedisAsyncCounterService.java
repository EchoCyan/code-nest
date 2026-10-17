package com.echocyan.codenest.counter.service.impl;

import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterTarget;
import com.echocyan.codenest.counter.api.Counts;
import com.echocyan.codenest.counter.event.CounterChangedEvent;
import com.echocyan.codenest.framework.mq.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计数服务实现类。
 *
 * <p>基于 Redis 内存原子更新与 MySQL 异步批量落库解耦写入路径，消除热点对象行锁争用：
 * <ul>
 *   <li>精确计数：在活动事务中暂存变更，通过事务同步器于 {@code beforeCommit} 阶段合并为
 *       {@link CounterChangedEvent} 写入 Outbox，随业务事务一体化提交后异步处理。</li>
 *   <li>近似计数（浏览量）：直接写入 Redis 并标记待落库，不经过 MQ。</li>
 *   <li>对账纠偏：遵循“先写 Redis 再写 MySQL”的顺序，规避并发落库覆盖新值的风险。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
class RedisAsyncCounterService implements CounterApi {

    private final RedisCounterStore redisCounterStore;
    private final CounterTables counterTables;
    private final DomainEventPublisher domainEventPublisher;

    @Override
    public void increment(CounterMetric metric, long targetId, long delta) {
        if (metric == CounterMetric.ARTICLE_VIEW) {
            redisCounterStore.increment(metric, targetId, delta, null);
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Counter changes must be made in a transaction: " + metric);
        }
        pendingChanges().add(metric, targetId, delta);
    }

    /**
     * 获取或创建绑定至当前事务生命周期的变更同步器。
     *
     * @return 当前事务专用的 {@link PendingChanges} 同步器实例
     */
    private PendingChanges pendingChanges() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof PendingChanges pending && pending.owner == this) {
                return pending;
            }
        }
        PendingChanges pending = new PendingChanges(this);
        TransactionSynchronizationManager.registerSynchronization(pending);
        return pending;
    }

    @Override
    public Map<Long, Counts> get(CounterTarget target, Collection<Long> targetIds) {
        return redisCounterStore.get(target, targetIds);
    }

    @Override
    public void reset(CounterMetric metric, long targetId, long value) {
        if (value < 0) {
            throw new IllegalArgumentException("计数不能为负数: " + metric + "=" + value);
        }
        redisCounterStore.resetIfPresent(metric, targetId, value);
        counterTables.reset(metric, targetId, value);
    }

    /**
     * 事务生命周期同步回调实现，负责聚合事务内的计数变更并在提交前统一发布。
     */
    private static final class PendingChanges implements TransactionSynchronization {

        private final RedisAsyncCounterService owner;
        private final Map<CounterKey, Long> deltas = new LinkedHashMap<>();

        private PendingChanges(RedisAsyncCounterService owner) {
            this.owner = owner;
        }

        /**
         * 累加单项计数变更增量。
         */
        void add(CounterMetric metric, long targetId, long delta) {
            deltas.merge(new CounterKey(metric, targetId), delta, Long::sum);
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            List<CounterChangedEvent.Change> changes = deltas.entrySet().stream()
                    .filter(entry -> entry.getValue() != 0)
                    .map(entry -> new CounterChangedEvent.Change(entry.getKey().metric(), entry.getKey().targetId(),
                            entry.getValue()))
                    .toList();
            deltas.clear();
            if (!changes.isEmpty()) {
                owner.domainEventPublisher.publish(new CounterChangedEvent(changes));
            }
        }
    }

    /**
     * 计数变更聚合哈希键。
     *
     * @param metric   计数指标
     * @param targetId 目标对象 ID
     */
    private record CounterKey(CounterMetric metric, long targetId) {
    }
}
