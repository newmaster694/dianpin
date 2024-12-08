package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
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
	
	//阻塞队列
	private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
	
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
		
		@Override
		public void run() {
			while (true) {
				try {
					//获取阻塞队列中的订单信息
					VoucherOrder voucherOrder = orderTasks.take();
					handlerVoucherOrder(voucherOrder);
					//创建订单
				} catch (InterruptedException e) {
					log.error("处理订单异常");
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
		SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
		Long orderId = redisIdWorker.nextId("order");
		Long currentUserId = UserHolder.getUser().getId();
		
		//判断是否符合购买资格
		if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
			return Result.fail("秒杀未开始");
		}
		
		if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
			return Result.fail("秒杀已结束");
		}
		
		//执行lua脚本
		Long result = stringRedisTemplate.execute(
				SECKILL_SCRIPT,
				Collections.emptyList(),
				voucherId.toString(), currentUserId.toString(), String.valueOf(orderId)
		);
		
		int r = result.intValue();
		
		if (r != 0) {
			return Result.fail(r == 1 ? "库存不足" : "不允许重复下单");
		}
		
		//创建订单
		VoucherOrder voucherOrder = new VoucherOrder();
		voucherOrder
				.setId(orderId)
				.setUserId(currentUserId)
				.setVoucherId(voucherId);
		
		//放入阻塞队列
		orderTasks.add(voucherOrder);
		
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
		Long currentUserId = voucherOrder.getUserId();
		
		//这里是原本保留一人一单的判断逻辑(基于查询MySQL的order表数据判断),如果想要提升性能,可以删除这个判断逻辑
		synchronized (currentUserId.toString().intern()) {
			Long count = this.query().eq("user_id", currentUserId).eq("voucher_id", voucherOrder.getVoucherId()).count();
			
			if (count > 0) {
				log.error("不允许重复下单");
				return;
			}
			
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
}
