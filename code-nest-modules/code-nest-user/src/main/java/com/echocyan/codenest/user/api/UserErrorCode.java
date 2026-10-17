package com.echocyan.codenest.user.api;

import com.echocyan.codenest.common.exception.ErrorCode;

/**
 * 用户模块业务错误码（号段：10xxx）。
 */
public enum UserErrorCode implements ErrorCode {

    /**
     * 用户名已被占用（HTTP 409 Conflict）。
     */
    USERNAME_TAKEN(10001, "用户名已被占用", 409),

    /**
     * 用户名或密码错误（HTTP 401 Unauthorized）。
     * <p>
     * 认证失败时统一返回，防范恶意枚举探测已注册用户名。
     */
    BAD_CREDENTIALS(10002, "用户名或密码错误", 401),

    /**
     * 用户不存在（HTTP 404 Not Found）。
     */
    USER_NOT_FOUND(10003, "用户不存在", 404);

    private final int code;
    private final String message;
    private final int httpStatus;

    UserErrorCode(int code, String message, int httpStatus) {
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
