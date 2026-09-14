package com.hmdp.ai.engine;

import com.hmdp.ai.domain.dto.LLMRequest;
import com.hmdp.ai.domain.dto.LLMResponse;
import reactor.core.publisher.Flux;

/**
 * LLM 调用引擎接口
 * 支持流式输出（SSE）和同步调用两种模式
 */
public interface LLMEngine {

    /**
     * 流式调用 LLM，返回 token 流
     * 用于 SSE 实时推送给前端
     *
     * @param request LLM 请求（stream 会被强制设为 true）
     * @return token 字符串流，每个元素为一个 token 片段
     */
    Flux<String> chatStream(LLMRequest request);

    /**
     * 同步调用 LLM，等待完整响应
     * 用于意图识别、相关性评估等内部场景
     *
     * @param request LLM 请求
     * @return 完整响应
     */
    LLMResponse chatSync(LLMRequest request);

    /**
     * 评估回复与原始问题的相关性分数
     * 用于 QualityGuard 回复质量校验
     *
     * @param question 用户原始问题
     * @param answer   LLM 生成的回复
     * @return 相关性分数 0.0 ~ 1.0
     */
    double evaluateRelevance(String question, String answer);
}
