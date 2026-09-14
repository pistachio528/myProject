package com.hmdp.ai.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 知识库文档片段（RAG 检索结果）
 */
@Data
@Builder
public class DocumentChunk {
    /** 片段 ID */
    private String chunkId;
    /** 所属文档 ID */
    private Long docId;
    /** 文档类型：faq/rule/product */
    private String docType;
    /** 片段文本内容 */
    private String content;
    /** 相似度分数（0.0 ~ 1.0） */
    private Float score;
}
