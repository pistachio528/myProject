package com.hmdp.ai.engine;

import com.hmdp.ai.domain.DocumentChunk;
import com.hmdp.ai.domain.KnowledgeDocument;
import java.util.List;

/**
 * RAG（检索增强生成）模块接口
 */
public interface RAGModule {

    /**
     * 根据用户查询检索相关文档片段
     * @param query     用户原始问题
     * @param topK      返回的最大片段数
     * @param threshold 相似度阈值（0.0~1.0）
     * @return 按相似度降序排列的文档片段列表（仅包含分数 >= threshold 的结果）
     */
    List<DocumentChunk> retrieve(String query, int topK, double threshold);

    /**
     * 将文档分块并向量化存储到 Milvus
     */
    void indexDocument(KnowledgeDocument document);

    /**
     * 更新已有文档的向量索引（先删除旧片段，再重新索引）
     */
    void updateDocument(KnowledgeDocument document);

    /**
     * 删除文档的向量索引
     */
    void deleteDocument(String documentId);
}
