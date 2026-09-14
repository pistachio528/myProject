package com.hmdp.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.constant.SeckillRedisKey;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.mapper.SeckillVoucherMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 定时对账任务
 * <p>
 * 每 5 分钟执行一次，对比 Redis 库存与数据库库存，发现不一致时以数据库为准进行修正。
 * 作为最后一道防线，保证 Redis 库存与 DB 库存的最终一致性。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJob {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final SeckillVoucherMapper seckillVoucherMapper;

    /**
     * 对账主方法，每 5 分钟执行一次（fixedDelay 保证上次执行完毕后再计时）
     */
    @Scheduled(fixedDelay = 300000)
    public void reconcile() {
        // 整体 try-catch，任何异常都只记录日志，不影响下次调度
        try {
            // 1. 查询所有进行中及刚结束（10 分钟内）的秒杀活动
            // 加 10 分钟缓冲窗口，确保活动结束后仍有未处理完的消息时也能被对账覆盖
            List<SeckillVoucher> activeVouchers = seckillVoucherMapper.selectList(
                    new LambdaQueryWrapper<SeckillVoucher>()
                            .gt(SeckillVoucher::getEndTime, LocalDateTime.now().minusMinutes(10))
            );

            if (activeVouchers == null || activeVouchers.isEmpty()) {
                log.info("[ReconciliationJob] 当前无进行中的秒杀活动，跳过本次对账");
                return;
            }

            int totalCount = activeVouchers.size();   // 本次对账活动总数
            int inconsistentCount = 0;                // 发现不一致的数量

            // 2. 逐个 voucherId 进行对账
            for (SeckillVoucher voucher : activeVouchers) {
                Long voucherId = voucher.getId();
                String lockKey = SeckillRedisKey.reconciliationLock(voucherId);
                RLock lock = redissonClient.getLock(lockKey);

                // a. 尝试获取分布式锁（TTL=30s），获取失败说明其他实例正在对账，跳过
                boolean locked = false;
                try {
                    locked = lock.tryLock(0, 30, TimeUnit.SECONDS);
                    if (!locked) {
                        log.debug("[ReconciliationJob] voucherId={} 对账锁被占用，跳过", voucherId);
                        continue;
                    }

                    // b. 读取 Redis 库存
                    String stockKey = SeckillRedisKey.stock(voucherId);
                    String redisStockStr = stringRedisTemplate.opsForValue().get(stockKey);

                    if (redisStockStr == null) {
                        // Redis 中无库存 key，可能尚未预热，跳过
                        log.warn("[ReconciliationJob] voucherId={} Redis 库存 key 不存在，跳过", voucherId);
                        continue;
                    }

                    int redisStock = Integer.parseInt(redisStockStr);

                    // c. 读取 DB 库存（重新查询，确保拿到最新值）
                    SeckillVoucher latest = seckillVoucherMapper.selectById(voucherId);
                    if (latest == null) {
                        log.warn("[ReconciliationJob] voucherId={} 数据库记录不存在，跳过", voucherId);
                        continue;
                    }
                    int dbStock = latest.getStock();

                    // d. 比对差值
                    int diff = redisStock - dbStock;
                    if (diff == 0) {
                        // 一致，跳过
                        continue;
                    }

                    // 差值不为 0，以 DB 为准修正 Redis 库存
                    inconsistentCount++;
                    stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(dbStock));

                    // 记录修正日志：包含 voucherId、修正前 Redis 库存、修正后库存（DB 库存）、修正时间
                    log.warn("[ReconciliationJob] 库存不一致已修正 | voucherId={} | 修正前Redis库存={} | 修正后库存(DB)={} | 修正时间={}",
                            voucherId, redisStock, dbStock, LocalDateTime.now());

                } finally {
                    // e. 释放锁（仅当本实例持有锁时才释放）
                    if (locked && lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }

            // 3. 不一致数量 > 10 时触发告警
            if (inconsistentCount > 10) {
                log.error("[ReconciliationJob] 库存不一致数量超过阈值！本次对账共 {} 个活动，发现 {} 个不一致，请及时排查！",
                        totalCount, inconsistentCount);
            } else {
                log.info("[ReconciliationJob] 对账完成 | 本次对账共 {} 个活动，发现 {} 个不一致",
                        totalCount, inconsistentCount);
            }

        } catch (Exception e) {
            log.error("[ReconciliationJob] 对账任务异常，跳过本次", e);
        }
    }
}
