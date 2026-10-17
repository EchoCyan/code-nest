package com.echocyan.codenest.interaction.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.api.ArticleErrorCode;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.result.CursorResult;
import com.echocyan.codenest.interaction.entity.Favorite;
import com.echocyan.codenest.interaction.vo.FavoriteVO;

import java.util.Collection;
import java.util.Set;

/**
 * 文章收藏服务接口。
 *
 * <p>收藏与取消收藏均保证幂等；仅当实际插入或删除数据行时才增减文章收藏数指标。
 */
public interface FavoriteService extends IService<Favorite> {

    /**
     * 收藏指定文章。
     *
     * @param userId    收藏用户 ID
     * @param articleId 目标文章 ID
     * @throws BizException 若文章不存在、已删除或非已发布状态（{@link ArticleErrorCode#ARTICLE_NOT_FOUND}）
     */
    void favorite(long userId, long articleId);

    /**
     * 取消对文章的收藏。
     *
     * <p>不校验文章状态，文章删除后仍可取消；未收藏时静默忽略。
     *
     * @param userId    取消收藏的用户 ID
     * @param articleId 目标文章 ID
     */
    void unfavorite(long userId, long articleId);

    /**
     * 分页查询当前用户的收藏文章列表。
     *
     * <p>按收藏时间倒序排列，以收藏记录 ID 为游标。已删除文章自动过滤，故单页可能少于 size 条。
     *
     * @param userId 目标用户 ID
     * @param cursor 游标（上一页末尾记录的收藏 ID，首页传 null）
     * @param size   单页查询大小
     * @return 收藏游标分页结果
     */
    CursorResult<FavoriteVO> listMine(long userId, Long cursor, int size);

    /**
     * 批量查询用户在给定文章集合中已收藏的文章 ID。
     *
     * @param userId     目标用户 ID
     * @param articleIds 待检查的文章 ID 集合
     * @return 该用户已收藏的文章 ID 集合
     */
    Set<Long> listFavoritedArticleIds(long userId, Collection<Long> articleIds);
}
