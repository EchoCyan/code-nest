package com.echocyan.codenest.counter.api;

import java.util.Map;

/**
 * 单个目标对象的各项计数指标聚合结果。
 *
 * @param values 指标到对应计数值的映射
 */
public record Counts(Map<CounterMetric, Long> values) {

    /**
     * 获取指定指标的计数值。
     *
     * @param metric 计数指标
     * @return 对应的计数值；若无记录则返回 0
     */
    public long get(CounterMetric metric) {
        return values.getOrDefault(metric, 0L);
    }
}
