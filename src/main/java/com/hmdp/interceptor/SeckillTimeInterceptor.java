package com.hmdp.interceptor;

import com.alibaba.fastjson2.JSON;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;

/**
 * 判断优惠券有效时间拦截器
 */
public class SeckillTimeInterceptor implements HandlerInterceptor {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取URL的路径信息
        String pathInfo = request.getPathInfo();

        //使用"/"符号分割路径
        String[] parts = pathInfo.split("/");

        if (parts.length <= 2 || !"seckill".equals(parts[2])) {
            //不满足条件,返回错误信息
            response.getWriter().write(JSON.toJSONString(Result.fail("未查询到优惠券")));
            return false;
        }

        //获取优惠券
        String voucherId = parts[3];
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        //判断优惠券秒杀时间
        if (voucher.getBeginTime().isAfter(LocalDateTime.now()) && voucher.getEndTime().isBefore(LocalDateTime.now())) {
            response.getWriter().write(JSON.toJSONString(Result.fail("优惠券未到抢购时间")));
            return false;
        }

        //满足秒杀时间判断,返回真
        return true;
    }
}
