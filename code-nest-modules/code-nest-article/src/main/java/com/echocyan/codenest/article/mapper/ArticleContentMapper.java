package com.echocyan.codenest.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.article.entity.ArticleContent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章正文扩展表数据访问层接口。
 */
@Mapper
public interface ArticleContentMapper extends BaseMapper<ArticleContent> {
}
