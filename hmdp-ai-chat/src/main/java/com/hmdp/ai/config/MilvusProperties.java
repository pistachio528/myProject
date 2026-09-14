package com.hmdp.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {
    private String host = "localhost";
    private int port = 19530;
    private String collectionName = "knowledge_chunks";
    private int embeddingDim = 1536;
}
