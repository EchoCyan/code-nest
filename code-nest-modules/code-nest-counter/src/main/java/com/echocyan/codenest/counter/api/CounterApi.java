package com.echocyan.codenest.counter.api;

import java.util.Collection;
import java.util.Map;

/**
 * 计数模块对外门面接口。
 *
 * <p>提供计数的增减、批量查询与对账重置能力，计数值下限统一为 0。
 */
public interface CounterApi {

    /**
     * 增减指定对象的计数指标。
     *
     * <p>精确计数须在活动事务中调用，随业务事务一起提交或回滚；
     * 近似计数（如浏览量）支持无事务独立累加。
     *
     * @param metric   计数指标
     * @param targetId 目标对象 ID
     * @param delta    变动增量，支持负数；累计计算结果低于 0 时按 0 处理
     * @throws IllegalStateException 若修改精确计数时当前不存在活动事务
     */
    void increment(CounterMetric metric, long targetId, long delta);

    /**
     * 批量获取指定目标类型的全部计数指标。
     *
     * @param target    目标对象类型
     * @param targetIds 目标对象 ID 集合
     * @return 对象 ID 到各项计数的映射；传入的每个 ID 均包含条目，无记录对象计数全为 0
     */
    Map<Long, Counts> get(CounterTarget target, Collection<Long> targetIds);

    /**
     * 重置指定对象的计数指标为绝对值，供数据对账纠偏使用。
     *
     * @param metric   计数指标
     * @param targetId 目标对象 ID
     * @param value    重置后的计数值，必须大于或等于 0
     * @throws IllegalArgumentException 若 value 为负数
     */
    void reset(CounterMetric metric, long targetId, long value);
}

