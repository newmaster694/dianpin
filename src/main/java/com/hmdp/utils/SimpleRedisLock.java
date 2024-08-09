package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("./script/unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        // 获取线程的标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 获取锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + name
                , threadId
                , timeoutSec
                , TimeUnit.SECONDS);

        // 避免自动拆箱时产生的空指针问题,如果为true返回true,false/null返回false
        return Boolean.TRUE.equals(flag);
    }

    @Override
    public void unlock() {
        // 调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),           // 执行的键(列表)
                ID_PREFIX + Thread.currentThread().getId()              //执行的参数值
        );
    }

/*    @Override
    public void unlock() {
        // 释放锁的时候应该判断一下线程标识,如果被不同线程释放掉了锁,就会造成线程安全问题
        // 获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 获取锁中的标识
        String id = stringRedisTemplate.opsForValue().get(KET_PREFIX + name);

        // 判断是否一致
        if (threadId.equals(id)) {
            // 释放锁
            stringRedisTemplate.delete(KET_PREFIX + name);
        }
    }*/
}
