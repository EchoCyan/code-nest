package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.api.ArticleErrorCode;
import com.echocyan.codenest.article.dto.ArticleRequest;
import com.echocyan.codenest.article.entity.Article;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.exception.CommonErrorCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 文章写服务接口，处理草稿创建、更新、发布与软删除。
 * <p>
 * 查询服务由 {@link ArticleReader} 提供。
 */
public interface ArticleService extends IService<Article> {

    /**
     * 新建文章草稿。
     *
     * @param authorId 作者用户 ID
     * @param request  文章创建请求
     * @return 持久化后的文章实体
     * @throws BizException 若分类或标签不存在
     */
    Article create(long authorId, ArticleRequest request);

    /**
     * 全量更新文章内容与元数据。
     *
     * @param id      文章 ID
     * @param userId  当前操作人用户 ID
     * @param version 乐观锁版本号
     * @param request 文章更新请求
     * @return 更新后的文章实体
     * @throws BizException 若文章不存在抛出 {@link ArticleErrorCode#ARTICLE_NOT_FOUND}；
     *                      若非作者本人操作抛出 {@link CommonErrorCode#FORBIDDEN}；
     *                      若版本号冲突抛出 {@link ArticleErrorCode#VERSION_CONFLICT}；
     *                      若分类或标签不存在抛出对应异常
     */
    Article update(long id, long userId, int version, ArticleRequest request);

    /**
     * 发布文章草稿。已发布文章重复发布幂等返回。
     *
     * @param id     文章 ID
     * @param userId 当前操作人用户 ID
     * @return 发布后的文章实体
     * @throws BizException 若文章不存在抛出 {@link ArticleErrorCode#ARTICLE_NOT_FOUND}；
     *                      若非作者本人操作抛出 {@link CommonErrorCode#FORBIDDEN}；
     *                      若版本并发冲突抛出 {@link ArticleErrorCode#VERSION_CONFLICT}
     */
    Article publish(long id, long userId);

    /**
     * 软删除指定文章。
     *
     * @param id     文章 ID
     * @param userId 当前操作人用户 ID
     * @throws BizException 若文章不存在抛出 {@link ArticleErrorCode#ARTICLE_NOT_FOUND}；
     *                      若非作者本人操作抛出 {@link CommonErrorCode#FORBIDDEN}；
     *                      若版本并发冲突抛出 {@link ArticleErrorCode#VERSION_CONFLICT}
     */
    void delete(long id, long userId);

    /**
     * 直查数据库中的已发布文章，绕过应用层缓存。
     *
     * @param id 文章 ID
     * @return 文章实体；若不存在、已软删除或处于草稿态则返回 {@link Optional#empty()}
     */
    Optional<Article> findPublished(long id);

    /**
     * 根据主键查询文章实体（包含已被软删除的记录）。
     *
     * @param id 文章 ID
     * @return 文章实体；若从未创建则返回 {@code null}
     */
    Article getIncludingDeleted(long id);

    /**
     * 基于游标批量查询指定时间戳之后发生更新的文章（包含草稿与已软删除记录），按 ID 升序排列。
     *
     * @param since   更新时间起始下限
     * @param afterId 游标偏移量，为 {@code null} 时从头遍历
     * @param limit   单批返回的最大记录数
     * @return 变更文章实体列表
     */
    List<Article> listUpdatedSinceIncludingDeleted(LocalDateTime since, Long afterId, int limit);
}
