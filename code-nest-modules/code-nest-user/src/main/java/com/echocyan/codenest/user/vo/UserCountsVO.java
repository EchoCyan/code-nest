package com.echocyan.codenest.user.vo;

/**
 * 用户多维度统计指标视图对象。
 *
 * @param followerCount     粉丝总数
 * @param followingCount    关注总数
 * @param articleCount      已发布公开文章总数
 * @param likeReceivedCount 获得的点赞总数
 */
public record UserCountsVO(long followerCount, long followingCount, long articleCount, long likeReceivedCount) {
}

