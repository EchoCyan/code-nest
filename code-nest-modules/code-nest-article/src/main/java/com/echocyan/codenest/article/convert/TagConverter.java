package com.echocyan.codenest.article.convert;

import com.echocyan.codenest.article.entity.Tag;
import com.echocyan.codenest.article.vo.TagVO;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 标签对象 MapStruct 映射器。
 */
@Mapper
public interface TagConverter {

    /**
     * 将标签实体转换为视图对象。
     */
    TagVO toVO(Tag tag);

    /**
     * 将标签实体列表批量转换为视图对象列表。
     */
    List<TagVO> toVOs(List<Tag> tags);
}
