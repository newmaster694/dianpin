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
import org.springframework.stereotype.Service;

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
		
		//扣减库存
		boolean flag = seckillVoucherService.update()
				.setSql("stock = stock - 1")
				.eq("voucher_id", voucherId).gt("stock", 0).update();
		
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
	
	@Override
	public void createVoucherOrder(VoucherOrder voucherOrder) {
	}
}
