package com.hmdp.dto;

import com.hmdp.enums.SeckillResultCode;
import lombok.Data;

/**
 * 限购策略校验结果
 */
@Data
public class LimitResult {

    /** 是否通过限购校验 */
    private boolean passed;

    /** 未通过时的错误码 */
    private SeckillResultCode code;

    private LimitResult() {
    }

    /** 通过限购校验 */
    public static LimitResult pass() {
        LimitResult result = new LimitResult();
        result.passed = true;
        result.code = SeckillResultCode.SUCCESS;
        return result;
    }

    /** 未通过限购校验 */
    public static LimitResult reject(SeckillResultCode code) {
        LimitResult result = new LimitResult();
        result.passed = false;
        result.code = code;
        return result;
    }
}
