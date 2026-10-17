package com.echocyan.codenest.article.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.echocyan.codenest.article.api.ArticleErrorCode;
import com.echocyan.codenest.article.entity.Comment;
import com.echocyan.codenest.article.vo.CommentVO;
import com.echocyan.codenest.article.vo.ReplyVO;
import com.echocyan.codenest.common.exception.BizException;
import com.echocyan.codenest.common.exception.CommonErrorCode;
import com.echocyan.codenest.common.result.CursorResult;

/**
 * 评论与楼中楼回复服务接口。
 */
public interface CommentService extends IService<Comment> {

    /**
     * 对已发布文章发表一级评论。
     *
     * @param articleId 目标文章 ID
     * @param userId    发表者用户 ID
     * @param content   评论正文
     * @return 新建的评论实体
     * @throws BizException 若文章不存在、已删除或非已发布状态抛出 {@link ArticleErrorCode#ARTICLE_NOT_FOUND}
     */
    Comment comment(long articleId, long userId, String content);

    /**
     * 基于游标分页查询文章下的一级评论列表，按 ID 降序排列。
     *
     * @param articleId 目标文章 ID
     * @param cursor    游标偏移量（上一页最后一条记录 ID），首批传 {@code null}
     * @param size      单页记录数
     * @return 一级评论游标分页结果
     * @throws BizException 若文章不存在、已删除或非已发布状态抛出 {@link ArticleErrorCode#ARTICLE_NOT_FOUND}
     */
    CursorResult<CommentVO> listComments(long articleId, Long cursor, int size);

    /**
     * 发表二级楼中楼回复。
     *
     * @param commentId     被回复的目标评论或回复 ID
     * @param userId        发表者用户 ID
     * @param content       回复正文
     * @param replyToUserId 被回复目标用户 ID，为空时自动推导
     * @return 新建的回复实体
     * @throws BizException 若目标记录不存在抛出 {@link ArticleErrorCode#COMMENT_NOT_FOUND}；
     *                      若所属文章非已发布抛出 {@link ArticleErrorCode#ARTICLE_NOT_FOUND}；
     *                      若被回复用户未参与讨论抛出 {@link ArticleErrorCode#REPLY_TO_USER_INVALID}
     */
    Comment reply(long commentId, long userId, String content, Long replyToUserId);

    /**
     * 基于游标分页查询一级评论下的二级回复列表，按 ID 升序排列。
     *
     * @param commentId 根评论 ID
     * @param cursor    游标偏移量（上一页最后一条记录 ID），首批传 {@code null}
     * @param size      单页记录数
     * @return 二级回复游标分页结果
     * @throws BizException 若根评论不存在或其本身不是一级评论抛出 {@link ArticleErrorCode#COMMENT_NOT_FOUND}；
     *                      若所属文章非已发布抛出 {@link ArticleErrorCode#ARTICLE_NOT_FOUND}
     */
    CursorResult<ReplyVO> listReplies(long commentId, Long cursor, int size);

    /**
     * 软删除评论或回复。
     *
     * @param id     待删除的评论或回复 ID
     * @param userId 当前操作人用户 ID
     * @throws BizException 若记录不存在抛出 {@link ArticleErrorCode#COMMENT_NOT_FOUND}；
     *                      若操作人非作者本人抛出 {@link CommonErrorCode#FORBIDDEN}
     */
    void delete(long id, long userId);
}
