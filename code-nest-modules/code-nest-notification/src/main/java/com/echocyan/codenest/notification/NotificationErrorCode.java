package com.echocyan.codenest.notification;

import com.echocyan.codenest.common.exception.ErrorCode;

/**
 * 通知模块业务错误码（号段：5xxxx）。
 */
public enum NotificationErrorCode implements ErrorCode {

    /**
     * 通知不存在或不属于当前用户。
     */
    NOTIFICATION_NOT_FOUND(50001, "通知不存在", 404);

    private final int code;
    private final String message;
    private final int httpStatus;

    /**
     * 构造通知错误码。
     *
     * @param code       业务状态码
     * @param message    错误提示信息
     * @param httpStatus 对应的 HTTP 响应状态码
     */
    NotificationErrorCode(int code, String message, int httpStatus) {
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
