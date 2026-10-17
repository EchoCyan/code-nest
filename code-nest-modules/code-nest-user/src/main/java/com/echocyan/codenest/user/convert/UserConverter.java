package com.echocyan.codenest.user.convert;

import com.echocyan.codenest.user.api.UserBrief;
import com.echocyan.codenest.user.entity.User;
import com.echocyan.codenest.user.vo.UserCountsVO;
import com.echocyan.codenest.user.vo.UserProfileVO;
import org.mapstruct.Mapper;

/**
 * 用户模块对象模型转换器。
 * <p>
 * 基于 MapStruct 实现实体与视图对象、跨模块简要信息之间的映射转换。
 */
@Mapper
public interface UserConverter {

    /**
     * 将用户实体与统计计数合并转换为个人主页展示对象。
     *
     * @param user   用户实体
     * @param counts 用户统计计数
     * @return 个人主页资料展示对象
     */
    UserProfileVO toProfileVO(User user, UserCountsVO counts);

    /**
     * 将用户实体转换为跨模块简要信息。
     *
     * @param user 用户实体
     * @return 用户基础简要信息
     */
    UserBrief toBrief(User user);
}

