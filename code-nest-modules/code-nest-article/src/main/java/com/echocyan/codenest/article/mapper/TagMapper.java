package com.echocyan.codenest.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.article.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章标签字典表数据访问层接口。
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}
