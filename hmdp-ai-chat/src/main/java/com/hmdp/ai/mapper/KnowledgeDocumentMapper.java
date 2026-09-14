package com.hmdp.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.ai.domain.entity.KnowledgeDocumentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档 Mapper
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentEntity> {}
