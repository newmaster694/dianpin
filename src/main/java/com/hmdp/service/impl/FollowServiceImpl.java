package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result<Object> follow(Long followUserId, Boolean isFollow) {
        // 获取当前登录用户
        Long currentUserId = UserHolder.getUser().getId();

        String key = "follows:" + currentUserId;

        // 判断到底是关注还是取关
        if (isFollow) {
            // 关注->新增数据(数据库操作)
            Follow follow = new Follow();
            follow.setUserId(currentUserId);
            follow.setFollowUserId(followUserId);

            boolean isSuccess = this.save(follow);

            if (isSuccess) {
                // 把关注用户的id,放入Redis的set集合 sadd userId followerUserId
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            // 取关->删除数据
            boolean isSuccess = this.remove(new QueryWrapper<Follow>()
                    .eq("user_id", currentUserId).eq("follow_user_id", followUserId));

            if (isSuccess) {
                // 把关注的用户id从Redis集合中移除
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result<Boolean> isFollow(Long followUserId) {
        // 获取登录用户
        Long currentUserId = UserHolder.getUser().getId();

        // 查询是否关注->select * from tb_follow where user_id = ? and follow_user_id = ?;
        Long count = this.query().eq("user_id", currentUserId).eq("follow_user_id", followUserId).count();

        // 判断
        return Result.ok(count > 0);
    }

    @Override
    public Result<List<UserDTO>> followCommons(Long id) {
        // 1.获取当前登录用户
        Long currentUserId = UserHolder.getUser().getId();
        String keyOfCurrentUserId = "follows:" + currentUserId;
        String keyOfTargetUserId = "follows:" + id;

        // 2.求目标用户和当前用户的交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(keyOfCurrentUserId, keyOfTargetUserId);

        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        // 4.查询用户
        List<UserDTO> userDTOS = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }
}
