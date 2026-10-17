package com.echocyan.codenest.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.article.entity.Category;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章分类字典表数据访问层接口。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
