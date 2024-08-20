package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    /**
     * 关注用户接口
     * @param followUserId 要关注的作者的id
     * @param isFollow true:要关注;false:取关
     * @return Result
     */
    @PutMapping("/{id}/{isFollow}")
    public Result<Object> follow(@PathVariable("id") Long followUserId, @PathVariable Boolean isFollow) {
        return followService.follow(followUserId, isFollow);
    }

    /**
     * 查询有没有关注过当前用户接口
     * @param followUserId 要关注的用户id
     * @return Result
     */
    @GetMapping("/or/not/{id}")
    public Result<Boolean> isFollow(@PathVariable("id") Long followUserId) {
        return followService.isFollow(followUserId);
    }

    /**
     * 共同关注接口
     * @param id
     * @return
     */
    @GetMapping("/common/{id}")
    public Result<List<UserDTO>> togeterFollow(@PathVariable Long id) {
        return followService.followCommons(id);
    }
}
