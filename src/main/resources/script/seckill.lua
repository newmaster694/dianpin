---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by newmaster.
--- DateTime: 2024/6/30 下午2:24
---

-- 参数列表
-- 1.1优惠券id
local voucherId = ARGV[1]

-- 1.2用户id
local userId = ARGV[2]

-- 1.3订单ID
local orderId = ARGV[3]

-- 2.数据key
-- 获取Hash表中的秒杀优惠卷信息
local voucherInfoKey = 'seckill:voucher:' .. voucherId
-- 2.2.订单key
local orderKey = 'seckill:order:' .. voucherId

-- 获取库存
local stock = redis.call('hget', voucherInfoKey, 'stock')

-- 获取秒杀时间信息
local beginTime = redis.call('hget', voucherInfoKey, 'beginTime')
local endTime = redis.call('hget', voucherInfoKey, 'endTime')

-- 3.脚本业务
-- 当前时间戳（秒）
local currentTime = redis.call('time')[1]

-- 检查秒杀时间
if tonumber(beginTime) > tonumber(currentTime) or tonumber(endTime) < tonumber(currentTime) then
    return 1 -- 时间不符合条件
end

-- 3.1.判断库存是否充足 get stockKey,tonumber关键字用于将返回的字符串类型转换为数值类型
if(tonumber(stock) <= 0) then
    -- 3.2.库存不足，返回1
    return 2
end

-- 3.2判断一人一单业务 SISMEMBER orderKey userId
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 存在,说明是重复下单,不允许,返回2
    return 3
end

-- 3.3扣除库存
redis.call('hincrby', voucherInfoKey, 'stock', -1)

-- 3.4下单(保存用户)
redis.call('sadd', orderKey, userId)

-- 3.5 发送消息到消息队列中 XADD stream.orders * k1 v1 k2 v2 ...
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)

return 0
