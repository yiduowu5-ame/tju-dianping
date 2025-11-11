package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.RedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private IVoucherOrderService proxy;

    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Resource
    private ISeckillVoucherService  seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {

        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(voucher == null){
            return Result.fail("优惠券不存在!");
        }

        //1判断是否开始秒杀
        LocalDateTime now = LocalDateTime.now();
        if(now.isBefore(voucher.getBeginTime())||now.isAfter(voucher.getEndTime())){
            //1.1否，返回错误结果
            return Result.fail("不在活动时间内！");
        }

        //2是，判断库存是否充足
        Integer stock = voucher.getStock();
        if(stock<=0){
            //2.1否，返回错误结果
            return Result.fail("该优惠券库存不足！");
        }

        Long userId = UserHolder.getUser().getId();

        //创建锁对象
        RedisLock redisLock = new RedisLock("order" + userId, stringRedisTemplate);

        //获取锁
        boolean isLocked = redisLock.tryLock(1000L);

        if (!isLocked) {
            //获取锁失败，返回错误
            return Result.fail("不允许重复下单！");
        }

        //获取锁成功
        try{
            return proxy.createVoucherOrder(voucherId, now);
        }finally {
            redisLock.unlock();
        }

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId, LocalDateTime now) {
            //2.2库存充足，判断该用户是否已经购买过（一人一单）
            Long userId = UserHolder.getUser().getId();

            //这里使用toString并利用intern使得一个id对应的锁是一样的
            int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

            if(count>0){
                //用户已经购买过了
                return Result.fail("您已经购买过了！");
            }
            //3.扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1 ")
                    .eq("voucher_id", voucherId).gt("stock", 0)//where id = ? and stock = ?  CAS法解决线程安全问题
                    .update();

            if(!success){
                return Result.fail("扣除失败！");
            }

            //4.创建订单
            VoucherOrder  voucherOrder = new VoucherOrder();
            voucherOrder.setVoucherId(voucherId);

            long orderId = redisIdWorker.nextId("voucher_order");
            voucherOrder.setId(orderId);

            voucherOrder.setUserId(UserHolder.getUser().getId());
            voucherOrder.setCreateTime(now);

            save(voucherOrder);

            //5.返回订单id
            return Result.ok(orderId);
    }
}
