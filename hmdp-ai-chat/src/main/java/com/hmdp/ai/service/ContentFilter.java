package com.hmdp.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 敏感内容过滤器
 * 基于关键词列表 + 正则匹配检测用户消息中的违规内容
 * 需求 1.4：WHEN 用户消息包含敏感词或违规内容，THE AI_Customer_Service SHALL 拒绝生成回复并返回友好提示
 */
@Slf4j
@Component
public class ContentFilter {

    /** 友好提示语 */
    public static final String VIOLATION_REPLY = "您的消息包含不当内容，无法为您提供服务。如有疑问请联系人工客服。";

    /** 敏感关键词列表（可扩展为从配置文件或数据库加载） */
    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "骗子", "诈骗", "刷单", "套现", "黑产",
            "违禁", "赌博", "色情", "暴力", "恐怖"
    );

    /** 敏感正则模式（匹配手机号、身份证等隐私信息泄露风险） */
    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
            // 手机号（防止用户被诱导提供隐私）
            Pattern.compile("1[3-9]\\d{9}"),
            // 身份证号
            Pattern.compile("\\d{17}[0-9Xx]"),
            // 银行卡号（16-19位数字）
            Pattern.compile("\\b\\d{16,19}\\b")
    );

    /**
     * 检查消息是否包含违规内容
     *
     * @param message 用户消息
     * @return true=包含违规内容，false=正常
     */
    public boolean isViolation(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        // 1. 关键词匹配
        String lowerMsg = message.toLowerCase();
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lowerMsg.contains(keyword)) {
                log.info("Content filter triggered by keyword: [{}]", keyword);
                return true;
            }
        }

        // 2. 正则匹配
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(message).find()) {
                log.info("Content filter triggered by pattern: [{}]", pattern.pattern());
                return true;
            }
        }

        return false;
    }
}
