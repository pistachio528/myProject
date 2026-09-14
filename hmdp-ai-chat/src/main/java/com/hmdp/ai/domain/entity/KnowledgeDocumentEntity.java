package com.hmdp.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库文档元数据实体（对应 knowledge_document 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_document")
public class KnowledgeDocumentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文档类型：faq/rule/product */
    private String docType;

    /** 文档标题 */
    private String title;

    /** 原始文档内容 */
    private String content;

    /** 分块数量 */
    private Integer chunkCount;

    /**
     * 文档状态
     * 1=已索引 2=索引中 3=已删除
     */
    private Integer status;

    /** 版本号 */
    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
