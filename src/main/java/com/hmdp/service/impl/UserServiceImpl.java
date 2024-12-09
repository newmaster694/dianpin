package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result<Object> sendCode(String phone, HttpSession session) {
        // 1.校验登录手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果登录手机号不符合,返回错误信息
            return Result.fail("手机号码无效");
        }

        // 3.登录手机号符合,生成并将验证码
        String code = RandomUtil.randomNumbers(4);

        // 3.1保存验证码到session
        // session.setAttribute("code", code);

        // 3.2保存验证码到Redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // TODO 4.发送验证码(调用第三方的平台)
        log.info("短信发送成功,验证码:{}", code);

        // 5.返回成功信息
        return Result.ok(code);
    }

    @Override
    public Result<String> login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号与验证码
        String phone = loginForm.getPhone();
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();

        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果手机号校验不符合,返回错误信息
            return Result.fail("手机号码无效");
        }

        if (cacheCode == null || !cacheCode.equals(code)) {
            // 2.验证码与手机号校验不一致:返回错误信息
            return Result.fail("验证码错误");
        }

        // 3.根据手机号查询用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone, phone);
        User user = this.getOne(queryWrapper);

        // 4.登录用户不存在:创建新用户保存到数据库并登录
        if (user == null) {
            user = createUserWithPhone(phone);
        }

        // 5.登录用户存在,将用户信息保存到Redis
        // 5.1生成token,作为登录令牌(使用UUID)
        String token = UUID.randomUUID().toString(true);

        // 5.2将User对象转换为HashMap
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fildName, fildValue) -> fildValue.toString()));

        // 5.3将转换后的Haah存储到Redis中
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

        // 5.4设置用户信息(token)存储的有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 5.4返回Token
        return Result.ok(token);
    }

    @Override
    public Result<Object> sign() {
        //获取当前登录用户
        Long currentUserId = UserHolder.getUser().getId();

        //获取日期
        LocalDateTime now = LocalDateTime.now();

        //拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + currentUserId + keySuffix;

        //获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();

        //写入Redis       SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);

        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        this.save(user);

        return user;
    }
}
