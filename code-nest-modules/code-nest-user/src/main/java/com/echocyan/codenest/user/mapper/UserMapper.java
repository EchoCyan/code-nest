package com.echocyan.codenest.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.echocyan.codenest.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问 Mapper 接口。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}

