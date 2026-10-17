package com.echocyan.codenest.article.vo;

import com.echocyan.codenest.user.api.UserBrief;

import java.time.LocalDateTime;

/**
 * 二级楼中楼回复视图对象。
 *
 * @param id        回复唯一 ID
 * @param rootId    所属一级根评论 ID
 * @param content   回复文本内容
 * @param author    回复者用户信息；若用户已注销则为 {@code null}
 * @param replyTo   被回复目标用户信息；若直接针对根评论回复则为 {@code null}
 * @param createdAt 回复发表时间
 */
public record ReplyVO(Long id,
                      Long rootId,
                      String content,
                      UserBrief author,
                      UserBrief replyTo,
                      LocalDateTime createdAt) {
}
