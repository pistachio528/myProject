package com.hmdp.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_feedback")
public class ChatFeedbackEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private Long messageId;
    private Long userId;
    /** wrong_entity/irrelevant/inaccurate/other */
    private String feedbackType;
    private String detail;
    /** 0=未处理 1=已重新回复 */
    private Integer resolved;
    private LocalDateTime createTime;
}
