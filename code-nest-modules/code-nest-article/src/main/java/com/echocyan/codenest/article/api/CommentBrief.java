package com.echocyan.codenest.article.api;

/**
 * 评论与回复轻量级摘要投影。
 * <p>
 * 专供通知消息、动态展示等外围业务使用。
 *
 * @param id        评论或回复 ID
 * @param articleId 所属文章 ID
 * @param userId    发表者用户 ID
 * @param rootId    根评论 ID；若自身为一级评论则为 {@code 0}
 * @param summary   截取的正文纯文本摘要，长度上限为 {@value #SUMMARY_LENGTH} 个字符
 */
public record CommentBrief(Long id, Long articleId, Long userId, Long rootId, String summary) {

    /**
     * 摘要截取的最大字符数。
     */
    public static final int SUMMARY_LENGTH = 100;
}
