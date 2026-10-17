package com.echocyan.codenest.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.counter.api.IdCount;
import com.echocyan.codenest.social.entity.Follow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户关注关系数据访问层映射器。
 */
@Mapper
public interface FollowMapper extends BaseMapper<Follow> {

    /**
     * 分页统计作者粉丝数（按作者 ID 升序遍历）。
     * <p>
     * 用于对账及 Feed 大 V 缓存全量重建，仅统计作者 ID 大于指定游标且至少有一名粉丝的数据。
     *
     * @param afterId 上一页最后处理的作者用户 ID
     * @param limit   每页获取的最大作者数量
     * @return 包含作者 ID 与对应粉丝计数的列表
     */
    @Select("""
            SELECT author_id, COUNT(*) FROM follow
            WHERE author_id > #{afterId}
            GROUP BY author_id ORDER BY author_id LIMIT #{limit}
            """)
    List<IdCount> countByAuthor(long afterId, int limit);

    /**
     * 分页统计用户关注数（按关注人 ID 升序遍历）。
     * <p>
     * 用于关注数指标对账，仅统计用户 ID 大于指定游标且至少关注了一位作者的数据。
     *
     * @param afterId 上一页最后处理的关注人用户 ID
     * @param limit   每页获取的最大用户数量
     * @return 包含关注人 ID 与对应关注计数的列表
     */
    @Select("""
            SELECT follower_id, COUNT(*) FROM follow
            WHERE follower_id > #{afterId}
            GROUP BY follower_id ORDER BY follower_id LIMIT #{limit}
            """)
    List<IdCount> countByFollower(long afterId, int limit);
}
