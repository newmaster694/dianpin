package com.hmdp.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 限流注解
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Limit {
	/**
	 * 资源的key，唯一
	 * 作用：不同的接口，不同的流量控制
	 * @return
	 */
	String key() default "";
	
	/**
	 * 最多的访问限制次数
	 * @return
	 */
	double permitsPerSecond();
	
	/**
	 * 获取令牌最大等待时间
	 * @return
	 */
	long timeout();
	
	/**
	 * 获取令牌最大等待时间单位（例：分钟/秒/毫秒） 默认：毫秒
	 * @return
	 */
	TimeUnit timeunit() default TimeUnit.SECONDS;
	
	/**
	 * 得不到令牌的提示语
	 * @return
	 */
	String msg() default "系统繁忙，请稍后再试!";
}
