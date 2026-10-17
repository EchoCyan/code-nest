package com.echocyan.codenest.social.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.social.SocialErrorCode;
import com.echocyan.codenest.social.entity.Follow;
import com.echocyan.codenest.social.vo.FollowUserVO;
import com.echocyan.codenest.user.api.UserErrorCode;

import java.util.Collection;
import java.util.Set;

/**
 * 用户关注业务服务接口。
 * <p>
 * 提供关注、取关、粉丝与关注列表游标查询及关注状态批量判定能力。
 * 关注与取关操作均具备幂等性，仅当底层数据物理发生增删时才同步变更粉丝/关注计数并发布领域事件。
 */
public interface FollowService extends IService<Follow> {

    /**
     * 关注指定用户。
     *
     * @param followerId 发起关注的用户 ID
     * @param authorId   被关注的目标作者用户 ID
     * @throws BizException 若关注目标为自己（{@link SocialErrorCode#CANNOT_FOLLOW_SELF}）或目标用户不存在（
     *                      {@link UserErrorCode#USER_NOT_FOUND}）
     */
    void follow(long followerId, long authorId);

    /**
     * 取消关注指定用户。
     *
     * @param followerId 发起取关的用户 ID
     * @param authorId   被取关的目标作者用户 ID
     * @throws BizException 若取关目标为自己（{@link SocialErrorCode#CANNOT_FOLLOW_SELF}）或目标用户不存在（
     *                      {@link UserErrorCode#USER_NOT_FOUND}）
     */
    void unfollow(long followerId, long authorId);

    /**
     * 游标分页查询指定用户的粉丝列表（按关注时间倒序排列）。
     *
     * @param authorId 目标作者用户 ID
     * @param cursor   上一页最后一条记录的关注 ID（首次查询传入 {@code null}）
     * @param size     每页期望获取数量
     * @return 包含粉丝用户资料及下一页游标的分页结果
     */
    CursorResult<FollowUserVO> listFollowers(long authorId, Long cursor, int size);

    /**
     * 游标分页查询指定用户关注的人列表（按关注时间倒序排列）。
     *
     * @param followerId 目标关注人用户 ID
     * @param cursor     上一页最后一条记录的关注 ID（首次查询传入 {@code null}）
     * @param size       每页期望获取数量
     * @return 包含关注人用户资料及下一页游标的分页结果
     */
    CursorResult<FollowUserVO> listFollowings(long followerId, Long cursor, int size);

    /**
     * 批量过滤给定作者集合中已被指定用户关注的作者 ID。
     * <p>
     * 命中 {@code (follower_id, author_id)} 唯一索引快速返回。
     *
     * @param followerId 关注人用户 ID
     * @param authorIds  待判定的作者 ID 集合
     * @return 已被关注的作者 ID 集合
     */
    Set<Long> listFollowedAuthorIds(long followerId, Collection<Long> authorIds);
}
