package com.hmdp.ai.domain.vo;

import lombok.Builder;
import lombok.Data;

/**
 * SSE 事件数据结构
 * 对应设计文档中的 5 种事件类型：token / function / card / done / error
 */
@Data
@Builder
public class SseEventVO {

    /** 事件类型 */
    public enum EventType {
        TOKEN,      // 逐 token 推送
        FUNCTION,   // 工具调用状态
        CARD,       // 结构化卡片数据
        DONE,       // 完成信号
        ERROR       // 错误
    }

    private EventType type;
    /** token 事件：token 内容 */
    private String content;
    /** function 事件：工具名称 */
    private String functionName;
    /** function 事件：调用状态（calling / done / error） */
    private String functionStatus;
    /** card 事件：卡片类型（order / voucher / activity） */
    private String cardType;
    /** card 事件：卡片数据（JSON 对象） */
    private Object cardData;
    /** done 事件：消息 ID */
    private String messageId;
    /** done 事件：token 用量 */
    private UsageInfo usage;
    /** error 事件：错误码 */
    private String errorCode;
    /** error 事件：错误信息 */
    private String errorMessage;

    @Data
    @Builder
    public static class UsageInfo {
        private Integer promptTokens;
        private Integer completionTokens;
    }
}
