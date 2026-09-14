package com.hmdp.ai.engine;

import com.hmdp.ai.domain.dto.IntentResult;
import com.hmdp.ai.domain.dto.ResolveContext;

/**
 * 意图识别与消歧引擎接口
 */
public interface IntentResolver {

    /**
     * 识别用户意图并进行消歧
     *
     * @param message 用户当前消息
     * @param context 对话上下文 + 用户上下文
     * @return 意图识别结果（含置信度、是否需要确认）
     */
    IntentResult resolve(String message, ResolveContext context);
}
