package com.echocyan.codenest.counter.api;

/**
 * 单个目标对象的精确计数统计结果。
 *
 * @param id    目标对象 ID
 * @param count 该对象在事实表中的精确计数值
 */
public record IdCount(long id, long count) {
}
