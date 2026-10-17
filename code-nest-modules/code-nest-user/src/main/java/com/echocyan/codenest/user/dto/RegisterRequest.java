package com.echocyan.codenest.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 用户注册请求参数。
 * <p>
 * 密码限定为 8–32 位可打印 ASCII 字符，确保不会超出 BCrypt 算法 72 字节的最大处理长度。
 *
 * @param username 用户名（4–20 位字母、数字或下划线）
 * @param password 登录密码（8–32 位英文字母、数字或符号）
 */
public record RegisterRequest(@NotNull @Pattern(regexp = "^[A-Za-z0-9_]{4,20}$", message = "用户名需为 4–20 位字母、数字或下划线") String username,
                              @NotNull @Pattern(regexp = "^[\\x20-\\x7E]{8,32}$", message = "密码需为 8–32 位英文字母、数字或符号") String password) {
}
