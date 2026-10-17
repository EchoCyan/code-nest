package com.echocyan.codenest.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.counter.api.IdCount;
import com.echocyan.codenest.interaction.entity.ArticleLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文章点赞数据访问层接口。
 */
@Mapper
public interface ArticleLikeMapper extends BaseMapper<ArticleLike> {

    /**
     * 分页统计指定起始 ID 之后的文章点赞数。
     *
     * @param afterId 起始文章 ID（不含），仅统计 ID 大于该值的文章
     * @param limit   单页统计数量上限
     * @return 按文章 ID 升序排列的精确点赞数统计列表
     */
    @Select("""
            SELECT article_id, COUNT(*) FROM article_like
            WHERE article_id > #{afterId}
            GROUP BY article_id ORDER BY article_id LIMIT #{limit}
            """)
    List<IdCount> countByArticle(long afterId, int limit);

    /**
     * 分页统计指定起始 ID 之后的作者累计获赞数。
     *
     * @param afterId 起始作者 ID（不含），仅统计 ID 大于该值的作者
     * @param limit   单页统计数量上限
     * @return 按作者 ID 升序排列的精确获赞数统计列表
     */
    @Select("""
            SELECT author_id, COUNT(*) FROM article_like
            WHERE author_id > #{afterId}
            GROUP BY author_id ORDER BY author_id LIMIT #{limit}
            """)
    List<IdCount> countByAuthor(long afterId, int limit);
}
