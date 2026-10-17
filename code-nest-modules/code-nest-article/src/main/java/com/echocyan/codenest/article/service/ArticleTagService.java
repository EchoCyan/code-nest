package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.entity.ArticleTag;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 文章与标签关联服务接口。
 */
public interface ArticleTagService extends IService<ArticleTag> {

    /**
     * 批量查询文章关联的标签 ID 列表，按标签 ID 升序排列。
     *
     * @param articleIds 文章 ID 集合
     * @return 文章 ID 与标签 ID 列表的映射；未绑定标签的文章不包含在结果中
     */
    Map<Long, List<Long>> listTagIds(Collection<Long> articleIds);

    /**
     * 全量替换指定文章的标签关联关系。
     *
     * @param articleId 文章 ID
     * @param tagIds    新标签 ID 列表
     */
    void replaceTags(long articleId, List<Long> tagIds);
}
