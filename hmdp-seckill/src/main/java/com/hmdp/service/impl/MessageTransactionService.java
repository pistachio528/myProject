package com.hmdp.service.impl;

import com.hmdp.dto.SeckillMessage;
import com.hmdp.service.MessageRelay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消息事务服务
 * <p>
 * 将本地消息表写入操作封装为独立 Bean，解决 SeckillServiceImpl 的 self-invocation 问题。
 * Spring 的 @Transactional 基于 AOP 代理，同一个 Bean 内部调用无法触发代理，
 * 将事务方法抽到独立 Bean 后，Spring 可以正确拦截并开启事务。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageTransactionService {

    private final MessageRelay messageRelay;

    /**
     * 在独立事务中写入本地消息表
     * 调用方（SeckillServiceImpl）直接注入此 Bean，无需通过 ApplicationContext 获取代理
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMessageInTransaction(SeckillMessage message) {
        messageRelay.saveMessage(message);
        log.debug("[MessageTransactionService] 本地消息写入事务提交，token={}", message.getToken());
    }
}
