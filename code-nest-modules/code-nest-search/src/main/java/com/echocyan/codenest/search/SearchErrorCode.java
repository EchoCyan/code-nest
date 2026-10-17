package com.echocyan.codenest.search;

import com.echocyan.codenest.common.exception.ErrorCode;

/**
 * 搜索模块业务错误码（号段：6xxxx）。
 */
public enum SearchErrorCode implements ErrorCode {

    /**
     * 翻页超出深度分页窗口上限（{@code from + size > 1000}）。
     */
    PAGE_TOO_DEEP(60001, "搜索结果最多翻到第 1000 条", 400);

    private final int code;
    private final String message;
    private final int httpStatus;

    SearchErrorCode(int code, String message, int httpStatus) {
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
