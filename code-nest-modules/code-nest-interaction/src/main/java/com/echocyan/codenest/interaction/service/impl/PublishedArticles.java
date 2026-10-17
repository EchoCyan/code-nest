package com.echocyan.codenest.interaction.service.impl;

import com.echocyan.codenest.article.api.ArticleApi;
import com.echocyan.codenest.article.api.ArticleErrorCode;
import com.echocyan.codenest.article.api.ArticleState;
import com.echocyan.codenest.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 已发布文章校验辅助组件。
 *
 * <p>用于在点赞、收藏等互动前置检查中校验目标文章存在且处于已发布状态。
 */
@Component
@RequiredArgsConstructor
class PublishedArticles {

    private final ArticleApi articleApi;

    /**
     * 校验目标文章必须存在且处于已发布状态。
     *
     * @param articleId 目标文章 ID
     * @return 文章状态信息
     * @throws BizException 若文章不存在、已删除或非已发布状态（{@link ArticleErrorCode#ARTICLE_NOT_FOUND}）
     */
    ArticleState require(long articleId) {
        return articleApi.findPublished(articleId)
                .orElseThrow(() -> new BizException(ArticleErrorCode.ARTICLE_NOT_FOUND));
    }
}
