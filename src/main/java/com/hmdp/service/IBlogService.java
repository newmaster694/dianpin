package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
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
public interface IBlogService extends IService<Blog> {

    Result<Blog> queryBlogById(Long id);

    Result<List<Blog>> queryHotBlog(Integer current);

    Result<Object> likeBlog(Long id);

    Result<List<UserDTO>> queryBlogLikes(Long id);

    Result<Long> saveBlog(Blog blog);

    Result<ScrollResult> queryBlogOfFollow(Long max, Integer offset);
}
