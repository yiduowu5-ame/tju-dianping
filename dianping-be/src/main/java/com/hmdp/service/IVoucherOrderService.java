package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 按id购买秒杀券
     * @param voucherId
     * @return
     */
    Result seckillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId, LocalDateTime now);
}
