package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
	
	private StringRedisTemplate stringRedisTemplate;
	private String name;
	
	public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
		this.stringRedisTemplate = stringRedisTemplate;
		this.name = name;
	}
	
	private static final String KEY_PREFIX = "lock:";
	private static final String ID_PREFIX = UUID.randomUUID() + "-";//防止误删锁的线程标识
	
	@Override
	public boolean tryLock(Long timeoutSec) {
		//获取线程提示
		String currentThreadId = ID_PREFIX + Thread.currentThread().getId();
		
		//获取锁(键=>当前业务名;值=>当前线程id)
		Boolean flag = stringRedisTemplate.opsForValue()
				.setIfAbsent(KEY_PREFIX + name, currentThreadId, timeoutSec, TimeUnit.SECONDS);
		
		return Boolean.TRUE.equals(flag);
	}
	
	@Override
	public void unlock() {
		//获取线程标识
		String currentThreadId = ID_PREFIX + Thread.currentThread().getId();
		
		//获取锁标识
		String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
		
		//比较标识是否一致
		if (currentThreadId.equals(id)) {
			//释放锁
			stringRedisTemplate.delete(KEY_PREFIX + name);
		}
	}
}
