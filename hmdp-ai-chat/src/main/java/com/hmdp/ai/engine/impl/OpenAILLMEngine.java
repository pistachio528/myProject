package com.hmdp.ai.engine.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hmdp.ai.config.AiProperties;
import com.hmdp.ai.domain.ChatMessage;
import com.hmdp.ai.domain.dto.LLMRequest;
import com.hmdp.ai.domain.dto.LLMResponse;
import com.hmdp.ai.engine.LLMEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

/**
 * OpenAI 兼容 API 的 LLMEngine 实现
 * 支持流式（SSE）和同步两种调用模式，以及相关性评估
 */
@Slf4j
@Component
public class OpenAILLMEngine implements LLMEngine {

    /** 客服角色 System Prompt */
    public static final String CUSTOMER_SERVICE_SYSTEM_PROMPT =
            "你是黑马点评电商秒杀平台的智能客服助手。\n\n" +
            "【回答规则】\n" +
            "1. 对于实时数据类问题（订单状态、订单列表、库存、秒杀活动），必须调用对应工具获取真实数据后回答，不得猜测或编造。\n" +
            "2. 对于知识类问题（规则说明、使用帮助、平台介绍等），基于系统提供的【知识库内容】回答；知识库中没有相关内容时，回答：\"抱歉，暂无该问题的相关资料，建议联系人工客服。\"\n" +
            "3. 对于与平台业务完全无关的问题，礼貌告知只能回答平台相关问题。\n\n" +
            "【工具调用规则】\n" +
            "1. user_id 由系统自动提供，绝对不要询问用户的用户ID。\n" +
            "2. 查询订单、优惠券、库存时，直接调用对应工具，不要要求用户提供系统能自动获取的信息。\n" +
            "3. 只有在用户没有提供优惠券名称/ID且无法从上下文推断时，才询问具体的优惠券信息。\n\n" +
            "回答时请简洁、准确、友好，直接给出答案，不要重复用户的问题。";

    /** 兜底回复 */
    private static final String FALLBACK_REPLY = "抱歉，我暂时无法回答您的问题，请稍后再试或联系人工客服。";

    private final WebClient llmWebClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public OpenAILLMEngine(@Qualifier("llmWebClient") WebClient llmWebClient,
                           AiProperties aiProperties,
                           ObjectMapper objectMapper) {
        this.llmWebClient = llmWebClient;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<String> chatStream(LLMRequest request) {
        try {
            ObjectNode body = buildRequestBody(request, true);

            return llmWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofSeconds(aiProperties.getLlm().getTimeoutSeconds()))
                    .filter(line -> line.startsWith("data: ") && !line.contains("[DONE]"))
                    .map(line -> line.substring("data: ".length()).trim())
                    .flatMap(data -> {
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            JsonNode content = node.path("choices").path(0).path("delta").path("content");
                            if (!content.isMissingNode() && !content.isNull()) {
                                return Flux.just(content.asText());
                            }
                            return Flux.<String>empty();
                        } catch (Exception e) {
                            return Flux.<String>empty();
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("LLM stream error: {}", e.getMessage(), e);
                        return Flux.just(FALLBACK_REPLY);
                    });
        } catch (Exception e) {
            log.error("LLM stream build error: {}", e.getMessage(), e);
            return Flux.just(FALLBACK_REPLY);
        }
    }

    @Override
    public LLMResponse chatSync(LLMRequest request) {
        try {
            ObjectNode body = buildRequestBody(request, false);

            String responseJson = llmWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(aiProperties.getLlm().getTimeoutSeconds()))
                    .block();

            return parseResponse(responseJson);
        } catch (Exception e) {
            log.error("LLM sync call error: {}", e.getMessage(), e);
            return LLMResponse.builder()
                    .content(FALLBACK_REPLY)
                    .finishReason("error")
                    .build();
        }
    }

    @Override
    public double evaluateRelevance(String question, String answer) {
        String evalPrompt = String.format(
                "请评估以下回复与问题的相关性，只输出一个0.0到1.0之间的小数，不要输出其他内容。\n\n" +
                "问题：%s\n\n回复：%s\n\n相关性分数（0.0=完全无关，1.0=完全相关）：",
                question, answer);

        LLMRequest evalRequest = LLMRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("user").content(evalPrompt).build()
                ))
                .temperature(0.0)
                .maxTokens(10)
                .build();

        try {
            LLMResponse response = chatSync(evalRequest);
            String scoreStr = response.getContent().trim();
            double score = Double.parseDouble(scoreStr);
            return Math.max(0.0, Math.min(1.0, score));
        } catch (Exception e) {
            log.warn("Failed to parse relevance score, defaulting to 0.5: {}", e.getMessage());
            return 0.5;
        }
    }

    // ---- 私有辅助方法 ----

    private ObjectNode buildRequestBody(LLMRequest request, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", aiProperties.getLlm().getModel());
        body.put("temperature", request.getTemperature() != null
                ? request.getTemperature() : aiProperties.getLlm().getTemperature());
        body.put("max_tokens", request.getMaxTokens() != null
                ? request.getMaxTokens() : aiProperties.getLlm().getMaxTokens());
        body.put("stream", stream);

        // 构建 messages 数组
        ArrayNode messages = objectMapper.createArrayNode();
        for (ChatMessage msg : request.getMessages()) {
            ObjectNode msgNode = objectMapper.createObjectNode();
            msgNode.put("role", msg.getRole());
            msgNode.put("content", msg.getContent() != null ? msg.getContent() : "");
            messages.add(msgNode);
        }
        body.set("messages", messages);

        // 构建 tools 数组（Function Calling）
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.set("tools", objectMapper.valueToTree(request.getTools()));
            body.put("tool_choice", "auto");
        }

        return body;
    }

    private LLMResponse parseResponse(String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode choice = root.path("choices").path(0);
        JsonNode message = choice.path("message");
        String finishReason = choice.path("finish_reason").asText("stop");

        // 解析 usage
        JsonNode usage = root.path("usage");
        Integer promptTokens = usage.path("prompt_tokens").isMissingNode()
                ? null : usage.path("prompt_tokens").asInt();
        Integer completionTokens = usage.path("completion_tokens").isMissingNode()
                ? null : usage.path("completion_tokens").asInt();

        // 检查是否为 function_call / tool_calls
        JsonNode toolCalls = message.path("tool_calls");
        if (!toolCalls.isMissingNode() && toolCalls.isArray() && toolCalls.size() > 0) {
            JsonNode firstTool = toolCalls.path(0);
            String funcName = firstTool.path("function").path("name").asText();
            String funcArgs = firstTool.path("function").path("arguments").asText();
            return LLMResponse.builder()
                    .functionCall(true)
                    .functionName(funcName)
                    .functionArguments(funcArgs)
                    .finishReason(finishReason)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .build();
        }

        // 普通文本回复
        String content = message.path("content").asText(FALLBACK_REPLY);
        return LLMResponse.builder()
                .content(content)
                .functionCall(false)
                .finishReason(finishReason)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .build();
    }
}
