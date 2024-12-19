package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {
	
	@Resource
	private ISeckillVoucherService seckillVoucherService;
	
	@Resource
	private StringRedisTemplate stringRedisTemplate;
	
	@Override
	public Result<List<Voucher>> queryVoucherOfShop(Long shopId) {
		// 查询优惠券信息
		List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
		// 返回结果
		return Result.ok(vouchers);
	}
	
	@Override
	@Transactional
	public void addSeckillVoucher(Voucher voucher) {
		// 保存优惠券
		save(voucher);
		
		// 保存秒杀信息
		SeckillVoucher seckillVoucher = new SeckillVoucher()
				.setVoucherId(voucher.getId())
				.setStock(voucher.getStock())
				.setBeginTime(voucher.getBeginTime())
				.setEndTime(voucher.getEndTime());
		
		seckillVoucherService.save(seckillVoucher);
		
		//将优惠卷信息保存到Redis中,不设置有效期,将来手动删除
		String voucherKey = "seckill:voucher:" + voucher.getId();
		
		Map<String, String> seckillVoucherInfo = new HashMap<>();
		
		seckillVoucherInfo.put("stock", voucher.getStock().toString());
		seckillVoucherInfo.put("beginTime", String.valueOf(voucher.getBeginTime().atZone(ZoneId.systemDefault()).toEpochSecond()));
		seckillVoucherInfo.put("endTime", String.valueOf(voucher.getEndTime().atZone(ZoneId.systemDefault()).toEpochSecond()));
		
		stringRedisTemplate.opsForHash().putAll(voucherKey, seckillVoucherInfo);
	}
}
