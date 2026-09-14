package com.hmdp.ai.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 连接配置
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MilvusConfig {

    private final MilvusProperties milvusProperties;

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        try {
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(milvusProperties.getHost())
                    .withPort(milvusProperties.getPort())
                    .build();
            log.info("Connecting to Milvus at {}:{}", milvusProperties.getHost(), milvusProperties.getPort());
            return new MilvusServiceClient(connectParam);
        } catch (Exception e) {
            log.warn("Failed to connect to Milvus at {}:{} — RAG features will be unavailable. Cause: {}",
                    milvusProperties.getHost(), milvusProperties.getPort(), e.getMessage());
            // 返回一个连接参数，客户端会在实际调用时才真正建立连接
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(milvusProperties.getHost())
                    .withPort(milvusProperties.getPort())
                    .build();
            return new MilvusServiceClient(connectParam);
        }
    }
}
