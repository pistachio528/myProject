package com.hmdp.ai.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 知识库文档（用于索引）
 */
@Data
@Builder
public class KnowledgeDocument {
    /** 文档 ID（MySQL 主键） */
    private Long id;
    /** 文档类型：faq/rule/product */
    private String docType;
    /** 文档标题 */
    private String title;
    /** 文档内容 */
    private String content;
}
