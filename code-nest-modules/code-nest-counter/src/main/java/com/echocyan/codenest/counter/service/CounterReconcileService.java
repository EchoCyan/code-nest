package com.echocyan.codenest.counter.service;

import com.echocyan.codenest.counter.api.CounterMetric;

import java.util.Map;
import java.util.Optional;

/**
 * 计数对账服务接口。
 *
 * <p>按各 {@link com.echocyan.codenest.counter.api.CounterSource} 重新统计出的精确事实数据，
 * 比对并纠偏 Redis 缓存与 MySQL 统计表中的不一致计数。近似计数（如浏览量）不参与对账。
 */
public interface CounterReconcileService {

    /**
     * 同步执行一次全量指标对账。
     *
     * <p>基于分布式互斥锁执行，确保集群内同一时刻仅有一个实例运行对账任务。
     *
     * @return 包含各指标被修正的对象数量映射；若其他实例正在执行对账导致获取锁失败，则返回 {@link Optional#empty()}
     */
    Optional<Map<CounterMetric, Long>> reconcile();
}
