package com.echocyan.codenest.interaction.vo;

/**
 * 当前用户对文章的互动状态视图对象。
 *
 * @param liked     是否已点赞
 * @param favorited 是否已收藏
 */
public record ArticleInteractionStateVO(boolean liked, boolean favorited) {
}
