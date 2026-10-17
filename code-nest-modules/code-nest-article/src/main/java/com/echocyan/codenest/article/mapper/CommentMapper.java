package com.echocyan.codenest.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.article.entity.Comment;
import com.echocyan.codenest.counter.api.IdCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 评论与回复 Mapper 接口。
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * 根据主键查询评论或回复实体（包含已被软删除的记录）。
     *
     * @param id 评论或回复 ID
     * @return 评论或回复实体；若不存在则返回 {@code null}
     */
    @Select("SELECT * FROM comment WHERE id = #{id}")
    Comment selectByIdIncludingDeleted(long id);

    /**
     * 查询指定文章下可见的一级评论列表（未删除，或已删除但含有未删除子回复），按 ID 降序排列。
     *
     * @param articleId 所属文章 ID
     * @param cursor    游标偏移量，仅返回 ID 小于该值的记录
     * @param limit     单批返回的最大记录数
     * @return 一级评论列表
     */
    @Select("""
            SELECT * FROM comment c
            WHERE c.article_id = #{articleId} AND c.root_id = 0 AND c.id < #{cursor}
              AND (c.deleted = 0 OR EXISTS (
                  SELECT 1 FROM comment r
                  WHERE r.article_id = c.article_id AND r.root_id = c.id AND r.deleted = 0))
            ORDER BY c.id DESC
            LIMIT #{limit}
            """)
    List<Comment> selectVisibleComments(long articleId, long cursor, int limit);

    /**
     * 基于游标批量统计各文章未删除的评论与回复总数，按文章 ID 升序排列。
     *
     * @param afterId 文章 ID 游标，仅统计大于该值的记录
     * @param limit   单批返回的最大分组数
     * @return 文章 ID 与有效评论回复总数的统计结果列表
     */
    @Select("""
            SELECT article_id, COUNT(*) FROM comment
            WHERE article_id > #{afterId} AND deleted = 0
            GROUP BY article_id ORDER BY article_id LIMIT #{limit}
            """)
    List<IdCount> countByArticle(long afterId, int limit);

    /**
     * 基于游标批量统计各根评论未删除的有效回复总数，按根评论 ID 升序排列。
     * <p>
     * 专供计数模块执行 {@code COMMENT_REPLY} 指标的数据校准。
     *
     * @param afterId 根评论 ID 游标，仅统计大于该值的记录
     * @param limit   单批返回的最大分组数
     * @return 根评论 ID 与有效回复总数的统计结果列表
     */
    @Select("""
            SELECT root_id, COUNT(*) FROM comment
            WHERE root_id > #{afterId} AND deleted = 0
            GROUP BY root_id ORDER BY root_id LIMIT #{limit}
            """)
    List<IdCount> countByRoot(long afterId, int limit);
}
