package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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
	
	/**
	 * 秒杀下单方法-v1.0
	 *
	 * @param voucherId
	 * @return
	 */
	@Override
	public Result<Long> seckillVoucher(Long voucherId) {
		SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
		
		//判断是否符合购买资格
		if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
			return Result.fail("秒杀未开始");
		}
		
		if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
			return Result.fail("秒杀已结束");
		}
		
		if (voucher.getStock() < 1) {
			return Result.fail("库存不足");
		}
		
		Long currentUserId = UserHolder.getUser().getId();
		SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + currentUserId);
		
		boolean isLock = lock.tryLock(500L);
		
		if (!isLock) {
			return Result.fail("不允许重复购买");
		}
		
		try {
			IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
			return proxy.createVoucherOrder(voucherId);
		} finally {
			lock.unlock();
		}
	}
	
	/**
	 * 创建订单方法
	 * @param voucherId
	 * @return
	 */
	@Transactional
	public Result<Long> createVoucherOrder(Long voucherId) {
		Long currentUserId = UserHolder.getUser().getId();
		synchronized (currentUserId.toString().intern()) {
			Long count = this.query().eq("user_id", currentUserId).eq("voucher_id", voucherId).count();
			
			if (count > 0) {
				return Result.fail("不允许重复购买");
			}
			
			//扣减库存
			boolean flag = seckillVoucherService.update()
					.setSql("stock = stock - 1") //set stock = stock -1
					.eq("voucher_id", voucherId).gt("stock", 0).update(); //where id = ? and stock > 0
			
			if (!flag) {
				return Result.fail("库存不足");
			}
			
			//创建订单
			VoucherOrder voucherOrder = new VoucherOrder();
			Long orderId = redisIdWorker.nextId("order");
			voucherOrder
					.setId(orderId)
					.setUserId(UserHolder.getUser().getId())
					.setVoucherId(voucherId);
			
			this.save(voucherOrder);
			
			return Result.ok(orderId);
		}
	}
}
