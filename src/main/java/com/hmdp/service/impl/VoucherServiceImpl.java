package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public @NotNull Result queryVoucherOfShop(Long shopId) {
        return Result.ok(
                getBaseMapper().queryVoucherOfShop(shopId));
    }

    @Override
    @Transactional
    public void addSeckillVoucher(@NotNull Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        val seckillVoucher =
                new SeckillVoucher()
                        .setVoucherId(voucher.getId())
                        .setStock(voucher.getStock())
                        .setBeginTime(voucher.getBeginTime())
                        .setEndTime(voucher.getEndTime());

        stringRedisTemplate
                .opsForValue()
                .set(SECKILL_STOCK_KEY + voucher.getId(), voucher.getStock().toString());
    }
}
