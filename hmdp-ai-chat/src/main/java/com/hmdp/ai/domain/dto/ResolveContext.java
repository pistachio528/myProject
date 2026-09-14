package com.hmdp.ai.domain.dto;

import com.hmdp.ai.domain.ChatMessage;
import com.hmdp.ai.domain.UserContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 意图识别上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveContext {
    /** 对话历史 */
    private List<ChatMessage> history;
    /** 用户上下文信号 */
    private UserContext userContext;
    /** 当前页面信息（前端传递） */
    private String pageContext;
}
