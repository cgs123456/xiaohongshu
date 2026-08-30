package com.itcast.service.impl;

import com.itcast.context.UserContext;
import com.itcast.mapper.CouponMapper;
import com.itcast.mapper.UserCouponMapper;
import com.itcast.model.pojo.Coupon;
import com.itcast.model.vo.CouponVo;
import com.itcast.result.Result;
import com.itcast.service.CouponService;
import com.itcast.util.IsExpireUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CouponServiceImpl implements CouponService {

    @Autowired
    private UserCouponMapper userCouponMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private CouponMapper couponMapper;

    @Override
    public Result<List<CouponVo>> getCouponsByUserId() {
        List<CouponVo> couponsByUserId = getCouponsByUserId(UserContext.getUserId());
        return Result.success(couponsByUserId);
    }

    public List<CouponVo> getCouponsByUserId(Integer userId) {
        // 根据用户 id 查找到用户可用的优惠券
        List<Coupon> couponsByUserId = userCouponMapper.getCouponsByUserId(userId);

        return couponsByUserId.stream().map(coupon -> {
            CouponVo couponVo = new CouponVo();
            couponVo.setCoupon(coupon);

            // 判断是否可用（是否过期，是否有库存）
            // 修复：isExpire返回true表示已过期，需要取反
            boolean isUsable = coupon.getStock() > 0 && !IsExpireUtil.isExpire(coupon.getStartTime(), coupon.getEndTime());
            couponVo.setIsUsable(isUsable);
            return couponVo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> useCoupon(Long couponId) {
        useCoupon(couponId, UserContext.getUserId());
        return Result.success(null);
    }

    public void useCoupon(Long couponId, Integer userId) {
        RLock lock = redissonClient.getLock("useCoupon:" + couponId);
        boolean res = false;
        try {
            res = lock.tryLock(3, TimeUnit.SECONDS);
            if (!res) {
                // 获取锁失败时返回失败结果
                throw new RuntimeException("获取优惠券锁失败，请稍后重试");
            }
            // 1.条件更新 rb_user_coupon：仅当用户持有且未使用时核销，影响行数为0表示不满足条件
            int updated = userCouponMapper.markUsed(userId, couponId.intValue());
            if (updated == 0) {
                throw new RuntimeException("优惠券不存在、已使用或不可用");
            }
            // 2.原子扣减 rb_coupon 库存（条件 UPDATE，影响行数为0表示库存不足，事务回滚）
            int rows = couponMapper.deductStock(couponId);
            if (rows == 0) {
                log.error("优惠券库存不足，couponId={} userId={}", couponId, userId);
                throw new RuntimeException("优惠券库存不足");
            }
            log.info("优惠券使用成功，couponId={} userId={}", couponId, userId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取锁时发生异常", e);
            throw new RuntimeException("获取优惠券锁时线程被中断", e);
        } finally {
            // 只有在成功获取锁时才释放锁
            if (res) {
                lock.unlock();
            }
        }
    }

    @Override
    public Result<Void> insertCoupon(Coupon coupon) {
        couponMapper.insert(coupon);
        return Result.success(null);
    }
}
