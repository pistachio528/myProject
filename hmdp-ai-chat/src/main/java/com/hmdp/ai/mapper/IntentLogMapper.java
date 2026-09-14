package com.hmdp.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.ai.domain.entity.IntentLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 意图识别日志 Mapper
 */
@Mapper
public interface IntentLogMapper extends BaseMapper<IntentLogEntity> {
}
