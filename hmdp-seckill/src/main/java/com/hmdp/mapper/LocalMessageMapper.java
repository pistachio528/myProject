package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.LocalMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表 Mapper
 */
@Mapper
public interface LocalMessageMapper extends BaseMapper<LocalMessage> {

    /**
     * 查询超时的 PENDING 消息（createTime < timeout）
     * 用于定时重试任务扫描
     *
     * @param timeout 超时时间点（早于此时间的 PENDING 消息视为超时）
     * @return 超时 PENDING 消息列表（最多 100 条）
     */
    List<LocalMessage> findPendingTimeout(@Param("timeout") LocalDateTime timeout);
}
