package com.hmdp.dto;

import lombok.Data;

/**
 * 统一 HTTP 响应结果
 */
@Data
public class Result {

    /** 是否成功 */
    private Boolean success;

    /** 错误信息（失败时有值） */
    private String errorMsg;

    /** 响应数据 */
    private Object data;

    /** 总数（分页场景） */
    private Long total;

    /** 成功响应（无数据） */
    public static Result ok() {
        Result result = new Result();
        result.success = true;
        return result;
    }

    /** 成功响应 */
    public static Result ok(Object data) {
        Result result = new Result();
        result.success = true;
        result.data = data;
        return result;
    }

    /** 失败响应 */
    public static Result fail(String errorMsg) {
        Result result = new Result();
        result.success = false;
        result.errorMsg = errorMsg;
        return result;
    }
}
