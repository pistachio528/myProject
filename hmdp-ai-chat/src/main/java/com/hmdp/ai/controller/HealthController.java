package com.hmdp.ai.controller;

import com.hmdp.ai.config.AiProperties;
import com.hmdp.ai.config.MilvusProperties;
import com.hmdp.ai.domain.vo.HealthVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查端点
 * GET /api/chat/health
 * 检查 LLM 服务、Milvus、Redis 的连接状态
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class HealthController {

    private final StringRedisTemplate redisTemplate;
    private final AiProperties aiProperties;
    private final MilvusProperties milvusProperties;

    @GetMapping("/health")
    public ResponseEntity<HealthVO> health() {
        Map<String, HealthVO.ComponentStatus> components = new LinkedHashMap<>();

        // 1. 检查 Redis
        components.put("redis", checkRedis());

        // 2. 检查 Milvus（TCP 连通性）
        components.put("milvus", checkMilvus());

        // 3. 检查 LLM 服务（TCP 连通性，不发送实际请求避免消耗 token）
        components.put("llm", checkLlm());

        // 整体状态：所有组件 UP 则 UP，有 DOWN 则 DEGRADED 或 DOWN
        long downCount = components.values().stream()
                .filter(c -> "DOWN".equals(c.getStatus()))
                .count();

        String overallStatus;
        if (downCount == 0) {
            overallStatus = "UP";
        } else if (downCount == components.size()) {
            overallStatus = "DOWN";
        } else {
            overallStatus = "DEGRADED";
        }

        HealthVO healthVO = HealthVO.builder()
                .status(overallStatus)
                .components(components)
                .timestamp(LocalDateTime.now())
                .build();

        // 有组件 DOWN 时返回 503，全部正常返回 200
        return downCount > 0
                ? ResponseEntity.status(503).body(healthVO)
                : ResponseEntity.ok(healthVO);
    }

    private HealthVO.ComponentStatus checkRedis() {
        try {
            String pong = redisTemplate.getConnectionFactory()
                    .getConnection().ping();
            return HealthVO.ComponentStatus.builder()
                    .status("UP")
                    .detail("PONG: " + pong)
                    .build();
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            return HealthVO.ComponentStatus.builder()
                    .status("DOWN")
                    .detail(e.getMessage())
                    .build();
        }
    }

    private HealthVO.ComponentStatus checkMilvus() {
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(milvusProperties.getHost(), milvusProperties.getPort()),
                    3000
            );
            return HealthVO.ComponentStatus.builder()
                    .status("UP")
                    .detail(milvusProperties.getHost() + ":" + milvusProperties.getPort() + " reachable")
                    .build();
        } catch (Exception e) {
            log.warn("Milvus health check failed: {}", e.getMessage());
            return HealthVO.ComponentStatus.builder()
                    .status("DOWN")
                    .detail(e.getMessage())
                    .build();
        }
    }

    private HealthVO.ComponentStatus checkLlm() {
        try {
            // 仅检查 LLM API 域名的 TCP 连通性，不发送实际请求（避免消耗 token）
            String apiUrl = aiProperties.getLlm().getApiUrl();
            java.net.URL url = new java.net.URL(apiUrl);
            int port = url.getPort() == -1 ? (apiUrl.startsWith("https") ? 443 : 80) : url.getPort();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(url.getHost(), port), 5000);
            }
            return HealthVO.ComponentStatus.builder()
                    .status("UP")
                    .detail("API endpoint reachable: " + url.getHost())
                    .build();
        } catch (Exception e) {
            log.warn("LLM health check failed: {}", e.getMessage());
            return HealthVO.ComponentStatus.builder()
                    .status("DOWN")
                    .detail(e.getMessage())
                    .build();
        }
    }
}
