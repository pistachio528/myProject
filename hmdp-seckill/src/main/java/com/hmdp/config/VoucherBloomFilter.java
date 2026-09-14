package com.hmdp.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.mapper.SeckillVoucherMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * 优惠券布隆过滤器
 * <p>
 * 防缓存穿透第一道防线：在请求到达 Redis 和 DB 之前，先用布隆过滤器判断
 * voucherId 是否可能存在。不存在则直接拒绝，避免无效查询打穿数据库。
 *
 * <b>布隆过滤器特性：</b>
 * - 判断"不存在"：100% 准确，一定不存在
 * - 判断"存在"：有一定误判率（本实现设为 0.01%），极少数不存在的 ID 会被放行
 *   → 这部分由缓存空值兜底处理
 *
 * <b>局限性：</b>
 * 布隆过滤器不支持删除，新增活动需要调用 {@link #add(Long)} 手动加入。
 * 服务启动时通过 {@link #init()} 从 DB 全量加载一次。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherBloomFilter {

    private final SeckillVoucherMapper seckillVoucherMapper;

    /**
     * 布隆过滤器实例
     * 预期元素数量：100万（秒杀活动 ID 空间）
     * 误判率：0.01%（万分之一）
     */
    private BloomFilter<Long> bloomFilter;

    /**
     * 服务启动时从 DB 全量加载所有 voucherId 到布隆过滤器
     */
    @PostConstruct
    public void init() {
        // 预期最多 100 万个活动 ID，误判率 0.01%
        bloomFilter = BloomFilter.create(Funnels.longFunnel(), 1_000_000, 0.0001);

        List<SeckillVoucher> vouchers = seckillVoucherMapper.selectList(
                new LambdaQueryWrapper<SeckillVoucher>()
                        .select(SeckillVoucher::getId)
        );

        if (vouchers != null) {
            vouchers.forEach(v -> bloomFilter.put(v.getId()));
            log.info("[VoucherBloomFilter] 初始化完成，加载 {} 个 voucherId", vouchers.size());
        }
    }

    /**
     * 判断 voucherId 是否可能存在
     *
     * @return false = 一定不存在（直接拒绝）；true = 可能存在（继续查询）
     */
    public boolean mightContain(Long voucherId) {
        return bloomFilter.mightContain(voucherId);
    }

    /**
     * 新增活动时，将 voucherId 加入布隆过滤器
     * 在创建秒杀活动的业务逻辑中调用
     */
    public void add(Long voucherId) {
        bloomFilter.put(voucherId);
        log.debug("[VoucherBloomFilter] 新增 voucherId={}", voucherId);
    }
}
