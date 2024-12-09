package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    Result<Object> follow(Long followUserId, Boolean isFollow);

    Result<Boolean> isFollow(Long followUserId);

    Result<List<UserDTO>> followCommons(Long id);
}
