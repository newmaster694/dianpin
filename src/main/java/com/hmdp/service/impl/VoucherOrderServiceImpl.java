package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
	
	@Resource
	private ISeckillVoucherService seckillVoucherService;
	
	@Resource
	private RedisIdWorker redisIdWorker;
	
	@Resource
	private StringRedisTemplate stringRedisTemplate;
	
	@Resource
	private RedissonClient redissonClient;
	
	//代理对象
	IVoucherOrderService proxy;
	
	//异步处理的线程池
	private static final ExecutorService SKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
	
	//初始化脚本
	private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
	
	static {
		SECKILL_SCRIPT = new DefaultRedisScript<>();
		SECKILL_SCRIPT.setLocation(new ClassPathResource("/script/seckill.lua"));
		SECKILL_SCRIPT.setResultType(Long.class);
	}
	
	@PostConstruct
	private void init() {
		SKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
	}
	
	private class VoucherOrderHandler implements Runnable {
		
		String queueName = "stream.orders";
		
		@Override
		public void run() {
			while (true) {
				try {
					//获取redis消息队列中的信息
					List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
							Consumer.from("g1", "c1"),
							StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
							StreamOffset.create(queueName, ReadOffset.lastConsumed())
					);
					
					//判断消息是否获取成功
					if (list == null || list.isEmpty()) {
						//获取失败,说明没有消息,继续循环
						continue;
					}
					
					//获取成功,解析出消息队列中的订单信息;第一个是消息的ID,剩下的是解析出的数据
					MapRecord<String, Object, Object> entries = list.get(0);
					Map<Object, Object> value = entries.getValue();
					VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
					
					//获取成功,创建订单
					handlerVoucherOrder(voucherOrder);
					
					//ACK确认,否则消息会进入pending-list
					stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", entries.getId());
				} catch (Exception e) {
					log.error("处理订单异常", e);
					//处理异常->去pending-list里获取订单信息处理
					handlePendingList();
				}
			}
		}
		
		private void handlePendingList() {
			while (true) {
				try {
					//获取redis pendlist 队列中的信息
					log.info("获取redis pendlist 队列中的信息");
					List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
							Consumer.from("g1", "c1"),
							StreamReadOptions.empty().count(1),
							StreamOffset.create(queueName, ReadOffset.from("0"))
					);
					
					//判断消息是否获取成功
					if (list == null || list.isEmpty()) {
						//获取失败,说明pendinglist没有消息,结束循环
						break;
					}
					
					//获取成功,解析出消息队列中的订单信息;第一个是消息的ID,剩下的是解析出的数据
					MapRecord<String, Object, Object> entries = list.get(0);
					Map<Object, Object> value = entries.getValue();
					VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
					
					//获取成功,创建订单
					handlerVoucherOrder(voucherOrder);
					
					//ACK确认,否则消息会进入pending-list
					stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", entries.getId());
				} catch (Exception e) {
					log.error("处理pending-list订单异常", e);
					try {
						Thread.sleep(20);
					} catch (InterruptedException ex) {
						throw new RuntimeException(ex);
					}
				}
			}
		}
		
		/**
		 * 创建订单方法
		 *
		 * @param voucherOrder
		 */
		private void handlerVoucherOrder(VoucherOrder voucherOrder) {
			Long userId = voucherOrder.getUserId();
			
			RLock redisLock = redissonClient.getLock("lock:order:" + userId);
			
			boolean isLock = redisLock.tryLock();
			
			if (!isLock) {
				log.error("不允许重复下单");
				return;
			}
			
			try {
				//创建订单逻辑
				log.info("MySQL数据库创建订单");
				proxy.createVoucherOrder(voucherOrder);
			} finally {
				redisLock.unlock();
			}
		}
	}
	
	/**
	 * 秒杀下单方法-v1.0
	 *
	 * @param voucherId
	 * @return
	 */
	@Override
	public Result<Long> seckillVoucher(Long voucherId) {
		Long orderId = redisIdWorker.nextId("order");
		Long currentUserId = UserHolder.getUser().getId();
		
		//执行lua脚本
		log.info("Redis操作=>判断库存,一人一单");
		Long result = stringRedisTemplate.execute(
				SECKILL_SCRIPT,
				Collections.emptyList(),
				voucherId.toString(), currentUserId.toString(), String.valueOf(orderId)
		);
		
		int r = result.intValue();
		
		if (r != 0) {
			switch (r) {
				case 1:
					return Result.fail("还未到秒杀时间,请稍后再试哦");
				case 2:
					return Result.fail("库存不足了,请不要再试了/ᐠ .ᆺ. ᐟ\\ﾉ");
				case 3:
					return Result.fail("你已经下过单了,给别人留点机会吧⋛⋋( ‘Θ’)⋌⋚");
			}
		}
		
		proxy = (IVoucherOrderService) AopContext.currentProxy();
		
		return Result.ok(orderId);
	}
	
	/**
	 * 创建订单方法
	 *
	 * @param voucherOrder
	 * @return
	 */
	@Transactional
	public void createVoucherOrder(VoucherOrder voucherOrder) {
		//扣减库存
		boolean flag = seckillVoucherService.update()
				.setSql("stock = stock - 1") //set stock = stock -1
				.eq("voucher_id", voucherOrder.getVoucherId())
				.gt("stock", 0)
				.update(); //where id = ? and stock > 0
		
		if (!flag) {
			log.error("库存不足");
			return;
		}
		
		//创建订单
		this.save(voucherOrder);
	}
}
