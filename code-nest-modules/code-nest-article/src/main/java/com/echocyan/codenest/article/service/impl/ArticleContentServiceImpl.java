package com.echocyan.codenest.article.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.echocyan.codenest.article.entity.ArticleContent;
import com.echocyan.codenest.article.mapper.ArticleContentMapper;
import com.echocyan.codenest.article.service.ArticleContentService;
import org.springframework.stereotype.Service;

/**
 * 文章正文存储服务实现类。
 */
@Service
public class ArticleContentServiceImpl extends ServiceImpl<ArticleContentMapper, ArticleContent>
        implements ArticleContentService {
}
