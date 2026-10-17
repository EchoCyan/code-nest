package com.echocyan.codenest.article.api;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 文章生命周期状态枚举。
 * <p>
 * 持久化层映射数值状态码，API 交互层使用枚举名称序列化。
 */
public enum ArticleStatus {

    /**
     * 草稿态：仅作者本人可见，不参与检索与公开列表。
     */
    DRAFT(0),

    /**
     * 已发布：全站公开可见，支持检索、互动与推荐。
     */
    PUBLISHED(1);

    @EnumValue
    private final int code;

    ArticleStatus(int code) {
        this.code = code;
    }
}
