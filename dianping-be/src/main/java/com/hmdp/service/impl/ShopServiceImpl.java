package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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

    /**
     * 通过id查询店铺
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //使用添加空缓存的方法改造查询，防止缓存穿透
        //尝试通过id在redis里查询
        String key = RedisConstants.SHOP_TYPE_KEY+id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //判断是否存在（缓存命中）
        if(StrUtil.isNotBlank(shopJson)){
            //如果命中，且不是空值，返回商铺信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }

        //如果存在，且是空值，返回一个null
        if(shopJson!=null){//相当于shopJson==""
            return Result.fail("店铺信息为空！");
        }

        //未命中，通过id查询数据库
        Shop shop = getById(id);

        //在数据库中查询商品是否存在
        if(shop==null){
            //不存在，将空值存入redis
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return Result.fail("店铺不存在！");
        }

        //存在，将商铺信息存入redis
        String newShopJson = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,newShopJson, RedisConstants.CACHE_SHOP_TTL,TimeUnit.MINUTES);

        return Result.ok(shop);
    }

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
}
