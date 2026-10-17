package com.echocyan.codenest.article.convert;

import com.echocyan.codenest.article.api.CommentBrief;
import com.echocyan.codenest.article.entity.Comment;
import com.echocyan.codenest.common.util.Texts;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 评论对象 MapStruct 映射器。
 */
@Mapper(imports = Texts.class)
public interface CommentConverter {

    /**
     * 将评论实体转换为简要摘要投影，自动截取正文文本。
     */
    @Mapping(target = "summary", expression = "java(Texts.head(comment.getContent(), CommentBrief.SUMMARY_LENGTH))")
    CommentBrief toBrief(Comment comment);
}
