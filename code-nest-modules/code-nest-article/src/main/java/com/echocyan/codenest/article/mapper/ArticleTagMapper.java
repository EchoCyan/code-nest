package com.echocyan.codenest.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.article.entity.ArticleTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章与标签关联表数据访问层接口。
 */
@Mapper
public interface ArticleTagMapper extends BaseMapper<ArticleTag> {
}
