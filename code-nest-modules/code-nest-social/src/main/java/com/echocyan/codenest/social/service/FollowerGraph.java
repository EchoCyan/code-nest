package com.echocyan.codenest.social.service;

import com.echocyan.codenest.counter.api.IdCount;

import java.util.List;

/**
 * 关注关系图谱数据访问内部接口。
 * <p>
 * 提供 Feed 动态流系统及计数统计所需的基础关注拓扑查询，直接访问底层的关注关系表。
 */
public interface FollowerGraph {

    /**
     * 查询指定用户关注的全部作者 ID。
     * <p>
     * 仅扫描 {@code (follower_id, author_id)} 覆盖索引以提升性能。
     *
     * @param followerId 关注人用户 ID
     * @return 关注的作者 ID 集合
     */
    List<Long> listAllFollowedAuthorIds(long followerId);

    /**
     * 游标分页查询指定作者的粉丝 ID（按粉丝 ID 升序排列）。
     * <p>
     * 命中 {@code (author_id, follower_id)} 覆盖索引，主要用于发文推送到收件箱时的批量批次遍历。
     *
     * @param authorId        作者用户 ID
     * @param afterFollowerId 粉丝游标 ID（仅返回大于该 ID 的记录；首次查询传入 {@code null}）
     * @param limit           每批获取的最大粉丝数量
     * @return 粉丝用户 ID 列表
     */
    List<Long> listFollowerIds(long authorId, Long afterFollowerId, int limit);

    /**
     * 统计指定作者的实时粉丝总数。
     * <p>
     * 命中 {@code (author_id, follower_id)} 覆盖索引统计记录总行数。
     *
     * @param authorId 作者用户 ID
     * @return 当前粉丝总数
     */
    long countFollowers(long authorId);

    /**
     * 分页统计作者粉丝数（按作者 ID 升序排列）。
     * <p>
     * 仅统计作者 ID 大于指定游标且至少有一名粉丝的记录，用于大 V 缓存与计数系统重建。
     *
     * @param afterAuthorId 上一页最后处理的作者 ID
     * @param limit         每页获取的最大作者数量
     * @return 包含作者 ID 与对应粉丝计数的列表
     */
    List<IdCount> countFollowersAfter(long afterAuthorId, int limit);
}
