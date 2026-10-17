package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.entity.Tag;

import java.util.Collection;
import java.util.List;

/**
 * 文章标签字典服务接口。
 */
public interface TagService extends IService<Tag> {

    /**
     * 查询全部标签实体，按标签 ID 升序排列。
     *
     * @return 有序标签实体列表
     */
    List<Tag> listInOrder();

    /**
     * 根据 ID 集合批量查询标签实体，按标签 ID 升序排列。
     *
     * @param ids 标签 ID 集合；不存在的 ID 自动被忽略
     * @return 匹配的有序标签实体列表
     */
    List<Tag> listInOrder(Collection<Long> ids);
}
