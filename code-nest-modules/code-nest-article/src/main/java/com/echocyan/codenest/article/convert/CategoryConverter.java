package com.echocyan.codenest.article.convert;

import com.echocyan.codenest.article.api.CategoryBrief;
import com.echocyan.codenest.article.entity.Category;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 分类对象 MapStruct 映射器。
 */
@Mapper
public interface CategoryConverter {

    /**
     * 将分类实体转换为简要投影。
     */
    CategoryBrief toBrief(Category category);

    /**
     * 将分类实体列表批量转换为简要投影列表。
     */
    List<CategoryBrief> toBriefs(List<Category> categories);
}
