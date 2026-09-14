package com.hmdp.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContentFilter 单元测试
 */
class ContentFilterTest {

    private ContentFilter contentFilter;

    @BeforeEach
    void setUp() {
        contentFilter = new ContentFilter();
    }

    // ---- 正常消息（不应触发过滤）----

    @Test
    void normalMessage_shouldNotBeViolation() {
        assertThat(contentFilter.isViolation("我想查询我的订单状态")).isFalse();
    }

    @Test
    void nullMessage_shouldNotBeViolation() {
        assertThat(contentFilter.isViolation(null)).isFalse();
    }

    @Test
    void blankMessage_shouldNotBeViolation() {
        assertThat(contentFilter.isViolation("   ")).isFalse();
    }

    @Test
    void emptyMessage_shouldNotBeViolation() {
        assertThat(contentFilter.isViolation("")).isFalse();
    }

    // ---- 敏感关键词触发 ----

    @Test
    void messageWithKeyword_骗子_shouldBeViolation() {
        assertThat(contentFilter.isViolation("这个平台是骗子")).isTrue();
    }

    @Test
    void messageWithKeyword_诈骗_shouldBeViolation() {
        assertThat(contentFilter.isViolation("我遇到了诈骗")).isTrue();
    }

    @Test
    void messageWithKeyword_刷单_shouldBeViolation() {
        assertThat(contentFilter.isViolation("帮我刷单赚钱")).isTrue();
    }

    @Test
    void messageWithKeyword_赌博_shouldBeViolation() {
        assertThat(contentFilter.isViolation("推荐赌博网站")).isTrue();
    }

    @Test
    void messageWithKeyword_暴力_shouldBeViolation() {
        assertThat(contentFilter.isViolation("含有暴力内容")).isTrue();
    }

    // ---- 正则模式触发 ----

    @Test
    void messageWithPhoneNumber_shouldBeViolation() {
        assertThat(contentFilter.isViolation("我的手机号是13812345678")).isTrue();
    }

    @Test
    void messageWithIdCard_shouldBeViolation() {
        assertThat(contentFilter.isViolation("身份证号110101199001011234")).isTrue();
    }

    @Test
    void messageWithBankCard_shouldBeViolation() {
        // 16位纯数字银行卡号，前后无其他数字字符
        assertThat(contentFilter.isViolation("卡号 6222021234567890 请核实")).isTrue();
    }

    // ---- 边界情况 ----

    @Test
    void shortNumber_shouldNotBeViolation() {
        // 少于16位数字不应触发银行卡规则
        assertThat(contentFilter.isViolation("订单号123456789012345")).isFalse();
    }

    @Test
    void violationReplyConstant_shouldNotBeEmpty() {
        assertThat(ContentFilter.VIOLATION_REPLY).isNotBlank();
    }
}
