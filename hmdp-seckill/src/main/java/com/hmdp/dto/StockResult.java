package com.hmdp.dto;

import com.hmdp.enums.SeckillResultCode;
import lombok.Data;

/**
 * 库存扣减结果（封装 Lua 脚本返回值）
 */
@Data
public class StockResult {

    /** 操作结果码 */
    private SeckillResultCode code;

    private StockResult() {
    }

    /** 构建库存操作结果 */
    public static StockResult of(SeckillResultCode code) {
        StockResult result = new StockResult();
        result.code = code;
        return result;
    }

    /** 是否扣减成功 */
    public boolean isSuccess() {
        return SeckillResultCode.SUCCESS == this.code;
    }
}
