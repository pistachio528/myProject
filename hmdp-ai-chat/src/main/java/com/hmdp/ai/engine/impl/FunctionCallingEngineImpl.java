package com.hmdp.ai.engine.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.ai.domain.UserContext;
import com.hmdp.ai.domain.dto.FunctionCall;
import com.hmdp.ai.domain.dto.FunctionResult;
import com.hmdp.ai.domain.dto.ToolDefinition;
import com.hmdp.ai.engine.FunctionCallingEngine;
import com.hmdp.ai.service.SeckillClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionCallingEngineImpl implements FunctionCallingEngine {

    private final SeckillClient seckillClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<ToolDefinition> getAvailableTools() {
        return Arrays.asList(
                buildTool("query_order_status", "查询用户的秒杀订单状态。适用于：查询订单进度、查看抢购结果、订单是否成功等场景。user_id 由系统自动填充，无需用户提供。",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "order_token", Map.of("type", "string", "description", "订单 token（可选，不提供则查询最近订单）"),
                                        "user_id", Map.of("type", "integer", "description", "用户ID，系统自动填充，无需用户提供")
                                )
                        )),
                buildTool("query_voucher_info", "查询优惠券/秒杀活动的详细信息，包括价格、时间、说明等。适用于：查询某张券的详情、了解某个活动的具体信息。",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "voucher_id", Map.of("type", "integer", "description", "优惠券ID"),
                                        "voucher_name", Map.of("type", "string", "description", "优惠券名称（模糊匹配）")
                                )
                        )),
                buildTool("query_stock_status", "查询优惠券库存状态",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "voucher_id", Map.of("type", "integer", "description", "优惠券ID")
                                ),
                                "required", List.of("voucher_id")
                        )),
                buildTool("list_active_seckill", "列出当前进行中的秒杀活动",
                        Map.of("type", "object", "properties", Map.of())),
                buildTool("list_user_vouchers", "查询当前用户购买/抢购/持有的优惠券列表。适用于：查看我买了什么、我抢购的东西、我的订单里有哪些券、我持有哪些优惠券等场景。user_id 由系统自动填充，无需用户提供。",
                        Map.of("type", "object", "properties", Map.of(
                                "user_id", Map.of("type", "integer", "description", "用户ID，系统自动填充")
                        ))),
                buildTool("list_vouchers_with_stock", "查询当前有库存（stock > 0）的优惠券列表，用于回答\"还有哪些券有库存\"、\"哪些券还能买\"等问题。",
                        Map.of("type", "object", "properties", Map.of())),
                buildTool("list_ongoing_vouchers", "查询活动时间还在进行中的优惠券列表（beginTime <= 当前时间 <= endTime），用于回答\"哪些活动还没结束\"、\"现在还能参与哪些活动\"等问题。",
                        Map.of("type", "object", "properties", Map.of())),
                buildTool("search_voucher_by_name", "根据优惠券名称关键词搜索优惠券信息，返回匹配的优惠券列表（含价格、库存、活动时间等）。适用于：用户说出券名但不知道ID时，如\"帮我查一下新人券\"、\"周末特惠券是什么\"等场景。必须由用户提供券名关键词，不得自行猜测。",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "keyword", Map.of("type", "string", "description", "优惠券名称关键词，支持模糊匹配，必须由用户提供")
                                ),
                                "required", List.of("keyword")
                        )),
                buildTool("create_reservation", "帮用户预约餐厅就餐。需要用户提供：商家名称、预约日期（yyyy-MM-dd）、预约时间（HH:mm）、就餐人数。备注为可选项。user_id 由系统自动填充。",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "shop_name",    Map.of("type", "string",  "description", "商家名称，必须由用户提供"),
                                        "reserve_date", Map.of("type", "string",  "description", "预约日期，格式 yyyy-MM-dd，如 2026-05-20"),
                                        "reserve_time", Map.of("type", "string",  "description", "预约时间，格式 HH:mm，如 18:30"),
                                        "people_count", Map.of("type", "integer", "description", "就餐人数，1-20之间"),
                                        "remark",       Map.of("type", "string",  "description", "备注，如靠窗位置、需要儿童椅等，可选"),
                                        "user_id",      Map.of("type", "integer", "description", "用户ID，系统自动填充")
                                ),
                                "required", List.of("shop_name", "reserve_date", "reserve_time", "people_count")
                        )),
                buildTool("cancel_reservation", "取消用户的餐厅预约。需要预约ID，可先调用 list_reservations 获取。user_id 由系统自动填充。",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "reservation_id", Map.of("type", "integer", "description", "预约ID，可通过查询预约列表获取"),
                                        "user_id",        Map.of("type", "integer", "description", "用户ID，系统自动填充")
                                ),
                                "required", List.of("reservation_id")
                        )),
                buildTool("list_reservations", "查询用户的餐厅预约列表，包含预约状态（待确认/已确认/已完成）。user_id 由系统自动填充。",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "user_id", Map.of("type", "integer", "description", "用户ID，系统自动填充")
                                )
                        ))
        );
    }

    @Override
    public FunctionCall autoCompleteParams(FunctionCall functionCall, UserContext userContext) {
        Map<String, Object> args = functionCall.getArguments() != null
                ? new HashMap<>(functionCall.getArguments())
                : new HashMap<>();

        // 自动注入 user_id
        if (!args.containsKey("user_id") && userContext.getUserId() != null) {
            args.put("user_id", userContext.getUserId());
        }

        // 自动填充 order_token（如果上下文中有最近订单）
        if (!args.containsKey("order_token")
                && userContext.getRecentOrderTokens() != null
                && !userContext.getRecentOrderTokens().isEmpty()) {
            args.put("order_token", userContext.getRecentOrderTokens().get(0));
        }

        // 自动填充 voucher_id（如果上下文中有最近优惠券）
        if (!args.containsKey("voucher_id")
                && userContext.getRecentVoucherIds() != null
                && !userContext.getRecentVoucherIds().isEmpty()) {
            // 仅对 query_stock_status 和 query_voucher_info 自动填充
            String funcName = functionCall.getName();
            if ("query_stock_status".equals(funcName) || "query_voucher_info".equals(funcName)) {
                args.put("voucher_id", userContext.getRecentVoucherIds().get(0));
            }
        }

        return FunctionCall.builder()
                .name(functionCall.getName())
                .arguments(args)
                .rawArguments(functionCall.getRawArguments())
                .build();
    }

    @Override
    public FunctionResult execute(FunctionCall functionCall, UserContext userContext) {
        // 1. 参数自动补全
        FunctionCall completedCall = autoCompleteParams(functionCall, userContext);

        // 2. 用户身份校验：确保 user_id 与当前用户一致
        Map<String, Object> args = completedCall.getArguments();
        if (args.containsKey("user_id")) {
            Object callUserId = args.get("user_id");
            Long contextUserId = userContext.getUserId();
            if (contextUserId != null && callUserId != null) {
                long callUserIdLong = ((Number) callUserId).longValue();
                if (callUserIdLong != contextUserId) {
                    log.warn("User ID mismatch: call={}, context={}", callUserIdLong, contextUserId);
                    // 强制使用当前用户 ID（安全关键：防止越权查询）
                    args.put("user_id", contextUserId);
                }
            }
        }

        // 3. 路由到对应业务方法
        try {
            String funcName = completedCall.getName();
            Object resultData;

            switch (funcName) {
                case "query_order_status": {
                    Long userId = getLong(args, "user_id");
                    String orderToken = getString(args, "order_token");
                    resultData = seckillClient.queryOrderStatus(userId, orderToken);
                    break;
                }
                case "query_voucher_info": {
                    Long voucherId = getLong(args, "voucher_id");
                    String voucherName = getString(args, "voucher_name");
                    resultData = seckillClient.queryVoucherInfo(voucherId, voucherName);
                    break;
                }
                case "query_stock_status": {
                    Long voucherId = getLong(args, "voucher_id");
                    if (voucherId == null) {
                        return FunctionResult.builder()
                                .functionName(funcName)
                                .success(false)
                                .errorCode("MISSING_PARAM")
                                .errorMessage("缺少必填参数：voucher_id")
                                .build();
                    }
                    resultData = seckillClient.queryStockStatus(voucherId);
                    break;
                }
                case "list_active_seckill": {
                    resultData = seckillClient.listActiveSeckill();
                    break;
                }
                case "list_user_vouchers": {
                    Long userId = getLong(args, "user_id");
                    if (userId == null) userId = userContext.getUserId();
                    resultData = seckillClient.listUserVouchers(userId, userContext.getAuthToken());
                    break;
                }
                case "list_vouchers_with_stock": {
                    resultData = seckillClient.listVouchersWithStock();
                    break;
                }
                case "list_ongoing_vouchers": {
                    resultData = seckillClient.listOngoingVouchers();
                    break;
                }
                case "search_voucher_by_name": {
                    String keyword = getString(args, "keyword");
                    if (keyword == null || keyword.isBlank()) {
                        return FunctionResult.builder()
                                .functionName(funcName)
                                .success(false)
                                .errorCode("MISSING_PARAM")
                                .errorMessage("请告诉我您想查询哪张优惠券的名称？")
                                .build();
                    }
                    resultData = seckillClient.searchVoucherByName(keyword);
                    break;
                }
                case "create_reservation": {
                    Long userId = getLong(args, "user_id");
                    if (userId == null) userId = userContext.getUserId();
                    String shopName   = getString(args, "shop_name");
                    String date       = getString(args, "reserve_date");
                    String time       = getString(args, "reserve_time");
                    Integer people    = args.get("people_count") != null
                            ? ((Number) args.get("people_count")).intValue() : null;
                    String remark     = getString(args, "remark");
                    if (shopName == null || date == null || time == null || people == null) {
                        return FunctionResult.builder()
                                .functionName(funcName).success(false)
                                .errorCode("MISSING_PARAM")
                                .errorMessage("预约需要提供：商家名称、预约日期（yyyy-MM-dd）、预约时间（HH:mm）、就餐人数")
                                .build();
                    }
                    resultData = seckillClient.createReservation(userId, shopName, date, time, people, remark,
                            userContext.getAuthToken());
                    break;
                }
                case "cancel_reservation": {
                    Long userId = getLong(args, "user_id");
                    if (userId == null) userId = userContext.getUserId();
                    Long reservationId = getLong(args, "reservation_id");
                    if (reservationId == null) {
                        return FunctionResult.builder()
                                .functionName(funcName).success(false)
                                .errorCode("MISSING_PARAM")
                                .errorMessage("请提供要取消的预约ID，可先查询您的预约列表获取")
                                .build();
                    }
                    resultData = seckillClient.cancelReservation(reservationId, userId,
                            userContext.getAuthToken());
                    break;
                }
                case "list_reservations": {
                    Long userId = getLong(args, "user_id");
                    if (userId == null) userId = userContext.getUserId();
                    resultData = seckillClient.listReservations(userId, userContext.getAuthToken());
                    break;
                }
                default:
                    return FunctionResult.builder()
                            .functionName(funcName)
                            .success(false)
                            .errorCode("UNKNOWN_FUNCTION")
                            .errorMessage("未知的工具函数：" + funcName)
                            .build();
            }

            String dataJson = objectMapper.writeValueAsString(resultData);
            return FunctionResult.builder()
                    .functionName(funcName)
                    .success(true)
                    .data(dataJson)
                    .build();

        } catch (Exception e) {
            log.error("Function call execution failed [{}]: {}", completedCall.getName(), e.getMessage(), e);
            return FunctionResult.builder()
                    .functionName(completedCall.getName())
                    .success(false)
                    .errorCode("FUNCTION_CALL_FAILED")
                    .errorMessage("工具调用失败：" + e.getMessage())
                    .build();
        }
    }

    // ---- 辅助方法 ----

    private ToolDefinition buildTool(String name, String description, Map<String, Object> parameters) {
        return ToolDefinition.builder()
                .type("function")
                .function(ToolDefinition.FunctionDef.builder()
                        .name(name)
                        .description(description)
                        .parameters(parameters)
                        .build())
                .build();
    }

    private Long getLong(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null) return null;
        return ((Number) val).longValue();
    }

    private String getString(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val != null ? val.toString() : null;
    }
}
