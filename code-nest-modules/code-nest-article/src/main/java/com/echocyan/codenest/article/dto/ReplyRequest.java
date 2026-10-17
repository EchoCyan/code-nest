package com.echocyan.codenest.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发表楼中楼回复请求 DTO。
 *
 * @param content       回复文本内容，非空且长度不超过 1,000 字符
 * @param replyToUserId 被回复目标用户 ID；若显式指定，该用户须为当前讨论树（根评论或其子回复）的参与者；未指定时由服务端推导
 */
public record ReplyRequest(@NotBlank @Size(max = 1000) String content, Long replyToUserId) {
}
