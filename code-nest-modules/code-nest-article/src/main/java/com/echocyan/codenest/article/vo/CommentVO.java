package com.echocyan.codenest.article.vo;

import com.echocyan.codenest.user.api.UserBrief;

import java.time.LocalDateTime;

/**
 * 一级根评论视图对象。
 * <p>
 * 仅包含评论本体与回复统计数，楼中楼回复通过独立接口按需拉取。
 *
 * @param id         评论 ID
 * @param content    评论内容；若已被软删除但名下仍有子回复，脱敏展示为预置文案（如“该评论已删除”）
 * @param deleted    软删除标识；为 {@code true} 时表明该评论仅作占位展示以维持讨论树完整性
 * @param author     发表者用户信息；评论被软删除或用户已注销时为 {@code null}
 * @param replyCount 当前根评论下的二级回复总数
 * @param createdAt  评论发表时间
 */
public record CommentVO(Long id,
                        String content,
                        boolean deleted,
                        UserBrief author,
                        long replyCount,
                        LocalDateTime createdAt) {
}
