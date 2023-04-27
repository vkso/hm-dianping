package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//        Shop shop = queryWithPassThrough(id);

        // 互斥锁解决缓存击穿
        Shop shop = queryWithMutex(id);
        if (null == shop) {
            return Result.fail("店铺不存在！");
        }

        // 返回
        return Result.ok(shop);
    }

    public Shop queryWithMutex(Long id) {
        // 1. 从 Redis 查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断缓存是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断命中是否为空值
        if (null != shopJson) {
            return null;
        }
        // 实现缓存重建
        // 获取互斥锁
        String lockKey = "lock:shop:" + id;
        Shop shop;
        try {
            boolean isLock = tryLock(lockKey);
            // 判断是否获取成功
            if (!isLock) {
                Thread.sleep(50);
                queryWithMutex(id);
            }
            // 如果失败，则休眠并重试
            // 如果成功，根据 id 查询数据库

            // 获取到锁之后，根据 id 查询数据库
            shop = getById(id);

            // 4. 不存在，返回错误
            if (null == shop) {
                // 将空值写入 Redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 5. 存在，写入 Redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e){
            throw new RuntimeException(e);
        } finally {
            // 释放互斥锁
            unlock(lockKey);
        }

        // 返回
        return shop;
    }

    public Shop queryWithPassThrough(Long id) throws InterruptedException {
        // 1. 从 Redis 查询商铺缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 判断缓存是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3. 存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断命中是否为空值
        if (null != shopJson) {
            return null;
        }
        // 不存在，根据 id 查询数据库
        Shop shop = getById(id);
        // 模拟重建缓存的延时
        Thread.sleep(200);

        // 4. 不存在，返回错误
        if (null == shop) {
            // 将空值写入 Redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 5. 存在，写入 Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (null == id) {
            return Result.fail("店铺 id 不能为空");
        }
        // 1. 更新数据库
        updateById(shop);
        // 2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }
}
