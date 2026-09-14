package com.hmdp.infrastructure;

import com.hmdp.dto.SeckillResult;
import com.hmdp.enums.SeckillResultCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SeckillResultCode 和 SeckillResult 单元测试
 */
class SeckillResultCodeTest {

    @Test
    void fromCode_returns_correct_enum() {
        assertEquals(SeckillResultCode.SUCCESS, SeckillResultCode.fromCode(0));
        assertEquals(SeckillResultCode.ACTIVITY_NOT_FOUND, SeckillResultCode.fromCode(1));
        assertEquals(SeckillResultCode.ACTIVITY_NOT_STARTED, SeckillResultCode.fromCode(2));
        assertEquals(SeckillResultCode.ACTIVITY_ENDED, SeckillResultCode.fromCode(3));
        assertEquals(SeckillResultCode.STOCK_INSUFFICIENT, SeckillResultCode.fromCode(4));
        assertEquals(SeckillResultCode.STOCK_CACHE_MISS, SeckillResultCode.fromCode(5));
        assertEquals(SeckillResultCode.STOCK_NEGATIVE, SeckillResultCode.fromCode(6));
        assertEquals(SeckillResultCode.LIMIT_EXCEEDED, SeckillResultCode.fromCode(7));
        assertEquals(SeckillResultCode.SYSTEM_BUSY, SeckillResultCode.fromCode(8));
        assertEquals(SeckillResultCode.QUERY_TIMEOUT, SeckillResultCode.fromCode(9));
        assertEquals(SeckillResultCode.DUPLICATE_ORDER, SeckillResultCode.fromCode(10));
    }

    @Test
    void fromCode_unknown_returns_system_busy() {
        assertEquals(SeckillResultCode.SYSTEM_BUSY, SeckillResultCode.fromCode(999));
    }

    @Test
    void seckillResult_success_has_token() {
        SeckillResult result = SeckillResult.success("test-token");
        assertTrue(result.isSuccess());
        assertEquals(0, result.getCode());
        assertEquals("test-token", result.getToken());
        assertNotNull(result.getMessage());
    }

    @Test
    void seckillResult_fail_has_no_token() {
        SeckillResult result = SeckillResult.fail(SeckillResultCode.STOCK_INSUFFICIENT);
        assertFalse(result.isSuccess());
        assertEquals(4, result.getCode());
        assertNull(result.getToken());
        assertNotNull(result.getMessage());
    }
}
