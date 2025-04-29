package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {
	
	private StringRedisTemplate stringRedisTemplate;
	
	public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
		this.stringRedisTemplate = stringRedisTemplate;
	}
	
	@Override
	public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
		// 1.获取请求头中的token
		String token = request.getHeader("authorization");
		
		if (StrUtil.isBlank(token)) {
			// token不存在
			return true;
		}
		
		// 2.基于token获取Redis中的用户
		String key = LOGIN_USER_KEY + token;
		Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
		
		// 3.判断用户是否存在
		if (userMap.isEmpty()) {
			// 4.用户不存在
			return true;
		}
		
		// 5.将查询到的Hash数据转成UserDto对象
		UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), true);
		
		// 6.保存用户信息到ThreadLocal
		UserHolder.saveUser(userDTO);
		
		// 7.刷新token有效期
		stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
		
		// 8.放行
		return true;
	}
	
	/**
	 * <p>登陆结束后销毁用户信息,防止内存泄漏</p>
	 *
	 * @param request
	 * @param response
	 * @param handler
	 * @param ex
	 */
	@Override
	public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, Exception ex) {
		UserHolder.removeUser();
	}
}
