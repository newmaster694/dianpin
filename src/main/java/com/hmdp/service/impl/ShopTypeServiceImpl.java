package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private RedisTemplate<String, ShopType> redisTemplate;

    @Override
    public Result<List<ShopType>> queryTypeList() {
        String key = "cache:shop-type";

        //1.在Redis中查询缓存
        List<ShopType> cacheTypeList = redisTemplate.opsForList().range(key, 0, -1);

        //2.缓存存在,直接返回结果
        if (!(cacheTypeList == null || cacheTypeList.isEmpty())) {
            return Result.ok(cacheTypeList);
        }

        //3.缓存不存在,查询数据库
        LambdaQueryWrapper<ShopType> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(ShopType::getSort);
        List<ShopType> typeList = this.list(queryWrapper);

        //4.数据库不存在,返回错误结果
        if (typeList == null) {
            return Result.fail("店铺类型不存在");
        }

        //5.数据库存在,将数据库的查询结果保存到Redis中
        for(ShopType i : typeList) {
            redisTemplate.opsForList().rightPush(key, i);
        }

        redisTemplate.expire(key, 30L, TimeUnit.MINUTES);

        //6.返回数据库的查询结果
        return Result.ok(typeList);
    }
}
