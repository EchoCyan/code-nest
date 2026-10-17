package com.echocyan.codenest.framework.web;

import cn.dev33.satoken.exception.NotLoginException;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.exception.CommonErrorCode;
import com.echocyan.codenest.common.exception.ErrorCode;
import com.echocyan.codenest.common.result.Result;
import com.echocyan.codenest.framework.ratelimit.RateLimitedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * REST API 全局异常处理器。
 *
 * <p>统一捕获 Controller 层抛出的各类异常（业务异常、参数校验、未登录、限流、框架及兜底异常），
 * 转换为统一响应体 {@link Result} 并赋予语义化的 HTTP 状态码。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理未知非预期异常的统一内部兜底方法。
     *
     * @param e 未知非预期异常
     * @return 状态码为 500 的标准错误响应实体
     */
    private static ResponseEntity<Result<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return respond(CommonErrorCode.INTERNAL_ERROR);
    }

    /**
     * 根据错误码构建响应实体，使用错误码自带的默认错误信息。
     *
     * @param errorCode 错误码枚举
     * @return 包装标准错误结果的响应实体
     */
    private static ResponseEntity<Result<Void>> respond(ErrorCode errorCode) {
        return respond(errorCode, errorCode.message());
    }

    /**
     * 根据错误码和自定义错误信息构建响应实体。
     *
     * @param errorCode 错误码枚举
     * @param message   对外展示的具体错误提示信息
     * @return 包装标准错误结果的响应实体
     */
    private static ResponseEntity<Result<Void>> respond(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.httpStatus()).body(Result.fail(errorCode, message));
    }

    /**
     * 捕获显式声明的业务领域异常。
     *
     * @param e 业务异常实例
     * @return 携带对应业务错误码与 HTTP 状态码的响应实体
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        return respond(e.getErrorCode(), e.getMessage());
    }

    /**
     * 捕获滑动窗口请求限流异常。
     *
     * @param e 限流异常实例
     * @return 状态码为 429 且携带 {@code Retry-After} 响应头的错误响应实体
     */
    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<Result<Void>> handleRateLimited(RateLimitedException e) {
        return ResponseEntity.status(e.getErrorCode().httpStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(Result.fail(e.getErrorCode(), e.getMessage()));
    }

    /**
     * 捕获 Sa-Token 鉴权失败与登录凭证缺失异常。
     *
     * <p>未携带 Token、Token 格式不合法、已失效过期或缺少 {@code Bearer } 前缀，均统一收敛为 401。
     *
     * @param e 未登录异常实例
     * @return 状态码为 401 的标准错误响应实体
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException e) {
        return respond(CommonErrorCode.UNAUTHORIZED);
    }

    /**
     * 捕获 {@code @RequestBody} 请求体 JavaBean 校验失败异常。
     *
     * @param e 请求体验证异常
     * @return 状态码为 400 且包含第一个违规字段提示信息的响应实体
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleBodyValidation(MethodArgumentNotValidException e) {
        FieldError error = e.getBindingResult().getFieldError();
        return error == null ? respond(CommonErrorCode.BAD_REQUEST)
                : respond(CommonErrorCode.BAD_REQUEST, error.getField() + ": " + error.getDefaultMessage());
    }

    /**
     * 捕获 Controller 方法入参校验异常（Spring 6.1+ 机制）。
     *
     * @param e 方法参数校验异常
     * @return 状态码为 400（参数不合法）或 500（返回值校验异常）的响应实体
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleMethodValidation(HandlerMethodValidationException e) {
        if (e.isForReturnValue()) {
            // 返回值校验失败属于服务端内部逻辑实现缺陷，转换为 500 告警
            return handleUnexpected(e);
        }
        return e.getAllErrors().stream().findFirst()
                .map(MessageSourceResolvable::getDefaultMessage)
                .map(message -> respond(CommonErrorCode.BAD_REQUEST, message))
                .orElseGet(() -> respond(CommonErrorCode.BAD_REQUEST));
    }

    /**
     * 捕获 URL 路径参数与 Query 查询参数的 JSR-303 约束违规异常。
     *
     * @param e 约束违规异常
     * @return 状态码为 400 且包含具体违规说明的响应实体
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        return e.getConstraintViolations().stream().findFirst()
                .map(ConstraintViolation::getMessage)
                .map(message -> respond(CommonErrorCode.BAD_REQUEST, message))
                .orElseGet(() -> respond(CommonErrorCode.BAD_REQUEST));
    }

    /**
     * 捕获请求参数类型不匹配与 HTTP 报文反序列化不可读异常（如非法 JSON 格式）。
     *
     * @param e 参数格式或类型错误异常
     * @return 状态码为 400 的参数错误响应实体
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Result<Void>> handleBadRequest(Exception e) {
        return respond(CommonErrorCode.BAD_REQUEST);
    }

    /**
     * 捕获所有其他未显式处理的异常。
     *
     * <p>若属于 Spring MVC 原生客户端错误（如 404 Not Found、405 Method Not Allowed 等实现了 {@link ErrorResponse} 的异常），
     * 保持其原生 HTTP 状态码并返回对应的通用错误码；其余非受检系统错误统一记录日志并返回 500。
     *
     * @param e 抛出的未捕获异常
     * @return 格式化后的统一响应实体
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        if (e instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = errorResponse.getStatusCode();
            CommonErrorCode errorCode = status.value() == 404 ? CommonErrorCode.NOT_FOUND : CommonErrorCode.BAD_REQUEST;
            return ResponseEntity.status(status).body(Result.fail(errorCode, errorCode.message()));
        }
        return handleUnexpected(e);
    }
}
