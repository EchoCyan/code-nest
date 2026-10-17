package com.echocyan.codenest.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户登录请求参数。
 *
 * @param username 用户名
 * @param password 登录密码
 */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}

