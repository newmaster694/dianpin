package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result<Blog> queryBlogById(Long id) {
        // 查询blog
        Blog blog = this.getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }

        // 查询blog有关的用户
        queryBlogUser(blog);

        //判断当前Blog有没有被登录用户点过赞
        isBlogLiked(blog);

        return Result.ok(blog);
    }

    @Override
    public Result<List<Blog>> queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result<Object> likeBlog(Long id) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();

        // 判断当前用户是否已经点赞(在Redis的set集合中做判断)
        String Key = BLOG_LIKED_KEY + id;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(Key, userId.toString());

        // 为什么使用工具类判断不直接判断? 因为这是一个包装类,会有空值的可能
        if (BooleanUtil.isFalse(isMember)) {
            /*不在Set集合里面,未点赞,可以点赞*/

            // 数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();

            if (isSuccess) {
                //更新成功,保存用户到Redis集合中
                stringRedisTemplate.opsForSet().add(Key, userId.toString());
            }
        } else {
            /*在Set集合里面,已点赞,取消点赞*/

            // 数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();

            if (isSuccess) {
                //更新成功,把用户从Redis的set集合中移除
                stringRedisTemplate.opsForSet().remove(Key, userId.toString());
            }
        }
        return Result.ok();
    }

    private void isBlogLiked(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }

        Long userId = user.getId();

        // 判断当前用户是否已经点赞(在Redis的set集合中做判断)
        String Key = BLOG_LIKED_KEY + blog.getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(Key, userId.toString());

        blog.setIsLike(BooleanUtil.isTrue(isMember));
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

}
