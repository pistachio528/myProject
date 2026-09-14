package com.hmdp.ai.engine.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.ai.config.AiProperties;
import com.hmdp.ai.domain.ChatMessage;
import com.hmdp.ai.domain.UserContext;
import com.hmdp.ai.domain.dto.IntentResult;
import com.hmdp.ai.domain.dto.LLMRequest;
import com.hmdp.ai.domain.dto.LLMResponse;
import com.hmdp.ai.domain.dto.ResolveContext;
import com.hmdp.ai.domain.entity.IntentLogEntity;
import com.hmdp.ai.engine.IntentResolver;
import com.hmdp.ai.engine.LLMEngine;
import com.hmdp.ai.mapper.IntentLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 意图识别与消歧引擎实现
 * <p>
 * 置信度分级策略：
 * - confidence >= 0.7  → 直接执行
 * - 0.4 <= confidence < 0.7 → 二次判断（多信号融合），判断后 >= 0.7 执行，否则发送确认消息
 * - confidence < 0.4  → 触发澄清流程
 * <p>
 * 多信号融合评分公式：
 * score = 0.35 × history_score + 0.25 × context_score + 0.20 × time_score + 0.20 × page_score
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentResolverImpl implements IntentResolver {

    private static final String INTENT_SYSTEM_PROMPT =
            "你是一个意图识别助手。分析用户消息，识别其意图，并以JSON格式输出结果。\n\n" +
            "支持的意图类型：\n" +
            "- tool_call：用户需要执行具体操作或查询实时数据，如：查我的订单、帮我预约、取消预约、查库存等\n" +
            "- knowledge_qa：用户在询问平台规则、使用说明、操作方法等知识性问题，如：怎么预约、如何退款、预约规则是什么等\n" +
            "- unknown：无法识别，或与平台业务完全无关\n\n" +
            "关键区分规则：\n" +
            "1. 用户说\"怎么...\"、\"如何...\"、\"...是什么\"、\"...规则\"→ knowledge_qa\n" +
            "2. 用户说\"帮我...\"、\"查一下...\"、\"我要...\"、\"取消...\" → tool_call\n" +
            "3. 用户的简短回复（如需要、是的、好的、要）通常是对上一轮客服问题的肯定回答，" +
            "请结合对话历史中客服的最后一个问题来判断意图，置信度应设为 0.8 以上。\n" +
            "4. 用户的否定回复（如不需要、不用、算了）置信度也应设为 0.8 以上，意图为 unknown。\n" +
            "5. 只有在完全无法从上下文推断意图时，才将意图设为 unknown 且置信度低于 0.4。\n\n" +
            "输出格式（只输出JSON，不要其他内容）：\n" +
            "{\"intent\": \"意图名称\", \"confidence\": 0.85, " +
            "\"entities\": [{\"type\": \"实体类型\", \"value\": \"实体值\", \"source\": \"user_message\"}], " +
            "\"reasoning\": \"推理说明\"}";

    private final LLMEngine llmEngine;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;
    private final IntentLogMapper intentLogMapper;

    /**
     * 规则引擎：关键词 → 意图映射
     * 命中则直接返回高置信度结果，跳过 LLM 调用，大幅降低响应延迟
     * key = 意图名称，value = 触发关键词列表（任意一个命中即可）
     */
    /**
     * 规则引擎：只做一件事——判断是走 RAG（知识库）还是走工具（实时数据）
     *
     * 设计原则：
     * - 命中"知识类"关键词 → knowledge_qa → 走 RAG
     * - 其他所有问题 → 交给 LLM 识别（tool_call / knowledge_qa / unknown）
     *
     * 关键词匹配策略：用户消息包含任意一个关键词即命中（子串匹配）
     * 关键词尽量用2字以上，避免单字误匹配（如"规"、"退"太短容易误触发）
     */
    private static final List<String> KNOWLEDGE_KEYWORDS = Arrays.asList(
            // 退款退货
            "退款", "退货", "退单",
            // 积分
            "积分",
            // 会员
            "会员",
            // 账号密码
            "密码", "账号", "封号", "封禁",
            // 规则说明
            "规则", "说明", "政策", "条款",
            // 预约说明（操作类"帮我预约"不在此，只有询问类）
            "怎么预约", "如何预约", "预约规则", "预约说明", "预约流程",
            "怎么取消预约", "预约注意",
            // 支付
            "支付方式", "怎么付款", "付款方式",
            // 帮助
            "怎么用", "如何使用", "使用说明", "怎么参与"
    );

    @Override
    public IntentResult resolve(String message, ResolveContext context) {
        // ── 快速路径：规则引擎关键词匹配，命中则跳过 LLM 调用 ──
        RuleMatchResult ruleMatch = matchByKeyword(message);
        if (ruleMatch != null) {
            log.info("Intent resolved by [规则引擎]: intent={}, keyword=\"{}\", message=\"{}\"",
                    ruleMatch.intent, ruleMatch.matchedKeyword, message);
            IntentResult result = IntentResult.builder()
                    .intent(ruleMatch.intent)
                    .confidence(ruleMatch.confidence)
                    .entities(new ArrayList<>())
                    .reasoning("规则引擎匹配关键词: " + ruleMatch.matchedKeyword)
                    .needsConfirmation(false)
                    .build();
            logIntent(context, message, result, 0);
            return result;
        }

        // ── 慢速路径：规则未命中，fallback 到 LLM 识别 ──
        log.info("Intent not matched by rule engine, falling back to [LLM识别]: message=\"{}\"", message);
        double confidence = 0.5;
        String intent = "unknown";
        List<IntentResult.Entity> entities = new ArrayList<>();
        String reasoning = "";

        try {
            // 构建包含对话历史的消息列表，让 LLM 结合上下文识别意图
            List<ChatMessage> intentMessages = new ArrayList<>();
            intentMessages.add(ChatMessage.builder().role("system").content(INTENT_SYSTEM_PROMPT).build());

            // 加入最近 3 轮对话历史（帮助理解简短回复）
            // 注意：对历史消息内容截断，避免工具返回的长数据撑大请求体
            // 意图识别只需要知道"上一轮在聊什么"，不需要完整内容
            if (context.getHistory() != null && !context.getHistory().isEmpty()) {
                int historySize = context.getHistory().size();
                int start = Math.max(0, historySize - 6); // 最多 3 轮（6条消息）
                for (int i = start; i < historySize; i++) {
                    ChatMessage msg = context.getHistory().get(i);
                    String content = msg.getContent();
                    if (content == null) continue;
                    // assistant 消息（工具返回数据）截断更短，只保留前 50 字
                    // user 消息保留前 100 字
                    int limit = "assistant".equals(msg.getRole()) ? 50 : 100;
                    if (content.length() > limit) {
                        content = content.substring(0, limit) + "...";
                    }
                    intentMessages.add(ChatMessage.builder()
                            .role(msg.getRole())
                            .content(content)
                            .build());
                }
            }
            intentMessages.add(ChatMessage.builder().role("user").content(message).build());

            LLMRequest request = LLMRequest.builder()
                    .messages(intentMessages)
                    .temperature(0.0)
                    .maxTokens(512)
                    .build();

            LLMResponse response = llmEngine.chatSync(request);
            String content = response.getContent();

            // 提取 JSON（LLM 可能在 JSON 前后有多余文本）
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                content = content.substring(start, end + 1);
            }

            JsonNode node = objectMapper.readTree(content);
            intent = node.path("intent").asText("unknown");
            confidence = node.path("confidence").asDouble(0.5);
            confidence = Math.max(0.0, Math.min(1.0, confidence));
            reasoning = node.path("reasoning").asText("");

            // 解析实体列表
            JsonNode entitiesNode = node.path("entities");
            if (entitiesNode.isArray()) {
                for (JsonNode e : entitiesNode) {
                    entities.add(IntentResult.Entity.builder()
                            .type(e.path("type").asText())
                            .value(e.path("value").asText())
                            .source(e.path("source").asText("user_message"))
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse intent result from LLM, using defaults. Error: {}", e.getMessage());
            // 解析失败时默认 tool_call（LLM 已识别意图但 JSON 被截断），让 LLM 自己决定调哪个工具
            intent = "tool_call";
            confidence = 0.75;
        }

        // 2. 置信度分级路由
        double highThreshold = aiProperties.getIntent().getConfidenceThresholdHigh();
        double lowThreshold = aiProperties.getIntent().getConfidenceThresholdLow();

        IntentResult result;
        // 0=否 1=自动消歧 2=用户确认
        int disambiguationType = 0;

        if (confidence >= highThreshold) {
            // 直接执行
            result = IntentResult.builder()
                    .intent(intent)
                    .confidence(confidence)
                    .entities(entities)
                    .reasoning(reasoning)
                    .needsConfirmation(false)
                    .build();

        } else if (confidence >= lowThreshold) {
            // 二次判断：多信号融合
            double secondaryScore = computeSecondaryScore(message, context);
            if (secondaryScore >= highThreshold) {
                // 二次判断后置信度提升，自动消歧
                disambiguationType = 1;
                result = IntentResult.builder()
                        .intent(intent)
                        .confidence(secondaryScore)
                        .entities(entities)
                        .reasoning(reasoning + " [二次判断提升置信度]")
                        .needsConfirmation(false)
                        .build();
            } else {
                // 仍无法确定，需要用户确认
                disambiguationType = 2;
                result = IntentResult.builder()
                        .intent(intent)
                        .confidence(confidence)
                        .entities(entities)
                        .reasoning(reasoning)
                        .needsConfirmation(true)
                        .clarificationQuestion(buildConfirmationQuestion(intent, entities))
                        .build();
            }
        } else {
            // 置信度过低，触发澄清流程
            disambiguationType = 2;
            result = IntentResult.builder()
                    .intent("unknown")
                    .confidence(confidence)
                    .entities(entities)
                    .reasoning(reasoning)
                    .needsConfirmation(true)
                    .clarificationQuestion(
                            "您好！我是黑马点评智能客服，很高兴为您服务。" +
                            "请问您需要：查询订单状态、了解秒杀活动、查询优惠券信息，还是有其他问题？")
                    .build();
        }

        // 3. 记录意图日志（不影响主流程）
        logIntent(context, message, result, disambiguationType);

        return result;
    }

    /**
     * 多信号融合二次判断
     * <p>
     * score = 0.35 × history_score + 0.25 × context_score + 0.20 × time_score + 0.20 × page_score
     *
     * @param message 用户消息
     * @param context 解析上下文
     * @return 加权综合分数 0.0 ~ 1.0
     */
    double computeSecondaryScore(String message, ResolveContext context) {
        UserContext userContext = context.getUserContext();
        if (userContext == null) {
            return 0.0;
        }

        // 信号1：对话历史中是否提及相关实体（权重 0.35）
        double historyScore = 0.0;
        if (context.getHistory() != null && !context.getHistory().isEmpty()) {
            String lowerMsg = message.toLowerCase();
            String prefix = lowerMsg.substring(0, Math.min(10, lowerMsg.length()));
            for (ChatMessage msg : context.getHistory()) {
                if (msg.getContent() != null && msg.getContent().toLowerCase().contains(prefix)) {
                    historyScore = 1.0;
                    break;
                }
            }
        }

        // 信号2：用户上下文中是否有相关数据（权重 0.25）
        double contextScore = 0.0;
        boolean hasRecentOrders = userContext.getRecentOrderTokens() != null
                && !userContext.getRecentOrderTokens().isEmpty();
        boolean hasRecentVouchers = userContext.getRecentVoucherIds() != null
                && !userContext.getRecentVoucherIds().isEmpty();
        if (hasRecentOrders || hasRecentVouchers) {
            contextScore = 1.0;
        }

        // 信号3：时间相关性（权重 0.20）
        double timeScore = 0.0;
        if (userContext.getLastInteractTime() != null) {
            long diffMs = System.currentTimeMillis() - userContext.getLastInteractTime();
            long diffHours = diffMs / (1000L * 60 * 60);
            if (diffHours <= 24) {
                timeScore = 1.0;
            } else if (diffHours <= 168) { // 7 天
                timeScore = 0.5;
            } else {
                timeScore = 0.1;
            }
        }

        // 信号4：当前页面信息（权重 0.20）
        double pageScore = 0.0;
        String pageContext = context.getPageContext() != null
                ? context.getPageContext()
                : (userContext.getPageContext() != null ? userContext.getPageContext() : "");
        if (!pageContext.isBlank()) {
            pageScore = 1.0;
        }

        return 0.35 * historyScore + 0.25 * contextScore + 0.20 * timeScore + 0.20 * pageScore;
    }

    /**
     * 根据意图和实体构建简短确认问题
     */
    private String buildConfirmationQuestion(String intent, List<IntentResult.Entity> entities) {
        if (!entities.isEmpty()) {
            String entityValue = entities.get(0).getValue();
            switch (intent) {
                case "query_order_status":
                    return "您是想查询订单 " + entityValue + " 的状态吗？";
                case "query_voucher_info":
                    return "您是想了解 " + entityValue + " 的详情吗？";
                case "query_stock_status":
                    return "您是想查询 " + entityValue + " 的库存情况吗？";
                case "list_active_seckill":
                    return "您是想查看当前进行中的秒杀活动吗？";
                default:
                    break;
            }
        }
        return "请问您具体想了解什么？";
    }

    /**
     * 将意图识别结果写入 intent_log 表
     * 异常时仅记录日志，不影响主流程
     */
    private void logIntent(ResolveContext context, String message, IntentResult result, int disambiguationType) {
        try {
            String sessionId = (context.getUserContext() != null && context.getUserContext().getSessionId() != null)
                    ? context.getUserContext().getSessionId()
                    : "unknown";

            // 截断过长的消息
            String truncatedMessage = message.length() > 500 ? message.substring(0, 500) : message;

            IntentLogEntity logEntity = IntentLogEntity.builder()
                    .sessionId(sessionId)
                    .userMessage(truncatedMessage)
                    .intent(result.getIntent())
                    .confidence(BigDecimal.valueOf(result.getConfidence()).setScale(2, RoundingMode.HALF_UP))
                    .disambiguation(disambiguationType)
                    .userConfirmed(result.isNeedsConfirmation() ? 1 : 0)
                    .createTime(LocalDateTime.now())
                    .build();

            intentLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.warn("Failed to log intent to database, skipping. Error: {}", e.getMessage());
        }
    }

    /**
     * 规则引擎关键词匹配
     * 只判断两类：
     * - 命中知识类关键词 → knowledge_qa（走 RAG）
     * - 未命中 → 返回 null，由 LLM 识别（tool_call / knowledge_qa / unknown）
     */
    private RuleMatchResult matchByKeyword(String message) {
        if (message == null || message.isBlank()) return null;
        String lower = message.toLowerCase();
        for (String keyword : KNOWLEDGE_KEYWORDS) {
            if (lower.contains(keyword)) {
                return new RuleMatchResult("knowledge_qa", 0.9, keyword);
            }
        }
        return null;
    }

    /** 规则引擎匹配结果 */
    private static class RuleMatchResult {
        final String intent;
        final double confidence;
        final String matchedKeyword;

        RuleMatchResult(String intent, double confidence, String matchedKeyword) {
            this.intent = intent;
            this.confidence = confidence;
            this.matchedKeyword = matchedKeyword;
        }
    }
}
