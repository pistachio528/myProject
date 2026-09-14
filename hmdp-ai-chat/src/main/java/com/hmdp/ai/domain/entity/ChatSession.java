package com.hmdp.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_session")
public class ChatSession {
    @TableId
    private String id;
    private Long userId;
    /** 1=活跃 2=已结束 */
    private Integer status;
    private Integer messageCount;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
    private LocalDateTime updateTime;
}
