package com.echocyan.codenest.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 用户个人资料修改请求参数。
 * <p>
 * 采用整体覆盖更新语义：未传递的头像、个人简介将被清空；用户名属于不可修改字段。
 *
 * @param nickname  用户昵称（必填，最多 20 字符）
 * @param avatarUrl 头像链接（可选，需为 http 或 https 地址，最多 512 字符）
 * @param bio       个人简介（可选，最多 200 字符）
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 20) String nickname,
        @Size(max = 512) @Pattern(regexp = "^https?://\\S+$", message = "头像需为 http(s) 地址") String avatarUrl,
        @Size(max = 200) String bio) {
}
