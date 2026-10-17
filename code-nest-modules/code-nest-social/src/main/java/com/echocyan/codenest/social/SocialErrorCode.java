package com.echocyan.codenest.social;

import com.echocyan.codenest.common.exception.ErrorCode;

/**
 * 社交与关注模块业务错误码（号段：4xxxx）。
 */
public enum SocialErrorCode implements ErrorCode {

    /**
     * 用户不能关注自己。
     */
    CANNOT_FOLLOW_SELF(40001, "不能关注自己", 400);

    private final int code;
    private final String message;
    private final int httpStatus;

    /**
     * 构造社交错误码。
     *
     * @param code       业务状态码
     * @param message    错误提示信息
     * @param httpStatus 对应的 HTTP 响应状态码
     */
    SocialErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public int code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
