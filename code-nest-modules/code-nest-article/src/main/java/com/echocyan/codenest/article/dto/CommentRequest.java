package com.echocyan.codenest.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发表一级评论请求 DTO。
 *
 * @param content 评论文本内容，非空且长度不超过 1,000 字符
 */
public record CommentRequest(@NotBlank @Size(max = 1000) String content) {
}
