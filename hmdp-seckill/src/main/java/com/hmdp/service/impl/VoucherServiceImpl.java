package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.VoucherBloomFilter;
import com.hmdp.constant.SeckillRedisKey;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.service.VoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 优惠券服务实现
 * <p>
 * 缓存穿透双重防护：
 * 1. 布隆过滤器（第一道）：voucherId 不在过滤器中，直接返回 null，不查 Redis 和 DB
 * 2. 缓存空值（第二道）：DB 查不到时，在 Redis 写入空值标记（TTL=2min），
 *    后续相同 ID 的请求命中空值缓存直接返回，不再穿透到 DB
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher>
        implements VoucherService {

    /** 空值缓存标记，存入 Redis 的占位符 */
    private static final String NULL_VALUE = "NULL";

    /** 空值缓存 TTL：2 分钟（不能太长，避免真实数据新增后长时间不可见） */
    private static final long NULL_CACHE_TTL_MINUTES = 2;

    private final VoucherBloomFilter voucherBloomFilter;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public List<SeckillVoucher> listByShopId(Long shopId) {
        // 列表查询不走布隆过滤器（返回全量，不存在穿透问题）
        return list();
    }

    /**
     * 按 voucherId 查询单个优惠券，带缓存穿透防护
     *
     * <pre>
     * 请求
     *  │
     *  ├─ 1. 布隆过滤器：mightContain = false → 直接返回 null（一定不存在）
     *  │
     *  ├─ 2. Redis 空值缓存：命中 "NULL" → 直接返回 null（短期内已确认不存在）
     *  │
     *  ├─ 3. 查 DB：
     *  │      ├─ 查到 → 返回结果
     *  │      └─ 查不到 → 写空值缓存（TTL=2min）→ 返回 null
     * </pre>
     *
     * @param voucherId 优惠券 ID
     * @return 优惠券实体，不存在时返回 null
     */
    @Override
    public SeckillVoucher getByIdWithCacheProtection(Long voucherId) {
        // ① 布隆过滤器：快速判断 ID 是否可能存在
        if (!voucherBloomFilter.mightContain(voucherId)) {
            log.debug("[VoucherService] 布隆过滤器拦截，voucherId={} 一定不存在", voucherId);
            return null;
        }

        // ② 检查空值缓存（布隆过滤器误判漏过来的不存在 ID，或短期内已确认不存在的 ID）
        String nullKey = SeckillRedisKey.nullVoucher(voucherId);
        String nullMark = stringRedisTemplate.opsForValue().get(nullKey);
        if (NULL_VALUE.equals(nullMark)) {
            log.debug("[VoucherService] 命中空值缓存，voucherId={} 不存在", voucherId);
            return null;
        }

        // ③ 查询数据库
        SeckillVoucher voucher = getById(voucherId);
        if (voucher == null) {
            // DB 也查不到，写入空值缓存，TTL=2min，防止后续请求继续穿透
            stringRedisTemplate.opsForValue().set(nullKey, NULL_VALUE,
                    NULL_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.warn("[VoucherService] DB 查不到 voucherId={}，已写入空值缓存", voucherId);
            return null;
        }

        return voucher;
    }
}
