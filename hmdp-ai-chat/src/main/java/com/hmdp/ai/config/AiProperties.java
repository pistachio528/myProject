package com.hmdp.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Llm llm = new Llm();
    private Rag rag = new Rag();
    private Conversation conversation = new Conversation();
    private Intent intent = new Intent();
    private Quality quality = new Quality();
    private Seckill seckill = new Seckill();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Llm {
        private String apiUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private String model = "gpt-4o";
        private double temperature = 0.7;
        private int maxTokens = 2048;
        private int timeoutSeconds = 30;
        private String embeddingModel = "text-embedding-ada-002";
    }

    @Data
    public static class Rag {
        private int topK = 5;
        private double similarityThreshold = 0.7;
        private int chunkSize = 512;
        private int chunkOverlap = 50;
    }

    @Data
    public static class Conversation {
        private int maxHistoryRounds = 10;
        private int sessionTtlHours = 24;
        private int inactiveTimeoutMinutes = 30;
        private int maxContextTokens = 4096;
    }

    @Data
    public static class Intent {
        private double confidenceThresholdHigh = 0.7;
        private double confidenceThresholdLow = 0.4;
        private double disambiguationDiffThreshold = 0.2;
    }

    @Data
    public static class Quality {
        private double relevanceThreshold = 0.6;
        private int maxRetryCount = 2;
    }

    @Data
    public static class Seckill {
        private String baseUrl = "http://localhost:8081";
    }

    @Data
    public static class RateLimit {
        /** 用户级限流：时间窗口大小（秒） */
        private int windowSeconds = 60;
        /** 用户级限流：窗口内最大请求数 */
        private int maxRequestsPerWindow = 10;
        /** 全局并发上限：同时处理的 LLM 请求数 */
        private int maxConcurrency = 20;
        /** 是否启用限流（false 时所有检查直接放行，方便开发调试） */
        private boolean enabled = true;
    }
}
