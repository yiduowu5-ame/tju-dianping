package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class RedisLock implements ILock{

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public RedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";

    /**
     * 尝试获取锁
     * @param timeOutSec
     * @return
     */
    @Override
    public boolean tryLock(Long timeOutSec) {
        //set lock thread1 nx ex 10
        String key = KEY_PREFIX+name;

        //获取当前线程标识
        String value = ID_PREFIX+Thread.currentThread().getId();

        //存入redis
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeOutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        //获取当前线程标识
        String threadId = ID_PREFIX+Thread.currentThread().getId();

        //从redis中取得线程标识
        String value = stringRedisTemplate.opsForValue().get(KEY_PREFIX+name);

        //判断是否一致
        if(!threadId.equals(value)){
            //不一致

        }
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }

}
