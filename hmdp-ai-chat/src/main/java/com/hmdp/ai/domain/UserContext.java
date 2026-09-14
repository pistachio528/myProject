package com.hmdp.ai.domain;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * 用户上下文信号
 * 用于意图消歧的多信号融合
 */
@Data
@Builder
public class UserContext {
    /** 用户 ID */
    private Long userId;
    /** 当前活跃会话 ID */
    private String sessionId;
    /** 最近交互的订单 token 列表（最多 5 条） */
    private List<String> recentOrderTokens;
    /** 最近交互的优惠券 ID 列表（最多 5 条） */
    private List<Long> recentVoucherIds;
    /** 当前页面上下文（前端传递，如 "voucher:123"） */
    private String pageContext;
    /** 最近一次交互时间戳（毫秒） */
    private Long lastInteractTime;
    /** 用户登录 token，用于调用秒杀系统接口时鉴权 */
    private String authToken;
}
