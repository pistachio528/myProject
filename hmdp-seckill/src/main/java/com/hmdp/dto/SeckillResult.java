package com.hmdp.dto;

import com.hmdp.enums.SeckillResultCode;
import lombok.Data;

/**
 * 秒杀接口统一响应结果
 * <p>
 * 主链路成功时返回 token，失败时返回错误码和提示信息。
 * 前端通过 token 轮询 /api/seckill/order/status/{token} 查询订单进度。
 * </p>
 */
@Data
public class SeckillResult {

    /** 结果码（对应 SeckillResultCode.code） */
    private int code;

    /** 排队 token（仅秒杀成功时有值，UUID 格式） */
    private String token;

    /** 提示信息 */
    private String message;

    private SeckillResult() {
    }

    /**
     * 秒杀成功，返回排队 token
     *
     * @param token UUID 格式的排队凭证
     */
    public static SeckillResult success(String token) {
        SeckillResult result = new SeckillResult();
        result.code = SeckillResultCode.SUCCESS.getCode();
        result.token = token;
        result.message = SeckillResultCode.SUCCESS.getMessage();
        return result;
    }

    /**
     * 秒杀失败，返回错误码和提示
     *
     * @param resultCode 错误码枚举
     */
    public static SeckillResult fail(SeckillResultCode resultCode) {
        SeckillResult result = new SeckillResult();
        result.code = resultCode.getCode();
        result.token = null;
        result.message = resultCode.getMessage();
        return result;
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return this.code == SeckillResultCode.SUCCESS.getCode();
    }
}
