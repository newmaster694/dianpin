package com.hmdp.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
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
	private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
	
	static {
		UNLOCK_SCRIPT = new DefaultRedisScript<>();
		UNLOCK_SCRIPT.setLocation(new ClassPathResource("/script/unlock.lua"));
		UNLOCK_SCRIPT.setResultType(Long.class);
	}
	
	/**
	 * 获取锁
	 *
	 * @param timeoutSec 锁持有的超过时间,过期后自动释放
	 * @return
	 */
	@Override
	public boolean tryLock(Long timeoutSec) {
		//获取线程标识
		String currentThreadId = ID_PREFIX + Thread.currentThread().getId();
		
		//获取锁(键=>当前业务名;值=>当前线程id)
		Boolean flag = stringRedisTemplate.opsForValue()
				.setIfAbsent(KEY_PREFIX + name, currentThreadId, timeoutSec, TimeUnit.SECONDS);
		
		return Boolean.TRUE.equals(flag);
	}
	
	/**
	 * 释放锁
	 */
	@Override
	public void unlock() {
		//调用lua脚本解决释放锁的原子性问题
		stringRedisTemplate.execute(
				UNLOCK_SCRIPT,
				Collections.singletonList(KEY_PREFIX + name),
				ID_PREFIX + Thread.currentThread().getId()
		);
	}
}
