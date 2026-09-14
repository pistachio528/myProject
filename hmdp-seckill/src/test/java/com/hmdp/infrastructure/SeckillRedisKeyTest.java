package com.hmdp.infrastructure;

import com.hmdp.constant.SeckillRedisKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SeckillRedisKey 单元测试
 * 验证 key 格式化方法的正确性
 */
class SeckillRedisKeyTest {

    @Test
    void stock_key_format() {
        assertEquals("seckill:stock:1001", SeckillRedisKey.stock(1001L));
    }

    @Test
    void activity_key_format() {
        assertEquals("seckill:activity:1001", SeckillRedisKey.activity(1001L));
    }

    @Test
    void lock_key_format() {
        assertEquals("seckill:lock:2001:1001", SeckillRedisKey.lock(2001L, 1001L));
    }

    @Test
    void idempotent_key_format() {
        assertEquals("seckill:idempotent:2001:1001", SeckillRedisKey.idempotent(2001L, 1001L));
    }

    @Test
    void limitDaily_key_format() {
        assertEquals("seckill:limit:1001:2024-01-15",
                SeckillRedisKey.limitDaily(1001L, "2024-01-15"));
    }

    @Test
    void limitCount_key_format() {
        assertEquals("seckill:limit:count:2001:1001", SeckillRedisKey.limitCount(2001L, 1001L));
    }

    @Test
    void orderStatus_key_format() {
        String token = "abc123";
        assertEquals("seckill:order:status:abc123", SeckillRedisKey.orderStatus(token));
    }

    @Test
    void reconciliationLock_key_format() {
        assertEquals("reconciliation:lock:1001", SeckillRedisKey.reconciliationLock(1001L));
    }

    @Test
    void all_prefixes_are_distinct() {
        // 确保各前缀不会产生 key 冲突
        String[] prefixes = {
            SeckillRedisKey.STOCK,
            SeckillRedisKey.ACTIVITY,
            SeckillRedisKey.LOCK,
            SeckillRedisKey.IDEMPOTENT,
            SeckillRedisKey.LIMIT_DAILY,
            SeckillRedisKey.LIMIT_COUNT,
            SeckillRedisKey.ORDER_STATUS,
            SeckillRedisKey.RECONCILIATION_LOCK
        };
        // 验证前缀两两不同
        for (int i = 0; i < prefixes.length; i++) {
            for (int j = i + 1; j < prefixes.length; j++) {
                assertNotEquals(prefixes[i], prefixes[j],
                        "前缀冲突：" + prefixes[i] + " == " + prefixes[j]);
            }
        }
    }
}
