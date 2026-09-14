package com.hmdp.ai.engine.impl;

import com.hmdp.ai.config.AiProperties;
import com.hmdp.ai.config.MilvusProperties;
import com.hmdp.ai.domain.DocumentChunk;
import com.hmdp.ai.domain.KnowledgeDocument;
import com.hmdp.ai.engine.RAGModule;
import com.hmdp.ai.service.EmbeddingService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.*;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 基于 Milvus 的 RAG 模块实现
 * 负责知识库文档的向量化存储与语义检索
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusRAGModule implements RAGModule {

    private static final String COLLECTION_NAME = "knowledge_chunks";
    private static final int EMBEDDING_DIM = 1024;  // BAAI/bge-m3 输出维度

    private final MilvusServiceClient milvusClient;
    private final EmbeddingService embeddingService;
    private final AiProperties aiProperties;
    private final MilvusProperties milvusProperties;

    /**
     * 应用启动时初始化 Milvus Collection
     * 若 Collection 不存在则自动创建并建立索引
     */
    @PostConstruct
    public void initCollection() {
        try {
            // 检查 Collection 是否存在
            R<Boolean> hasCollection = milvusClient.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(COLLECTION_NAME)
                            .build());

            if (Boolean.TRUE.equals(hasCollection.getData())) {
                // 验证 Collection 结构是否完整，有问题则删除重建
                try {
                    io.milvus.param.collection.DescribeCollectionParam describeParam =
                            io.milvus.param.collection.DescribeCollectionParam.newBuilder()
                                    .withCollectionName(COLLECTION_NAME)
                                    .build();
                    R<io.milvus.grpc.DescribeCollectionResponse> describeResp = milvusClient.describeCollection(describeParam);
                    boolean hasChunkId = describeResp.getData().getSchema().getFieldsList()
                            .stream().anyMatch(f -> f.getName().equals("chunk_id"));
                    if (!hasChunkId) {
                        log.warn("Milvus collection '{}' is missing chunk_id field, dropping and recreating", COLLECTION_NAME);
                        milvusClient.dropCollection(
                                io.milvus.param.collection.DropCollectionParam.newBuilder()
                                        .withCollectionName(COLLECTION_NAME).build());
                    } else {
                        log.info("Milvus collection '{}' already exists", COLLECTION_NAME);
                        loadCollection();
                        return;
                    }
                } catch (Exception e) {
                    log.warn("Failed to validate collection, dropping and recreating: {}", e.getMessage());
                    milvusClient.dropCollection(
                            io.milvus.param.collection.DropCollectionParam.newBuilder()
                                    .withCollectionName(COLLECTION_NAME).build());
                }
            }

            // 创建 Collection
            log.info("Creating Milvus collection '{}'", COLLECTION_NAME);
            List<FieldType> fields = Arrays.asList(
                    FieldType.newBuilder()
                            .withName("chunk_id")
                            .withDataType(DataType.VarChar)
                            .withMaxLength(64)
                            .withPrimaryKey(true)
                            .withAutoID(false)
                            .build(),
                    FieldType.newBuilder()
                            .withName("doc_id")
                            .withDataType(DataType.Int64)
                            .build(),
                    FieldType.newBuilder()
                            .withName("doc_type")
                            .withDataType(DataType.VarChar)
                            .withMaxLength(16)
                            .build(),
                    FieldType.newBuilder()
                            .withName("content")
                            .withDataType(DataType.VarChar)
                            .withMaxLength(2048)
                            .build(),
                    FieldType.newBuilder()
                            .withName("embedding")
                            .withDataType(DataType.FloatVector)
                            .withDimension(EMBEDDING_DIM)
                            .build()
            );

            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withDescription("Knowledge base chunks for RAG")
                    .withFieldTypes(fields)
                    .build();
            milvusClient.createCollection(createParam);

            // 创建 IVF_FLAT 索引（nlist=128, metric_type=COSINE）
            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withFieldName("embedding")
                    .withIndexType(IndexType.IVF_FLAT)
                    .withMetricType(MetricType.COSINE)
                    .withExtraParam("{\"nlist\":128}")
                    .build();
            milvusClient.createIndex(indexParam);

            loadCollection();
            log.info("Milvus collection '{}' created and loaded successfully", COLLECTION_NAME);
        } catch (Exception e) {
            log.error("Failed to initialize Milvus collection: {}", e.getMessage(), e);
        }
    }

    /**
     * 根据用户查询检索相关文档片段
     * 查询向量化 → Milvus 相似度检索 → 按阈值过滤 → 降序排列
     */
    @Override
    public List<DocumentChunk> retrieve(String query, int topK, double threshold) {
        try {
            List<Float> queryVector = embeddingService.embed(query);

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                    .withMetricType(MetricType.COSINE)
                    .withOutFields(Arrays.asList("doc_id", "doc_type", "content"))
                    .withTopK(topK)
                    .withVectors(Collections.singletonList(queryVector))
                    .withVectorFieldName("embedding")
                    .withParams("{\"nprobe\":16}")
                    .build();

            R<SearchResults> response = milvusClient.search(searchParam);
            if (response.getData() == null) {
                log.debug("No search results from Milvus for query: {}", query);
                return Collections.emptyList();
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            List<DocumentChunk> results = new ArrayList<>();

            List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
            // 知识库为空时直接返回
            if (scores == null || scores.isEmpty()) {
                return Collections.emptyList();
            }

            // chunk_id 是主键（VarChar），从 IDScore 的 strID 获取，不能用 getFieldData
            List<?> docIds = wrapper.getFieldData("doc_id", 0);
            List<?> docTypes = wrapper.getFieldData("doc_type", 0);
            List<?> contents = wrapper.getFieldData("content", 0);

            for (int i = 0; i < scores.size(); i++) {
                float score = scores.get(i).getScore();
                if (score < threshold) continue;

                String chunkId = scores.get(i).getStrID();  // 主键从 IDScore 取
                Long docId = (Long) docIds.get(i);
                String docType = (String) docTypes.get(i);
                String content = (String) contents.get(i);

                results.add(DocumentChunk.builder()
                        .chunkId(chunkId)
                        .docId(docId)
                        .docType(docType)
                        .content(content)
                        .score(score)
                        .build());
            }

            // 按 score 降序排列
            results.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));

            if (results.isEmpty()) {
                log.debug("No results above threshold {} for query: {}", threshold, query);
            }
            return results;
        } catch (Exception e) {
            log.error("RAG retrieve failed: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 将文档分块并向量化存储到 Milvus
     * 文档内容 → 分块 → 每块向量化 → 批量插入
     */
    @Override
    public void indexDocument(KnowledgeDocument document) {
        try {
            List<String> chunks = embeddingService.splitIntoChunks(document.getContent());
            if (chunks.isEmpty()) {
                log.warn("No chunks generated for document {}", document.getId());
                return;
            }

            List<String> chunkIds = new ArrayList<>();
            List<Long> docIds = new ArrayList<>();
            List<String> docTypes = new ArrayList<>();
            List<String> contents = new ArrayList<>();
            List<List<Float>> embeddings = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                // 截断超过 2048 字符的 chunk（Milvus VARCHAR 字段限制）
                if (chunk.length() > 2048) chunk = chunk.substring(0, 2048);

                chunkIds.add(document.getId() + "_" + i);
                docIds.add(document.getId());
                docTypes.add(document.getDocType() != null ? document.getDocType() : "faq");
                contents.add(chunk);
                embeddings.add(embeddingService.embed(chunk));
            }

            List<InsertParam.Field> fields = Arrays.asList(
                    new InsertParam.Field("chunk_id", chunkIds),
                    new InsertParam.Field("doc_id", docIds),
                    new InsertParam.Field("doc_type", docTypes),
                    new InsertParam.Field("content", contents),
                    new InsertParam.Field("embedding", embeddings)
            );

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withFields(fields)
                    .build();

            R<MutationResult> result = milvusClient.insert(insertParam);
            log.info("Indexed {} chunks for document {}", chunks.size(), document.getId());
        } catch (Exception e) {
            log.error("Failed to index document {}: {}", document.getId(), e.getMessage(), e);
        }
    }

    /**
     * 更新已有文档的向量索引
     * 先删除旧片段，再重新索引
     */
    @Override
    public void updateDocument(KnowledgeDocument document) {
        deleteDocument(String.valueOf(document.getId()));
        indexDocument(document);
    }

    /**
     * 删除文档的向量索引
     * 按 doc_id 表达式删除所有相关片段
     */
    @Override
    public void deleteDocument(String documentId) {
        try {
            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withExpr("doc_id == " + documentId)
                    .build();
            milvusClient.delete(deleteParam);
            log.info("Deleted chunks for document {}", documentId);
        } catch (Exception e) {
            log.error("Failed to delete document {}: {}", documentId, e.getMessage(), e);
        }
    }

    /**
     * 将 Collection 加载到内存以支持检索
     */
    private void loadCollection() {
        milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(COLLECTION_NAME)
                        .build());
    }
}
