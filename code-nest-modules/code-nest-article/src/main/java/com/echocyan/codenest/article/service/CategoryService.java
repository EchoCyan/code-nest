package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.entity.Category;

import java.util.List;

/**
 * 文章分类字典服务接口。
 */
public interface CategoryService extends IService<Category> {

    /**
     * 查询全部分类实体，按预置排序权重与分类 ID 升序排列。
     *
     * @return 有序分类实体列表
     */
    List<Category> listInOrder();
}
