package com.echocyan.codenest.counter.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 计数统计表通用 MyBatis 数据访问层。
 *
 * <p>提供文章、用户、评论三张计数统计表的公共 SQL 操作。表名与列名均由内部安全映射推导，不接受外部未受信任入参。
 */
@Mapper
public interface CounterMapper {

    /**
     * 重置单个对象的指定计数字段为绝对值；不存在对应行时执行插入。
     *
     * @param table    目标表名
     * @param idColumn 主键 ID 列名
     * @param column   计数字段列名
     * @param id       对象 ID
     * @param value    重置后的计数值
     * @param now      当前时间戳
     */
    @Insert("""
            INSERT INTO ${table} (${idColumn}, ${column}, created_at, updated_at)
            VALUES (#{id}, #{value}, #{now}, #{now})
            ON DUPLICATE KEY UPDATE ${column} = #{value}, updated_at = #{now}
            """)
    void reset(@Param("table") String table, @Param("idColumn") String idColumn, @Param("column") String column,
               @Param("id") long id, @Param("value") long value, @Param("now") LocalDateTime now);

    /**
     * 批量覆写多个对象的全量计数指标（绝对值）；不存在对应行时执行插入。
     *
     * @param table    目标表名
     * @param idColumn 主键 ID 列名
     * @param columns  各计数列名列表
     * @param rows     批量数据行列表，每行包含主键 ID 及 columns 对应顺序的计数值
     * @param now      当前时间戳
     */
    @Insert("""
            <script>
            INSERT INTO ${table} (${idColumn},
            <foreach collection="columns" item="column" separator=",">${column}</foreach>, created_at, updated_at)
            VALUES
            <foreach collection="rows" item="row" separator=",">
            (<foreach collection="row" item="value" separator=",">#{value}</foreach>, #{now}, #{now})
            </foreach>
            AS new ON DUPLICATE KEY UPDATE
            <foreach collection="columns" item="column" separator=",">${column} = new.${column}</foreach>,
            updated_at = new.updated_at
            </script>
            """)
    void upsertAll(@Param("table") String table, @Param("idColumn") String idColumn,
                   @Param("columns") List<String> columns, @Param("rows") List<List<Long>> rows,
                   @Param("now") LocalDateTime now);

    /**
     * 批量查询指定 ID 集合的计数记录。
     *
     * @param table    目标表名
     * @param idColumn 主键 ID 列名
     * @param ids      对象 ID 集合
     * @return 数据库行数据映射列表
     */
    @Select("""
            <script>
            SELECT * FROM ${table} WHERE ${idColumn} IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Map<String, Object>> selectByIds(@Param("table") String table, @Param("idColumn") String idColumn,
                                          @Param("ids") Collection<Long> ids);

    /**
     * 查询指定主键区间 {@code (afterId, upperId]} 内该计数字段大于 0 的对象 ID 列表。
     *
     * @param table    目标表名
     * @param idColumn 主键 ID 列名
     * @param column   计数字段列名
     * @param afterId  起始 ID（不含）
     * @param upperId  截止 ID（含）
     * @return 计数值大于 0 的对象 ID 列表
     */
    @Select("""
            SELECT ${idColumn} FROM ${table}
            WHERE ${idColumn} > #{afterId} AND ${idColumn} <= #{upperId} AND ${column} > 0
            """)
    List<Long> selectNonZeroIds(@Param("table") String table, @Param("idColumn") String idColumn,
                                @Param("column") String column, @Param("afterId") long afterId,
                                @Param("upperId") long upperId);
}
