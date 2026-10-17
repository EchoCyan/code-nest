package com.echocyan.codenest.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.counter.api.IdCount;
import com.echocyan.codenest.interaction.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文章收藏数据访问层接口。
 */
@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {

    /**
     * 分页统计指定起始 ID 之后的文章收藏数。
     *
     * @param afterId 起始文章 ID（不含），仅统计 ID 大于该值的文章
     * @param limit   单页统计数量上限
     * @return 按文章 ID 升序排列的精确收藏数统计列表
     */
    @Select("""
            SELECT article_id, COUNT(*) FROM favorite
            WHERE article_id > #{afterId}
            GROUP BY article_id ORDER BY article_id LIMIT #{limit}
            """)
    List<IdCount> countByArticle(long afterId, int limit);
}
