package com.hmdp.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.ai.domain.KnowledgeDocument;
import com.hmdp.ai.domain.entity.KnowledgeDocumentEntity;
import com.hmdp.ai.engine.RAGModule;
import com.hmdp.ai.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理端点
 * 提供全量索引构建等管理操作
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final RAGModule ragModule;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    /**
     * POST /api/knowledge/index
     * 触发全量知识库索引构建
     * 从 MySQL 加载所有有效文档，逐一向量化并存储到 Milvus
     *
     * 注意：当前为同步操作，生产环境建议改为异步任务
     */
    @PostMapping("/index")
    public ResponseEntity<Map<String, Object>> buildIndex() {
        log.info("Starting full knowledge base index build...");

        // 查询所有未删除的文档（排除 status=3 已删除）
        List<KnowledgeDocumentEntity> docs = knowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocumentEntity>()
                        .ne(KnowledgeDocumentEntity::getStatus, 3)
        );

        int success = 0, failed = 0;
        for (KnowledgeDocumentEntity doc : docs) {
            try {
                ragModule.indexDocument(KnowledgeDocument.builder()
                        .id(doc.getId())
                        .docType(doc.getDocType())
                        .title(doc.getTitle())
                        .content(doc.getContent())
                        .build());
                success++;
            } catch (Exception e) {
                log.error("Failed to index document {}: {}", doc.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("Knowledge base index build completed: total={}, success={}, failed={}",
                docs.size(), success, failed);

        return ResponseEntity.ok(Map.of(
                "total", docs.size(),
                "success", success,
                "failed", failed
        ));
    }
}
