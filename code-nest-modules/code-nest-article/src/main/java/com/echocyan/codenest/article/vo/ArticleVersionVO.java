package com.echocyan.codenest.article.vo;

/**
 * 文章写操作版本响应视图对象。
 * <p>
 * 返回写操作（创建、更新、发布）完成后最新的乐观锁版本号，客户端在执行下一次并发修改时须携带此版本号。
 *
 * @param id      文章 ID
 * @param version 最新乐观锁版本号
 */
public record ArticleVersionVO(Long id, Integer version) {
}
