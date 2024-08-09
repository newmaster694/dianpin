package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * <p>Redis实现的全局唯一自增类</p>
 *
 * @author newmaster
 */
@Component
public class RedisIdWorker {

    private static final int COUNT_BITS = 32;

    //开始时间戳
    private static final Long BEGIN_TIMESTAMP = 1704067200L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * @param keyPrefix 不同的业务有不同的key,不适用同一个自增长
     * @return
     */
    public Long nextId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //2.生成序列号
        //2.1.获取当前日期->精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2.自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //3.拼接并返回,使用或运算拼接count值(一个为真即为真)
        return timestamp << COUNT_BITS | count;
    }
}
