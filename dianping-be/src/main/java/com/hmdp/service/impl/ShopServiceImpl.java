package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private CacheClient cacheClient;

    /**
     * 定义一个线程池
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 通过id查询店铺
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id){
        Shop shop = cacheClient.queryWithId(
                RedisConstants.CACHE_SHOP_KEY,id,Shop.class,this::getById,RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES
        );

        if(shop == null){
            return Result.fail("店铺不存在!");
        }
        return Result.ok(shop);
    }
//    @Override
//    public Result queryById(Long id) {
//        //1提交id数据
//        String key = RedisConstants.CACHE_SHOP_KEY+id;
//
//        //2从redis中查询查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        //3判断缓存是否命中
//        if(StrUtil.isBlank(shopJson)){
//            //4未命中，可能是空，也可能是过期删除
//            //4.1查询数据库
//            Shop shop = getById(id);
//            if(shop==null){
//                //4.2如果数据库中不存在该商铺信息，防止缓存穿透，将空值存入redis
//                stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
//                return Result.fail("店铺不存在！");
//            }
//            //4.3 数据库中存在该商铺信息，将商铺信息存入redis中,（带逻辑过期时间）
//            saveData2Redis(id,30L);
//
//            return Result.ok(shop);
//        }
//
//        //命中空字符串
//        if(shopJson.isEmpty()){//相当于shopJson==""
//            return Result.fail("店铺信息为空！");
//        }
//
//        //5.命中，判断缓存是否过期
//        RedisData redisData = null;
//        try {
//            redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        } catch (Exception e) {
//            // 解析失败（可能是数据结构不一致），将其视为缓存失效
//            log.warn("解析缓存为 RedisData 失败，key={}，value={}，将视为未命中并尝试重建缓存");
//        }
//
//        // 如果解析失败或 redisData 为 null，把它当作未命中处理（避免 NPE）
//        if (redisData != null && redisData.getData() == null && redisData.getExpireTime() == null) {
//            // 这里直接异步或者同步去重建缓存，下面示例同步查询并写入（也可以异步）
//            Shop shop = getById(id);
//            if (shop == null) {
//                stringRedisTemplate.opsForValue()
//                        .set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return Result.fail("店铺不存在！");
//            }
//            saveData2Redis(id, 30L);
//            return Result.ok(shop);
//        }
//
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//
//        LocalDateTime expireTime = redisData.getExpireTime();
//
//        //5.1未过期，返回商铺信息
//        if(expireTime.isAfter(LocalDateTime.now())){
//            return Result.ok(shop);
//        }
//
//        //5.2已过期，尝试获取互斥锁
//        //6.判断是否获取了锁
//        String lockKey=RedisConstants.LOCK_SHOP_KEY+ id;
//        Boolean isLock = tryLock(lockKey);
//
//        if(isLock){
//            //6.1是，返回，且需要开启独立线程
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                //6.2通过新线程开始缓存重建，加入新数据
//                try {
//                    this.saveData2Redis(id,30L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    //释放锁
//                    releaseLock(lockKey);
//                }
//            });
//        }
//
//        //6.3否，返回商铺信息(旧数据)
//        return Result.ok(shop);
//    }


    /**
     * 更新店铺信息
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        //通过shop获取id
        Long id = shop.getId();

        if(id==null){
            return Result.fail("店铺id为空！");
        }

        //先更新数据库
        shopMapper.updateById(shop);

        //删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok(shop);
    }

    /**
     * 尝试获取互斥锁
     * @param key
     * @return
     */
    private Boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);

    }

    /**
     * 释放互斥锁
     */
    private void releaseLock(String key){
        stringRedisTemplate.delete(key);
    }
}
