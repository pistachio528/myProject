package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置
 * <p>
 * 注册 RedissonClient Bean，从 application.yml 读取 Redis 连接配置。
 * Redisson 提供：
 * - RLock：内置 watchdog 自动续期（默认每 10s 续期一次，续期到 30s）
 * - compare-and-delete 原子解锁（Lua 脚本保证）
 * - 防止锁误删和锁永久占用
 * </p>
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private int redisPort;

    /**
     * 注册 RedissonClient Bean（单节点模式）
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 单节点模式，地址格式：redis://host:port
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                // 连接池最小空闲连接数
                .setConnectionMinimumIdleSize(2)
                // 连接池最大连接数
                .setConnectionPoolSize(10)
                // 连接超时（ms）
                .setConnectTimeout(3000)
                // 命令等待超时（ms）
                .setTimeout(3000);
        return Redisson.create(config);
    }
}
