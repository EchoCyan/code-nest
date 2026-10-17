package com.echocyan.codenest.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.counter.api.IdCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章 Mapper 接口。
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 批量统计各作者已发布的有效文章数。
     *
     * @param afterId 作者 ID 游标
     * @param limit   单批记录数
     * @return 统计结果列表
     */
    @Select("""
            SELECT author_id, COUNT(*) FROM article
            WHERE author_id > #{afterId} AND status = 1 AND deleted = 0
            GROUP BY author_id ORDER BY author_id LIMIT #{limit}
            """)
    List<IdCount> countPublishedByAuthor(long afterId, int limit);

    /**
     * 根据主键查询文章实体（包含已删除记录）。
     *
     * @param id 文章 ID
     * @return 文章实体；若不存在则返回 {@code null}
     */
    @Select("SELECT * FROM article WHERE id = #{id}")
    Article selectByIdIncludingDeleted(long id);

    /**
     * 游标查询指定时间后更新的文章（包含已删除记录），按 ID 升序。
     *
     * @param since   更新时间起始下限
     * @param afterId 文章 ID 游标
     * @param limit   单批记录数
     * @return 变更文章列表
     */
    @Select("""
            SELECT * FROM article
            WHERE updated_at >= #{since} AND id > #{afterId}
            ORDER BY id LIMIT #{limit}
            """)
    List<Article> selectUpdatedSinceIncludingDeleted(LocalDateTime since, long afterId, int limit);
}
