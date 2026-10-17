package com.echocyan.codenest.article.convert;

import com.echocyan.codenest.article.api.*;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.article.vo.ArticleDetailVO;
import com.echocyan.codenest.article.vo.TagVO;
import com.echocyan.codenest.user.api.UserBrief;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 文章领域对象 MapStruct 映射器。
 * <p>
 * 负责在持久化实体、对外 API 投影 Record 以及表现层视图对象之间执行属性转换与组合。
 */
@Mapper
public interface ArticleConverter {

    /**
     * 将文章实体转换为状态与归属校验投影。
     */
    ArticleState toState(Article article);

    /**
     * 将文章实体转换为轻量级摘要投影。
     */
    ArticleBrief toBrief(Article article);

    /**
     * 将文章实体、正文文本与标签元数据装配为搜索同步快照。
     */
    @Mapping(target = "deleted", expression = "java(article.getDeleted() == 1)")
    ArticleSnapshot toSnapshot(Article article, String content, List<Long> tagIds, List<String> tagNames);

    /**
     * 聚合文章摘要、分类、作者与互动统计，生成文章列表复合展示项。
     * <p>
     * 显式指定 ID 映射源自 {@code brief.id}，避免多入参属性名称冲突。
     */
    @Mapping(target = "id", source = "brief.id")
    ArticleItem toItem(ArticleBrief brief, CategoryBrief category, UserBrief author, ArticleCounts counts);

    /**
     * 聚合文章元数据、正文内容、分类、标签集合、作者与互动统计，生成文章详情视图对象。
     */
    @Mapping(target = "id", source = "article.id")
    @Mapping(target = "content", source = "content")
    ArticleDetailVO toDetailVO(Article article, String content, CategoryBrief category, List<TagVO> tags,
                               UserBrief author, ArticleCounts counts);
}
