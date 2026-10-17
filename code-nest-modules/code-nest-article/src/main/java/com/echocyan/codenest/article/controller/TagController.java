package com.echocyan.codenest.article.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.article.convert.TagConverter;
import com.echocyan.codenest.article.service.TagService;
import com.echocyan.codenest.article.vo.TagVO;
import com.echocyan.codenest.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文章标签控制器。
 */
@SaIgnore
// 类名 Tag 与本模块的实体 Tag 重名，此处使用 OpenAPI 的全限定名避免歧义
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签")
@RestController
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;
    private final TagConverter tagConverter;

    /**
     * 查询全部标签列表，按标签 ID 升序返回。
     *
     * @return 标签视图对象列表
     */
    @Operation(summary = "全部标签")
    @GetMapping("/tags")
    public Result<List<TagVO>> list() {
        return Result.ok(tagConverter.toVOs(tagService.listInOrder()));
    }
}
