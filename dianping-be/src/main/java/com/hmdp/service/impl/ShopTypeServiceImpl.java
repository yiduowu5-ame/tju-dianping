package com.hmdp.service.impl;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

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
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ShopTypeMapper shopTypeMapper;

    /**
     * 查询商铺列表
     * @return
     */
    @Override
    public Result queryShopTypeList() {
        String key = RedisConstants.SHOP_TYPE_KEY;

        //从redis中查询商品列表
        List<String> shopTypeJSONList = stringRedisTemplate.opsForList().range(key, 0, -1);

        //判断redis中是否存在该缓存
        if(shopTypeJSONList!= null && !shopTypeJSONList.isEmpty()) {
            //存在，直接返回
            List<ShopType> shopTypeList = new ArrayList<>();
            for(String shopTypeJSON : shopTypeJSONList) {
                //将JSON类型的数据转化为shopType
                ShopType shopType = JSONUtil.toBean(shopTypeJSON, ShopType.class);
                shopTypeList.add(shopType);
            }
            return Result.ok(shopTypeList);
        }

        //不存在，从数据库中查询(Mybatis-plus)
        List<ShopType> typeList = query().orderByAsc("sort").list();

        //判断数据库中是否存在
        if(typeList==null || typeList.isEmpty()) {
            return Result.fail("商铺列表不存在!");
        }

        //存在，存入redis并返回
        for(ShopType shopType : typeList) {
            stringRedisTemplate.opsForList().rightPushAll(key, JSONUtil.toJsonStr(shopType));
        }
        return Result.ok(typeList);
    }
}
