package com.hmdp.interceptor;

import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
	@Override
	public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
		// 1.判断是否需要拦截(ThreadLocal中是否有用户)
		if (UserHolder.getUser() == null) {
			// ThreadLocal中没有用户,需要拦截,设置状态码
			response.setStatus(401);
			return false;
		}
		
		// 有用户,放行
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
