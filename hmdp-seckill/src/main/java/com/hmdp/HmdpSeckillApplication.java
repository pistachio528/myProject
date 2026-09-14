package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 黑马点评秒杀优化项目启动类
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@MapperScan("com.hmdp.mapper")
public class HmdpSeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(HmdpSeckillApplication.class, args);
    }
}
