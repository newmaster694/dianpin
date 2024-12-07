package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
	
	private StringRedisTemplate stringRedisTemplate;
	private String name;
	
	public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
		this.stringRedisTemplate = stringRedisTemplate;
		this.name = name;
	}
	
	private static final String KEY_PREFIX = "lock:";
	
	@Override
	public boolean tryLock(Long timeoutSec) {
		//获取线程提示
		long currentThreadId = Thread.currentThread().getId();
		
		//获取锁(键=>当前业务名;值=>当前线程id)
		Boolean flag = stringRedisTemplate.opsForValue()
				.setIfAbsent(KEY_PREFIX + name, currentThreadId + "", timeoutSec, TimeUnit.SECONDS);
		
		return Boolean.TRUE.equals(flag);
	}
	
	@Override
	public void unlock() {
		stringRedisTemplate.delete(KEY_PREFIX + name);
	}
}
