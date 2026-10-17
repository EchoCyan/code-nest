package com.echocyan.codenest.article.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建或编辑文章请求 DTO。
 * <p>
 * 更新操作采用全量替换策略；若未显式指定摘要，自动截取 Markdown 正文起始文本生成摘要。
 *
 * @param title      文章标题，非空且长度不超过 100 字符
 * @param content    Markdown 格式文章正文，非空且长度不超过 100,000 字符
 * @param summary    自定义纯文本摘要，长度不超过 200 字符；为空时由服务端自动截取正文前 100 字符
 * @param coverUrl   封面图 URL，须为合法的 HTTP/HTTPS 协议地址，长度不超过 512 字符；传 {@code null} 表示清空封面
 * @param categoryId 所属分类 ID，必填
 * @param tagIds     关联标签 ID 列表，至多关联 5 个标签，集合内部自动去重
 */
public record ArticleRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 100_000) String content,
        @Size(max = 200) String summary,
        @Size(max = 512) @Pattern(regexp = "^https?://\\S+$", message = "封面需为 http(s) 地址") String coverUrl,
        @NotNull Long categoryId,
        @Size(max = 5) List<@NotNull Long> tagIds) {
}
