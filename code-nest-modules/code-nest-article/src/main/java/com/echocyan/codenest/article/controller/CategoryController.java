package com.echocyan.codenest.article.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.echocyan.codenest.article.api.CategoryBrief;
import com.echocyan.codenest.article.convert.CategoryConverter;
import com.echocyan.codenest.article.service.CategoryService;
import com.echocyan.codenest.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文章分类控制器。
 */
@SaIgnore
@Tag(name = "分类")
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryConverter categoryConverter;

    /**
     * 查询全部分类列表，按预置排序权重升序返回。
     *
     * @return 分类简要投影列表
     */
    @Operation(summary = "全部分类", description = "按预置顺序返回")
    @GetMapping("/categories")
    public Result<List<CategoryBrief>> list() {
        return Result.ok(categoryConverter.toBriefs(categoryService.listInOrder()));
    }
}
