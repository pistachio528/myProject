package com.hmdp.handler;

import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * <p>
 * 捕获未处理的异常，返回友好提示而非 500 堆栈信息。
 * 重点处理中间件不可用（Redis、MQ）的场景。
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Redis 连接异常 —— Redis 宕机或连接池耗尽
     */
    @ExceptionHandler(RedisConnectionFailureException.class)
    public Result handleRedisConnectionFailure(RedisConnectionFailureException e) {
        log.error("Redis 连接异常: {}", e.getMessage());
        return Result.fail("系统繁忙，请稍后重试");
    }

    /**
     * Redisson 相关异常（分布式锁获取失败等）
     */
    @ExceptionHandler(org.redisson.client.RedisException.class)
    public Result handleRedissonException(org.redisson.client.RedisException e) {
        log.error("Redisson 异常: {}", e.getMessage());
        return Result.fail("系统繁忙，请稍后重试");
    }

    /**
     * RabbitMQ 连接异常
     */
    @ExceptionHandler(org.springframework.amqp.AmqpConnectException.class)
    public Result handleAmqpConnectException(org.springframework.amqp.AmqpConnectException e) {
        log.error("RabbitMQ 连接异常: {}", e.getMessage());
        // MQ 挂了但本地消息表兜底，不应影响主链路返回
        return Result.fail("系统繁忙，请稍后重试");
    }

    /**
     * 参数类型转换异常（如 voucherId 传了非数字）
     */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public Result handleTypeMismatch(Exception e) {
        log.warn("参数类型错误: {}", e.getMessage());
        return Result.fail("参数格式错误");
    }

    /**
     * 兜底：所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("未处理异常: ", e);
        return Result.fail("系统繁忙，请稍后重试");
    }
}
