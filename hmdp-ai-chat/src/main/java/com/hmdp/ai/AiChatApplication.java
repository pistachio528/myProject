package com.hmdp.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 黑马点评智能客服模块启动类
 * <p>
 * 功能：基于 LLM + RAG + Function Calling 的智能客服系统
 * - LLM：大语言模型对话能力
 * - RAG：检索增强生成，结合知识库回答
 * - Function Calling：调用秒杀系统等外部接口
 */
@SpringBootApplication
@EnableScheduling   // 用于会话过期定时任务
@EnableAsync        // 用于异步处理
@MapperScan("com.hmdp.ai.mapper")
public class AiChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiChatApplication.class, args);
    }
}
