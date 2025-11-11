package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(JSONUtil.toJsonStr(value));
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData), time, unit);
    }

    public <R,ID> R queryWithId(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbfallback,Long time, TimeUnit unit){
        //1提交id数据
        String key = keyPrefix + id;

        //2从redis中查询查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //3判断缓存是否命中
        if(StrUtil.isBlank(json)){
            //4未命中，可能是空，也可能是过期删除
            //4.1查询数据库
            return queryDbAndSetRedis(id, (Function<ID, R>) dbfallback, time, unit, key);
        }

        //命中空字符串
        if(json.isEmpty()){//相当于json==""
            return null;
        }

        //5.命中，判断缓存是否过期
        RedisData redisData = null;
        try {
            redisData = JSONUtil.toBean(json, RedisData.class);
        } catch (Exception e) {
            // 解析失败（可能是数据结构不一致），将其视为缓存失效
            log.warn("解析缓存为 RedisData 失败,将视为未命中并尝试重建缓存");
        }

        // 如果解析失败或 redisData 为 null，把它当作未命中处理（避免 NPE）
        if (redisData != null && redisData.getData() == null && redisData.getExpireTime() == null) {
            // 这里直接异步或者同步去重建缓存，下面示例同步查询并写入（也可以异步）
            return queryDbAndSetRedis(id, (Function<ID, R>) dbfallback, time, unit, key);
        }

        R r;
        try {
            String dataJson = null;
            if (redisData != null) {
                dataJson = JSONUtil.toJsonStr(redisData.getData());
            }
            r = JSONUtil.toBean(dataJson, type);
        } catch (Exception e) {
            log.error("解析数据对象失败，尝试重建缓存", e);
            // 解析失败，重建缓存
            r = dbfallback.apply(id);
            if (r == null) {
                stringRedisTemplate.opsForValue()
                        .set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            setWithLogicalExpire(key, r, time, unit);
            return r;
        }

        LocalDateTime expireTime = null;
          if (redisData != null) {
            expireTime = redisData.getExpireTime();
        }

        //5.1未过期，返回商铺信息
        if (expireTime != null && expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }

        //5.2已过期，尝试获取互斥锁
        //6.判断是否获取了锁
        String lockKey=RedisConstants.LOCK_SHOP_KEY+ id;
        Boolean isLock = tryLock(lockKey);

        if(isLock){
            //6.1是，返回，且需要开启独立线程
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                //6.2通过新线程开始缓存重建，加入新数据
                try {
                    R newData = dbfallback.apply(id);
                    this.setWithLogicalExpire(key,newData,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    releaseLock(lockKey);
                }
            });
        }

        //6.3否，返回商铺信息(旧数据)
        return r;
    }

    private <R, ID> R queryDbAndSetRedis(ID id, Function<ID, R> dbfallback, Long time, TimeUnit unit, String key) {
        R r = dbfallback.apply(id);
        if (r == null) {
            stringRedisTemplate.opsForValue()
                    .set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        setWithLogicalExpire(key,r,time,unit);
        return r;
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
