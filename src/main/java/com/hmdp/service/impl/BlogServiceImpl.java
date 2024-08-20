package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Resource
    private IFollowService followService;

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
        Double score = stringRedisTemplate.opsForZSet().score(Key, userId.toString());

        // 为什么使用工具类判断不直接判断? 因为这是一个包装类,会有空值的可能
        if (score == null) {
            /*不在Set集合里面,未点赞,可以点赞*/
            // 数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();

            if (isSuccess) {
                //更新成功,保存用户到Redis集合中 zadd key value score
                stringRedisTemplate.opsForZSet().add(Key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            /*在Set集合里面,已点赞,取消点赞*/
            // 数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();

            if (isSuccess) {
                //更新成功,把用户从Redis的set集合中移除
                stringRedisTemplate.opsForZSet().remove(Key, userId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * <p>点赞排行功能</p>
     * <p>需求:显示出点赞排行前TOP5的用户(时间戳排序)</p>
     *
     * @param id 博客id
     * @return Result
     */
    @Override
    public Result<List<UserDTO>> queryBlogLikes(Long id) {
        //1.查询出top5的点赞用户 zrange key 0 4
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);

        //2.解析出其中的用户ID
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        //到这里按照时间戳查询出来的id顺序都是正确的
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());

        String idStr = StrUtil.join(",", ids);

        //3.根据id查询出用户列表(List<UserDTO>)
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        //4.返回结果
        return Result.ok(userDTOS);
    }

    @Override
    public Result<Long> saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());

        // 保存探店博文
        boolean isSuccess = this.save(blog);

        if (!isSuccess) {
            return Result.fail("发布笔记失败");
        }

        // 查询笔记作者的所有粉丝 select * from tb_follow where follow_user_id=?
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();

        //推送笔记id给所有粉丝
        for (Follow follow : follows) {
            // 获取粉丝id
            Long userId = follow.getUserId();

            //推送
            String key = "feed:" + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blog.getId());
    }

    private void isBlogLiked(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录,无需查询是否已点赞
            return;
        }

        Long userId = user.getId();

        // 判断当前用户是否已经点赞(在Redis的set集合中做判断)
        String Key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(Key, userId.toString());

        blog.setIsLike(score != null);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

}
