package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Override
    public Result<Object> follow(Long followUserId, Boolean isFollow) {
        // 获取当前登录用户
        Long currentUserId = UserHolder.getUser().getId();

        // 判断到底是关注还是取关
        if (isFollow) {
            // 关注->新增数据
            Follow follow = new Follow();
            follow.setUserId(currentUserId);
            follow.setFollowUserId(followUserId);

            this.save(follow);
        } else {
            // 取关->删除数据
            this.remove(new QueryWrapper<Follow>()
                    .eq("user_id", currentUserId).eq("follow_user_id", followUserId));
        }

        return Result.ok();
    }

    @Override
    public Result<Boolean> isFollow(Long followUserId) {
        //获取登录用户
        Long currentUserId = UserHolder.getUser().getId();

        //查询是否关注->select * from tb_follow where user_id = ? and follow_user_id = ?;
        Long count = this.query().eq("user_id", currentUserId).eq("follow_user_id", followUserId).count();

        //判断
        return Result.ok(count > 0);
    }
}
