package com.echocyan.codenest.interaction.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.api.ArticleErrorCode;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.interaction.entity.ArticleLike;

import java.util.Collection;
import java.util.Set;

/**
 * 文章点赞服务接口。
 *
 * <p>点赞与取消点赞均保证幂等；仅当实际插入或删除数据行时才增减文章点赞数与作者获赞数，
 * 并在点赞成功时发出 {@link com.echocyan.codenest.interaction.api.event.LikeCreatedEvent}。
 */
public interface ArticleLikeService extends IService<ArticleLike> {

    /**
     * 对文章执行点赞。
     *
     * @param userId    点赞用户 ID
     * @param articleId 目标文章 ID
     * @throws BizException 若文章不存在、已删除或非已发布状态（{@link ArticleErrorCode#ARTICLE_NOT_FOUND}）
     */
    void like(long userId, long articleId);

    /**
     * 取消对文章的点赞。
     *
     * <p>不校验文章状态，文章删除后仍可取消；未点赞时静默忽略。
     *
     * @param userId    取消点赞的用户 ID
     * @param articleId 目标文章 ID
     */
    void unlike(long userId, long articleId);

    /**
     * 批量查询用户在给定文章集合中已点赞的文章 ID。
     *
     * @param userId     目标用户 ID
     * @param articleIds 待检查的文章 ID 集合
     * @return 该用户已点赞的文章 ID 集合
     */
    Set<Long> listLikedArticleIds(long userId, Collection<Long> articleIds);
}
