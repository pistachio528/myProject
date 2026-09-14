package com.hmdp.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.constant.SeckillRedisKey;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.StockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 秒杀库存预热启动器
 * <p>
 * 服务启动后自动执行：
 * 1. 将所有有效活动的库存和活动信息预热到 Redis
 * 2. 将已购用户预热到 seckill:buyers:{voucherId} Set，防止 Redis 重启后限购失效
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillPreheatRunner implements ApplicationRunner {

    private final StockManager stockManager;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final VoucherOrderMapper voucherOrderMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillVoucher> activeVouchers = seckillVoucherMapper.selectList(
                new LambdaQueryWrapper<SeckillVoucher>()
                        .gt(SeckillVoucher::getEndTime, now)
        );

        if (activeVouchers == null || activeVouchers.isEmpty()) {
            log.info("[SeckillPreheatRunner] 无有效活动需要预热");
            return;
        }

        int successCount = 0;
        for (SeckillVoucher voucher : activeVouchers) {
            try {
                // 1. 预热库存和活动信息
                stockManager.preheatStock(voucher.getId());

                // 2. 预热已购用户到 buyers Set
                preheatBuyers(voucher.getId());

                successCount++;
            } catch (Exception e) {
                log.error("[SeckillPreheatRunner] 预热活动异常，voucherId={}，原因：{}",
                        voucher.getId(), e.getMessage(), e);
            }
        }
        log.info("[SeckillPreheatRunner] 批量预热完成，共 {} 个活动，成功 {} 个",
                activeVouchers.size(), successCount);
    }

    /**
     * 从数据库查询已购用户，预热到 Redis Set
     */
    private void preheatBuyers(Long voucherId) {
        List<VoucherOrder> orders = voucherOrderMapper.selectList(
                new LambdaQueryWrapper<VoucherOrder>()
                        .eq(VoucherOrder::getVoucherId, voucherId)
                        .eq(VoucherOrder::getStatus, 2)
                        .select(VoucherOrder::getUserId)
        );
        if (orders == null || orders.isEmpty()) {
            log.info("[SeckillPreheatRunner] 活动 {} 暂无已购用户，跳过 buyers 预热", voucherId);
            return;
        }
        String key = SeckillRedisKey.buyers(voucherId);
        String[] userIds = orders.stream()
                .map(o -> String.valueOf(o.getUserId()))
                .toArray(String[]::new);
        stringRedisTemplate.opsForSet().add(key, userIds);
        log.info("[SeckillPreheatRunner] 活动 {} buyers 预热完成，共 {} 个已购用户", voucherId, userIds.length);
    }
}
