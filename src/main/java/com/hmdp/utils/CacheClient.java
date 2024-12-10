package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
	
	private final StringRedisTemplate stringRedisTemplate;
	
	public CacheClient(StringRedisTemplate stringRedisTemplate) {
		this.stringRedisTemplate = stringRedisTemplate;
	}
	
	/**
	 * 将任意的java对象序列化为json并存储到string类型的key中,并设置过期时间
	 *
	 * @param key
	 * @param value
	 * @param time
	 * @param unit
	 */
	public void set(String key, Object value, Long time, TimeUnit unit) {
		stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
	}
	
	private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
	
	/**
	 * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
	 *
	 * @param key
	 * @param value
	 * @param time
	 * @param unit
	 */
	public void setWithLogicalExprie(String key, Object value, Long time, TimeUnit unit) {
		//设置逻辑过期
		RedisData redisData = new RedisData();
		redisData.setData(value);
		redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
		
		//写入Redis
		stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
	}
	
	/**
	 * 根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
	 *
	 * @param keyPrefix
	 * @param id
	 * @param type
	 * @param dbFallBabk
	 * @param time
	 * @param unit
	 * @param <R>
	 * @param <ID>
	 * @return
	 */
	public <R, ID> R queryWithPassThrough(
			String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBabk, Long time, TimeUnit unit) {
		String key = keyPrefix + id;
		
		/*
		  - 如果 json 是一个空字符串（例如 ""），第一个 if 语句不会执行，但第二个 if 语句会执行，最终返回 null(缓存空值)。
		  - 如果 json 为 null，两个 if 语句都不会执行，最终返回 查询数据库。
		  - 如果 json 是一个有效的非空字符串，第一个 if 语句会执行，返回反序列化的对象。
		 */
		
		//从Redis查询缓存
		String json = stringRedisTemplate.opsForValue().get(key);
		
		if (StrUtil.isNotBlank(json)) {
			return JSONUtil.toBean(json, type);
		}
		
		if (json != null) {
			return null;
		}
		
		//不存在,查询数据库
		R r = dbFallBabk.apply(id);
		
		if (r == null) {
			//将空值写入Redis并返回错误消息
			stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
			return null;
		}
		
		//存在,写入Redis
		this.set(key, r, time, unit);
		
		return r;
	}
	
	/**
	 * 根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题<br/>
	 * 注意:使用该方法(逻辑过期方案)解决缓存击穿问题需要把数据提前加载到Redis里才能正确工作!!!(在单元测试里已经写好了加载代码...)
	 *
	 * @param keyPrefix
	 * @param id
	 * @param type
	 * @param dbFallBack
	 * @param time
	 * @param unit
	 * @param <R>
	 * @param <ID>
	 * @return
	 */
	public <R, ID> R queryWithLogicalExprie(
			String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
		String key = keyPrefix + id;
		
		String json = stringRedisTemplate.opsForValue().get(key);
		
		if (StrUtil.isBlank(json)) {
			return null;
		}
		
		RedisData redisData = JSONUtil.toBean(json, RedisData.class);
		R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
		LocalDateTime expireTime = redisData.getExpireTime();
		
		//判断是否过期
		if (expireTime.isAfter(LocalDateTime.now())) {
			//未过期,返回店铺信息
			return r;
		}
		
		//已过期,需要缓存重建;获取互斥锁
		String lockKey = LOCK_SHOP_KEY + id;
		boolean isLock = tryLock(lockKey);
		
		if (isLock) {
			//成功,开启独立线程,启动缓存重建
			CACHE_REBUILD_EXECUTOR.submit(() -> {
				try {
					//查询数据库
					R newR = dbFallBack.apply(id);
					
					//重建缓存
					this.setWithLogicalExprie(key, newR, time, unit);
				} catch (Exception e) {
					throw new RuntimeException(e);
				} finally {
					//释放锁
					unlock(lockKey);
				}
			});
		}
		
		return r;
	}
	
	private boolean tryLock(String key) {
		Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
		return BooleanUtil.isTrue(flag);
	}
	
	private void unlock(String key) {
		stringRedisTemplate.delete(key);
	}
}
