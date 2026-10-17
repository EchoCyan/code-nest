package com.echocyan.codenest.article.api;

import com.echocyan.codenest.common.exception.ErrorCode;

/**
 * 文章与评论业务错误码枚举。
 * <p>
 * 错误码范围统一分配为 {@code 20001 ~ 29999}。
 */
public enum ArticleErrorCode implements ErrorCode {

    /**
     * 指定文章不存在或已被软删除。
     */
    ARTICLE_NOT_FOUND(20001, "文章不存在", 404),

    /**
     * 乐观锁版本冲突。
     */
    VERSION_CONFLICT(20002, "文章已在别处修改，请刷新后重试", 409),

    /**
     * 分类不存在。
     */
    CATEGORY_NOT_FOUND(20003, "分类不存在", 400),

    /**
     * 标签不存在。
     */
    TAG_NOT_FOUND(20004, "标签不存在", 400),

    /**
     * 评论不存在或已被软删除。
     */
    COMMENT_NOT_FOUND(20005, "评论不存在", 404),

    /**
     * 被回复用户未参与讨论。
     */
    REPLY_TO_USER_INVALID(20006, "只能回复该评论下参与讨论的用户", 400);

    private final int code;
    private final String message;
    private final int httpStatus;

    ArticleErrorCode(int code, String message, int httpStatus) {
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
