package com.echocyan.codenest.interaction.vo;

import com.echocyan.codenest.article.api.ArticleItem;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.time.LocalDateTime;

/**
 * 用户收藏记录视图对象。
 *
 * @param article     文章简要信息（序列化时展平字段）
 * @param favoritedAt 收藏操作时间
 */
public record FavoriteVO(@JsonUnwrapped ArticleItem article, LocalDateTime favoritedAt) {
}
