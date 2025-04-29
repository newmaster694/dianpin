package com.hmdp.aop;

import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.RateLimiter;
import com.hmdp.annotation.Limit;
import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

@Aspect
@Slf4j
@Component
public class LimitAOP {
	
	/**
	 * 不同的接口，不同的流量控制
	 * map的key为：Limiter.key
	 */
	private final Map<String, RateLimiter> limiterMap = Maps.newConcurrentMap();
	
	@Around("@annotation(com.hmdp.annotation.Limit)")
	public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Method method = signature.getMethod();
		
		//拿limit的注释
		Limit limit = method.getAnnotation(Limit.class);
		
		if (limit != null) {
			String key = limit.key();
			RateLimiter rateLimiter;
			//验证缓存是否有命中key
			if (!limiterMap.containsKey(key)) {//没有命中，创建令牌桶
				rateLimiter = RateLimiter.create(limit.permitsPerSecond());//create()方法创建的令牌桶是以秒为单位的
				limiterMap.put(key, rateLimiter);
				log.info("新建令牌桶：{}；容量：{}", key, limit.permitsPerSecond());
			}
			
			rateLimiter = limiterMap.get(key);
			//拿令牌
			boolean tryAcquire = rateLimiter.tryAcquire(limit.timeout(), limit.timeunit());
			
			//拿不到令牌直接返回快速失败结果
			if (!tryAcquire) {
				log.debug("令牌桶：{}，获取令牌失败", key);
				this.responseFail(limit.msg());
				return null;
			}
		}
		
		return joinPoint.proceed();
	}
	
	private void responseFail(String msg) throws IOException {
		HttpServletResponse response = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getResponse();
		Result<String> result = Result.fail(msg);
		if (response != null) {
			response.getWriter().write(JSON.toJSONString(result));
		}
	}
}
