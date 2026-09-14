package com.hmdp.utils;

/**
 * 用户信息 ThreadLocal 工具类
 * 在请求处理线程中存取当前登录用户 ID
 */
public class UserHolder {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    /** 存入当前用户 ID */
    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    /** 获取当前用户 ID */
    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    /** 清除当前线程的用户信息（防止内存泄漏） */
    public static void removeUserId() {
        USER_ID_HOLDER.remove();
    }
}
