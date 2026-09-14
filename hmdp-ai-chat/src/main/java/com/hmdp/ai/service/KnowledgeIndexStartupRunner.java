package com.hmdp.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.ai.domain.KnowledgeDocument;
import com.hmdp.ai.domain.entity.KnowledgeDocumentEntity;
import com.hmdp.ai.engine.RAGModule;
import com.hmdp.ai.mapper.KnowledgeDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 应用启动时自动加载知识库文档并完成向量化索引。
 *
 * <p><b>文件驱动模式：</b>知识库内容维护在 {@code resources/knowledge/} 目录下的 .md 文件中，
 * 每个 ## 二级标题对应一条文档记录。启动时扫描文件，与数据库按标题对比：
 * <ul>
 *   <li>新增标题 → INSERT 到数据库</li>
 *   <li>内容有变化 → UPDATE 数据库记录，version+1</li>
 *   <li>数据库中有但文件里已删除 → 标记 status=3（逻辑删除）</li>
 * </ul>
 * 同步完成后统一向量化所有有效文档。
 *
 * <p><b>如何修改知识库：</b>直接编辑 resources/knowledge/ 下的 .md 文件，重启服务自动生效。
 *
 * <p>需求: 6.6 — WHEN AI_Customer_Service 启动时，SHALL 自动加载知识库文档并完成向量化索引构建
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIndexStartupRunner implements ApplicationRunner {

    private final RAGModule ragModule;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    /** knowledge/ 目录下 md 文件的文件名前缀 → docType 映射规则 */
    private static final java.util.Map<String, String> FILE_TYPE_MAP = java.util.Map.of(
            "faq",         "faq",
            "rules",       "rule",
            "product",     "product",
            "reservation", "faq"   // 预约说明归入 faq 类型，参与 RAG 检索
    );

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting knowledge base sync from markdown files...");
        try {
            // Step 1: 扫描 resources/knowledge/*.md，解析出所有文档条目
            List<KnowledgeDocumentEntity> fileEntries = loadFromMarkdownFiles();
            log.info("Loaded {} entries from markdown files", fileEntries.size());

            // Step 2: 与数据库对比，执行增量同步
            syncToDatabase(fileEntries);

            // Step 3: 从数据库加载所有有效文档，统一向量化
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
                    log.error("Failed to index document {} ({}): {}",
                            doc.getId(), doc.getTitle(), e.getMessage());
                    failed++;
                }
            }
            log.info("Knowledge base indexing completed: total={}, success={}, failed={}",
                    docs.size(), success, failed);

        } catch (Exception e) {
            log.warn("Knowledge base startup sync failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 扫描 resources/knowledge/*.md，按 ## 二级标题拆分为独立文档条目。
     * 文件名（不含扩展名）决定 docType，通过 FILE_TYPE_MAP 映射。
     */
    private List<KnowledgeDocumentEntity> loadFromMarkdownFiles() throws Exception {
        List<KnowledgeDocumentEntity> result = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:knowledge/*.md");

        for (Resource resource : resources) {
            // 从文件名推断 docType
            String filename = resource.getFilename();
            if (filename == null) continue;
            String baseName = filename.replace(".md", "");
            String docType = FILE_TYPE_MAP.getOrDefault(baseName, baseName);

            // 读取文件内容
            String content;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                content = reader.lines().collect(Collectors.joining("\n"));
            }

            // 按 ## 二级标题拆分，每个标题 + 其下内容 = 一条文档
            String[] sections = content.split("(?m)^## ");
            for (String section : sections) {
                if (section.isBlank() || section.startsWith("#")) continue; // 跳过文件标题行
                int newlineIdx = section.indexOf('\n');
                if (newlineIdx < 0) continue;
                String title = section.substring(0, newlineIdx).trim();
                String body = section.substring(newlineIdx).trim();
                if (title.isBlank() || body.isBlank()) continue;

                result.add(KnowledgeDocumentEntity.builder()
                        .docType(docType)
                        .title(title)
                        .content(body)
                        .status(1)
                        .version(1)
                        .build());
            }
        }
        return result;
    }

    /**
     * 将文件解析结果与数据库对比，执行增量同步：
     * - 新增：INSERT
     * - 内容变化：UPDATE content + version+1
     * - 文件中已删除：UPDATE status=3
     */
    private void syncToDatabase(List<KnowledgeDocumentEntity> fileEntries) {
        // 加载数据库中所有记录（含已删除，方便恢复）
        List<KnowledgeDocumentEntity> dbDocs = knowledgeDocumentMapper.selectList(null);
        java.util.Map<String, KnowledgeDocumentEntity> dbByTitle = dbDocs.stream()
                .collect(Collectors.toMap(KnowledgeDocumentEntity::getTitle, d -> d, (a, b) -> a));

        // 文件中存在的标题集合，用于后续判断哪些需要逻辑删除
        java.util.Set<String> fileTitles = fileEntries.stream()
                .map(KnowledgeDocumentEntity::getTitle)
                .collect(java.util.stream.Collectors.toSet());

        int inserted = 0, updated = 0, deleted = 0;

        // 新增 or 更新
        for (KnowledgeDocumentEntity fileEntry : fileEntries) {
            KnowledgeDocumentEntity dbDoc = dbByTitle.get(fileEntry.getTitle());
            if (dbDoc == null) {
                // 新增
                fileEntry.setCreateTime(LocalDateTime.now());
                fileEntry.setUpdateTime(LocalDateTime.now());
                knowledgeDocumentMapper.insert(fileEntry);
                inserted++;
            } else if (!fileEntry.getContent().equals(dbDoc.getContent()) || dbDoc.getStatus() == 3) {
                // 内容有变化，或之前被删除现在恢复
                dbDoc.setContent(fileEntry.getContent());
                dbDoc.setDocType(fileEntry.getDocType());
                dbDoc.setStatus(1);
                dbDoc.setVersion(dbDoc.getVersion() == null ? 1 : dbDoc.getVersion() + 1);
                dbDoc.setUpdateTime(LocalDateTime.now());
                knowledgeDocumentMapper.updateById(dbDoc);
                updated++;
            }
        }

        // 数据库中有但文件里已删除的 → 逻辑删除
        for (KnowledgeDocumentEntity dbDoc : dbDocs) {
            if (!fileTitles.contains(dbDoc.getTitle()) && dbDoc.getStatus() != 3) {
                dbDoc.setStatus(3);
                dbDoc.setUpdateTime(LocalDateTime.now());
                knowledgeDocumentMapper.updateById(dbDoc);
                deleted++;
            }
        }

        log.info("Knowledge DB sync: inserted={}, updated={}, deleted(logical)={}", inserted, updated, deleted);
    }
}
