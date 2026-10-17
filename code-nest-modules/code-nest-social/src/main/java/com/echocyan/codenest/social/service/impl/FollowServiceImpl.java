package com.echocyan.codenest.social.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.counter.api.CounterApi;
import com.echocyan.codenest.counter.api.CounterMetric;
import com.echocyan.codenest.counter.api.CounterSource;
import com.echocyan.codenest.counter.api.IdCount;
import com.echocyan.codenest.framework.mq.DomainEventPublisher;
import com.echocyan.codenest.social.SocialErrorCode;
import com.echocyan.codenest.social.api.event.FollowCreatedEvent;
import com.echocyan.codenest.social.api.event.FollowDeletedEvent;
import com.echocyan.codenest.social.entity.Follow;
import com.echocyan.codenest.social.mapper.FollowMapper;
import com.echocyan.codenest.social.service.FollowService;
import com.echocyan.codenest.social.service.FollowerGraph;
import com.echocyan.codenest.social.vo.FollowUserVO;
import com.echocyan.codenest.user.api.UserApi;
import com.echocyan.codenest.user.api.UserBrief;
import com.echocyan.codenest.user.api.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户关注业务服务实现类。
 * <p>
 * 实现 {@link FollowService} 关注业务、{@link FollowerGraph} 图谱拓扑查询以及 {@link CounterSource} 计数对账数据源。
 * 针对并发关注请求通过唯一索引兜底实现幂等入库，保证只有物理数据变更时才增减计数并触发领域事件。
 */
@Service
@RequiredArgsConstructor
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService, FollowerGraph,
        CounterSource {

    private final UserApi userApi;
    private final CounterApi counterApi;
    private final DomainEventPublisher eventPublisher;

    @Override
    @Transactional
    public void follow(long followerId, long authorId) {
        requireOtherUser(followerId, authorId);
        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setAuthorId(authorId);
        try {
            save(follow);
        } catch (DuplicateKeyException e) {
            // 已存在关注关系（含并发重复请求），静默忽略保持幂等
            return;
        }
        countFollow(followerId, authorId, 1);
        eventPublisher.publish(new FollowCreatedEvent(followerId, authorId));
    }

    @Override
    @Transactional
    public void unfollow(long followerId, long authorId) {
        requireOtherUser(followerId, authorId);
        boolean removed = lambdaUpdate()
                .eq(Follow::getFollowerId, followerId)
                .eq(Follow::getAuthorId, authorId)
                .remove();
        if (removed) {
            countFollow(followerId, authorId, -1);
            eventPublisher.publish(new FollowDeletedEvent(followerId, authorId));
        }
    }

    @Override
    public CursorResult<FollowUserVO> listFollowers(long authorId, Long cursor, int size) {
        return toCursorResult(lambdaQuery()
                .eq(Follow::getAuthorId, authorId)
                .lt(cursor != null, Follow::getId, cursor)
                .orderByDesc(Follow::getId)
                .last("LIMIT " + (size + 1))
                .list(), size, Follow::getFollowerId);
    }

    @Override
    public CursorResult<FollowUserVO> listFollowings(long followerId, Long cursor, int size) {
        return toCursorResult(lambdaQuery()
                .eq(Follow::getFollowerId, followerId)
                .lt(cursor != null, Follow::getId, cursor)
                .orderByDesc(Follow::getId)
                .last("LIMIT " + (size + 1))
                .list(), size, Follow::getAuthorId);
    }

    @Override
    public List<Long> listAllFollowedAuthorIds(long followerId) {
        return lambdaQuery()
                .select(Follow::getAuthorId)
                .eq(Follow::getFollowerId, followerId)
                .list().stream()
                .map(Follow::getAuthorId)
                .toList();
    }

    @Override
    public List<Long> listFollowerIds(long authorId, Long afterFollowerId, int limit) {
        return lambdaQuery()
                .select(Follow::getFollowerId)
                .eq(Follow::getAuthorId, authorId)
                .gt(afterFollowerId != null, Follow::getFollowerId, afterFollowerId)
                .orderByAsc(Follow::getFollowerId)
                .last("LIMIT " + limit)
                .list().stream()
                .map(Follow::getFollowerId)
                .toList();
    }

    @Override
    public Set<Long> listFollowedAuthorIds(long followerId, Collection<Long> authorIds) {
        if (authorIds.isEmpty()) {
            return Set.of();
        }
        return lambdaQuery()
                .select(Follow::getAuthorId)
                .eq(Follow::getFollowerId, followerId)
                .in(Follow::getAuthorId, authorIds)
                .list().stream()
                .map(Follow::getAuthorId)
                .collect(Collectors.toSet());
    }

    @Override
    public long countFollowers(long authorId) {
        return lambdaQuery().eq(Follow::getAuthorId, authorId).count();
    }

    @Override
    public List<IdCount> countFollowersAfter(long afterAuthorId, int limit) {
        return baseMapper.countByAuthor(afterAuthorId, limit);
    }

    @Override
    public Set<CounterMetric> metrics() {
        return Set.of(CounterMetric.USER_FOLLOWER, CounterMetric.USER_FOLLOWING);
    }

    @Override
    public List<IdCount> countAfter(CounterMetric metric, long afterId, int limit) {
        return switch (metric) {
            case USER_FOLLOWER -> countFollowersAfter(afterId, limit);
            case USER_FOLLOWING -> baseMapper.countByFollower(afterId, limit);
            default -> throw new IllegalArgumentException("不负责的计数指标: " + metric);
        };
    }

    /**
     * 将超额拉取（Overfetched）的关注数据转换为游标分页结果，并批量补全目标用户简要信息。
     *
     * @param fetched      拉取到的关注记录（多查 1 条用于下页探测）
     * @param size         期望分页大小
     * @param listedUserId 从关注记录中提取待展示方用户 ID 的映射函数
     * @return 包含用户视图对象及下页游标的分页结果
     */
    private CursorResult<FollowUserVO> toCursorResult(List<Follow> fetched, int size,
                                                      Function<Follow, Long> listedUserId) {
        CursorResult<Follow> page = CursorResult.ofOverfetched(fetched, size, Follow::getId);
        if (page.list().isEmpty()) {
            return CursorResult.empty();
        }
        Map<Long, UserBrief> users = userApi.getBriefs(page.list().stream().map(listedUserId).toList());
        return page.map(follow -> new FollowUserVO(users.get(listedUserId.apply(follow)), follow.getCreatedAt()));
    }

    /**
     * 校验关注操作的目标用户（禁止关注自身，且目标用户必须真实存在）。
     *
     * @param followerId 关注人用户 ID
     * @param authorId   被关注作者用户 ID
     */
    private void requireOtherUser(long followerId, long authorId) {
        if (followerId == authorId) {
            throw new BizException(SocialErrorCode.CANNOT_FOLLOW_SELF);
        }
        if (!userApi.exists(authorId)) {
            throw new BizException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    /**
     * 同步上报关注变更产生的计数更新（作者粉丝数与当前用户关注数）。
     *
     * @param followerId 关注人用户 ID
     * @param authorId   被关注作者用户 ID
     * @param delta      变更增量（+1 或 -1）
     */
    private void countFollow(long followerId, long authorId, long delta) {
        counterApi.increment(CounterMetric.USER_FOLLOWER, authorId, delta);
        counterApi.increment(CounterMetric.USER_FOLLOWING, followerId, delta);
    }
}
