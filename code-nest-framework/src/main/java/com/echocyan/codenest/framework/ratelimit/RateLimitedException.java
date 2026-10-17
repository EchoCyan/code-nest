package com.echocyan.codenest.framework.ratelimit;

import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.exception.CommonErrorCode;

/**
 * 接口请求超出滑动窗口频率限额时抛出的专用限流异常。
 *
 * <p>属于业务异常体系（继承自 {@link BizException}），绑定通用错误码
 * {@link CommonErrorCode#TOO_MANY_REQUESTS}（错误码：90429，HTTP 状态码：429）。
 * 异常携带根据滑动窗口中最早记录滑出时间计算出的建议重试冷却时间，
 * 由全局异常处理器提取并设置到 HTTP {@code Retry-After} 响应头中。
 */
public class RateLimitedException extends BizException {

    /**
     * 建议客户端等待重试的冷却秒数（向上取整）。
     */
    private final long retryAfterSeconds;

    /**
     * 构造请求限流异常。
     *
     * @param retryAfterSeconds 客户端至少需要等待的冷却秒数
     */
    public RateLimitedException(long retryAfterSeconds) {
        super(CommonErrorCode.TOO_MANY_REQUESTS);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * 获取建议客户端等待的冷却秒数。
     *
     * @return 距离下一个限额名额腾出所需的等待秒数
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
