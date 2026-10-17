package com.echocyan.codenest.counter.api;

import java.util.List;
import java.util.Set;

/**
 * 精确计数的真实数据源接口。
 *
 * <p>由掌握事实表（关系表或内容表）的业务模块实现并注册为 Spring Bean，供对账系统重新统计精确值。
 * 每个指标全局仅允许由一个数据源负责；近似计数（如浏览量）不提供数据源。
 */
public interface CounterSource {

    /**
     * 获取本数据源负责的计数指标集合。
     *
     * @return 负责的计数指标集合
     */
    Set<CounterMetric> metrics();

    /**
     * 基于事实表按对象 ID 游标重新分页统计精确计数。
     *
     * <p>未出现在任何统计结果中的对象，其精确计数值均视为 0。
     *
     * @param metric  目标指标，必须包含在 {@link #metrics()} 中
     * @param afterId 起始对象 ID（不含），仅统计 ID 大于该值的对象
     * @param limit   单页数量上限
     * @return 计数大于 0 的对象精确计数列表，按对象 ID 升序排列，至多返回 limit 条
     */
    List<IdCount> countAfter(CounterMetric metric, long afterId, int limit);
}
