package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ShopServiceImpl shopService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L, 10L);
    }

    // 生成一个初始时间
    @Test
    void testLocalTime() {
        LocalDateTime time = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second=" + second);
    }

    @Test
    void testRedisson() throws InterruptedException {
        // 获取锁(可重入),指定锁的名称
        RLock lock = redissonClient.getLock("anyLock");

        // 尝试获取锁,参数分别为:获取锁的最大等待时间(期间会重试),锁自动释放时间,时间单位
        boolean isLock = lock.tryLock(1, 10, TimeUnit.SECONDS);

        // 判断释放获取成功
        if (isLock) {
            try {
                System.out.println("执行业务");
            } finally {
                // 释放锁
                lock.unlock();
            }
        }
    }

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIDWorker() throws InterruptedException {
        // PS:线程池是异步加载的,所以需要用CountDownLatch去计时
        // 每一个线程(任务)CountDown1次,300个线程就是300次

        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            // 每个线程来了都生成100个ID
            for (int i = 0; i < 100; i++) {
                Long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();

        for (int i = 0; i < 300; i++) {
            // 把任务提交300次
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }
}