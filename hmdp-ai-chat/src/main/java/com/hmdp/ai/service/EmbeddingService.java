package com.hmdp.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hmdp.ai.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 向量化服务
 * 调用 OpenAI 兼容 API 将文本转换为向量表示
 */
@Slf4j
@Service
public class EmbeddingService {

    private final WebClient llmWebClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public EmbeddingService(@Qualifier("llmWebClient") WebClient llmWebClient,
                            AiProperties aiProperties,
                            ObjectMapper objectMapper) {
        this.llmWebClient = llmWebClient;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 将单个文本转换为向量
     * @param text 输入文本
     * @return 向量（float 列表）
     */
    public List<Float> embed(String text) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", aiProperties.getLlm().getEmbeddingModel());
            body.put("input", text);

            String responseJson = llmWebClient.post()
                    .uri("/embeddings")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode embeddingNode = root.path("data").path(0).path("embedding");

            List<Float> embedding = new ArrayList<>();
            for (JsonNode val : embeddingNode) {
                embedding.add((float) val.asDouble());
            }
            return embedding;
        } catch (Exception e) {
            log.error("Failed to embed text: {}", e.getMessage(), e);
            throw new RuntimeException("Embedding failed: " + e.getMessage(), e);
        }
    }

    /**
     * 将文本分块
     * 按段落分割，超过 chunkSize 的段落按固定长度切分，支持重叠
     *
     * @param text 原始文本
     * @return 文本片段列表
     */
    public List<String> splitIntoChunks(String text) {
        int chunkSize = aiProperties.getRag().getChunkSize();
        int overlap = aiProperties.getRag().getChunkOverlap();

        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        // 先按段落分割
        String[] paragraphs = text.split("\n\n+");
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            para = para.trim();
            if (para.isEmpty()) continue;

            if (current.length() + para.length() <= chunkSize) {
                if (current.length() > 0) current.append("\n\n");
                current.append(para);
            } else {
                // 当前 chunk 已满，保存并开始新 chunk
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    // 保留重叠部分
                    String overlapText = current.length() > overlap
                            ? current.substring(current.length() - overlap)
                            : current.toString();
                    current = new StringBuilder(overlapText);
                    if (current.length() > 0) current.append("\n\n");
                }
                // 如果单个段落超过 chunkSize，强制切分
                if (para.length() > chunkSize) {
                    for (int i = 0; i < para.length(); i += chunkSize - overlap) {
                        int end = Math.min(i + chunkSize, para.length());
                        chunks.add(para.substring(i, end));
                        if (end == para.length()) break;
                    }
                } else {
                    current.append(para);
                }
            }
        }

        if (current.length() > 0) {
            chunks.add(current.toString());
        }

        return chunks;
    }
}
