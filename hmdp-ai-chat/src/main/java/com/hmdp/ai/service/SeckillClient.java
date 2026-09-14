package com.hmdp.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 秒杀系统 HTTP 客户端
 * 调用秒杀系统 :8081 的接口获取业务数据
 *
 * 实际接口路径（以 hmdp-seckill 为准）：
 * - GET /api/voucher/list/{shopId}     列出店铺优惠券
 * - GET /api/seckill/order/status/{token}  查询订单状态
 * - GET /api/seckill/order/list        查询用户订单列表（需登录）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillClient {

    private final RestTemplate restTemplate;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    /**
     * 查询用户订单状态
     * GET /api/seckill/order/status/{token}
     */
    public Map<String, Object> queryOrderStatus(Long userId, String orderToken) {
        try {
            if (orderToken == null || orderToken.isBlank()) {
                return errorResult("请提供订单 token 以查询订单状态");
            }
            String url = aiProperties.getSeckill().getBaseUrl()
                    + "/api/seckill/order/status/" + orderToken;
            String response = restTemplate.getForObject(url, String.class);
            return parseResponse(response);
        } catch (Exception e) {
            log.error("Failed to query order status: {}", e.getMessage());
            return errorResult("查询订单状态失败：" + e.getMessage());
        }
    }

    /**
     * 查询优惠券详情（通过列表接口按名称或ID匹配）
     * GET /api/voucher/list/1
     */
    public Map<String, Object> queryVoucherInfo(Long voucherId, String voucherName) {
        try {
            // 获取所有优惠券列表，再按 id 或 name 匹配
            String url = aiProperties.getSeckill().getBaseUrl() + "/api/voucher/list/1";
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            // 兼容直接返回数组或包在 data 字段里
            JsonNode dataNode = root.isArray() ? root : root.path("data");
            if (!dataNode.isArray()) {
                return errorResult("暂无优惠券数据");
            }

            for (JsonNode item : dataNode) {
                boolean matchById = voucherId != null && item.path("id").asLong() == voucherId;
                boolean matchByName = voucherName != null && !voucherName.isBlank()
                        && item.path("title").asText("").contains(voucherName);
                if (matchById || matchByName) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", item.path("id").asLong());
                    result.put("title", item.path("title").asText());
                    result.put("subTitle", item.path("subTitle").asText());
                    result.put("payValue", String.format("%.2f", item.path("payValue").asLong() / 100.0));
                    result.put("actualValue", String.format("%.2f", item.path("actualValue").asLong() / 100.0));
                    result.put("stock", item.path("stock").asInt());
                    result.put("beginTime", item.path("beginTime").asText());
                    result.put("endTime", item.path("endTime").asText());
                    return result;
                }
            }
            return errorResult("未找到匹配的优惠券：" + (voucherName != null ? voucherName : "ID=" + voucherId));
        } catch (Exception e) {
            log.error("Failed to query voucher info: {}", e.getMessage());
            return errorResult("查询优惠券信息失败：" + e.getMessage());
        }
    }

    /**
     * 查询库存状态（从列表接口获取 stock 字段）
     * GET /api/voucher/list/1
     */
    public Map<String, Object> queryStockStatus(Long voucherId) {
        try {
            String url = aiProperties.getSeckill().getBaseUrl() + "/api/voucher/list/1";
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode dataNode = root.isArray() ? root : root.path("data");

            if (dataNode.isArray()) {
                for (JsonNode item : dataNode) {
                    if (item.path("id").asLong() == voucherId) {
                        int stock = item.path("stock").asInt();
                        String statusText;
                        if (stock <= 0) {
                            statusText = "售罄";
                        } else if (stock <= 10) {
                            statusText = "库存紧张";
                        } else {
                            statusText = "库存充足";
                        }
                        Map<String, Object> result = new HashMap<>();
                        result.put("voucherId", voucherId);
                        result.put("stock", stock);
                        result.put("stockStatus", statusText);
                        return result;
                    }
                }
            }
            return errorResult("未找到优惠券 ID=" + voucherId);
        } catch (Exception e) {
            log.error("Failed to query stock status for voucher {}: {}", voucherId, e.getMessage());
            return errorResult("查询库存状态失败：" + e.getMessage());
        }
    }

    /**
     * 列出进行中的秒杀活动
     * GET /api/voucher/list/1
     * 过滤：仅返回 beginTime <= 当前时间 <= endTime 的活动
     */
    public List<Map<String, Object>> listActiveSeckill() {
        try {
            String url = aiProperties.getSeckill().getBaseUrl() + "/api/voucher/list/1";
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode dataNode = root.isArray() ? root : root.path("data");

            List<Map<String, Object>> activeList = new ArrayList<>();
            if (!dataNode.isArray()) return activeList;

            LocalDateTime now = LocalDateTime.now();
            for (JsonNode item : dataNode) {
                String beginTimeStr = item.path("beginTime").asText(null);
                String endTimeStr = item.path("endTime").asText(null);
                if (beginTimeStr == null || endTimeStr == null) {
                    // 没有时间限制的优惠券也加入列表
                    Map<String, Object> activity = buildVoucherMap(item, beginTimeStr, endTimeStr);
                    activeList.add(activity);
                    continue;
                }
                try {
                    LocalDateTime beginTime = LocalDateTime.parse(beginTimeStr.replace(" ", "T"));
                    LocalDateTime endTime = LocalDateTime.parse(endTimeStr.replace(" ", "T"));
                    if (!now.isBefore(beginTime) && !now.isAfter(endTime)) {
                        activeList.add(buildVoucherMap(item, beginTimeStr, endTimeStr));
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse activity time: {}", e.getMessage());
                    activeList.add(buildVoucherMap(item, beginTimeStr, endTimeStr));
                }
            }
            return activeList;
        } catch (Exception e) {
            log.error("Failed to list active seckill: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 查询用户持有的优惠券列表（通过订单列表获取）
     * GET /api/seckill/order/list?userId={userId}
     */
    public List<Map<String, Object>> listUserVouchers(Long userId, String authToken) {
        try {
            String url = aiProperties.getSeckill().getBaseUrl()
                    + "/api/seckill/order/list?userId=" + userId;

            // 带上用户 token 调用，让秒杀系统正常鉴权
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            if (authToken != null && !authToken.isBlank()) {
                headers.set("Authorization", authToken);
            }
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
            String response = restTemplate.exchange(url,
                    org.springframework.http.HttpMethod.GET, entity, String.class).getBody();
            JsonNode root = objectMapper.readTree(response);

            // 获取订单列表
            JsonNode dataNode = root.path("data");
            if (!dataNode.isArray()) return new ArrayList<>();

            // 收集 voucherId 列表，去重
            List<Long> voucherIds = new ArrayList<>();
            for (JsonNode order : dataNode) {
                long voucherId = order.path("voucherId").asLong(0);
                if (voucherId > 0 && !voucherIds.contains(voucherId)) {
                    voucherIds.add(voucherId);
                }
            }

            if (voucherIds.isEmpty()) return new ArrayList<>();

            // 获取优惠券详情
            String voucherListUrl = aiProperties.getSeckill().getBaseUrl() + "/api/voucher/list/1";
            String voucherResponse = restTemplate.getForObject(voucherListUrl, String.class);
            JsonNode voucherRoot = objectMapper.readTree(voucherResponse);
            JsonNode voucherList = voucherRoot.isArray() ? voucherRoot : voucherRoot.path("data");

            List<Map<String, Object>> result = new ArrayList<>();
            if (voucherList.isArray()) {
                for (JsonNode voucher : voucherList) {
                    long vid = voucher.path("id").asLong(0);
                    if (voucherIds.contains(vid)) {
                        result.add(buildVoucherMap(voucher,
                                voucher.path("beginTime").asText(null),
                                voucher.path("endTime").asText(null)));
                    }
                }
            }

            // 如果优惠券详情里没有，直接返回 voucherId 列表
            if (result.isEmpty()) {
                for (Long vid : voucherIds) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("voucherId", vid);
                    result.add(item);
                }
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to list user vouchers for user {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 查询有库存的优惠券列表（stock > 0）
     * GET /api/voucher/list/1
     */
    public List<Map<String, Object>> listVouchersWithStock() {
        try {
            String url = aiProperties.getSeckill().getBaseUrl() + "/api/voucher/list/1";
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode dataNode = root.isArray() ? root : root.path("data");

            List<Map<String, Object>> result = new ArrayList<>();
            if (!dataNode.isArray()) return result;

            for (JsonNode item : dataNode) {
                int stock = item.path("stock").asInt(0);
                if (stock > 0) {
                    Map<String, Object> v = buildVoucherMap(item,
                            item.path("beginTime").asText(null),
                            item.path("endTime").asText(null));
                    // 加上库存状态文字
                    v.put("stockStatus", stock <= 10 ? "库存紧张" : "库存充足");
                    v.put("stock", stock);
                    result.add(v);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to list vouchers with stock: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 查询活动时间还在进行中的优惠券列表（beginTime <= now <= endTime）
     * GET /api/voucher/list/1
     */
    public List<Map<String, Object>> listOngoingVouchers() {
        try {
            String url = aiProperties.getSeckill().getBaseUrl() + "/api/voucher/list/1";
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode dataNode = root.isArray() ? root : root.path("data");

            List<Map<String, Object>> result = new ArrayList<>();
            if (!dataNode.isArray()) return result;

            LocalDateTime now = LocalDateTime.now();
            for (JsonNode item : dataNode) {
                String beginTimeStr = item.path("beginTime").asText(null);
                String endTimeStr = item.path("endTime").asText(null);
                if (beginTimeStr == null || endTimeStr == null) continue;
                try {
                    LocalDateTime beginTime = LocalDateTime.parse(beginTimeStr.replace(" ", "T"));
                    LocalDateTime endTime = LocalDateTime.parse(endTimeStr.replace(" ", "T"));
                    if (!now.isBefore(beginTime) && !now.isAfter(endTime)) {
                        Map<String, Object> v = buildVoucherMap(item, beginTimeStr, endTimeStr);
                        // 计算剩余时间（分钟）
                        long remainMinutes = java.time.Duration.between(now, endTime).toMinutes();
                        v.put("remainMinutes", remainMinutes);
                        v.put("remainDesc", remainMinutes < 60
                                ? remainMinutes + "分钟后结束"
                                : (remainMinutes / 60) + "小时后结束");
                        result.add(v);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse voucher time for id={}: {}", item.path("id").asLong(), e.getMessage());
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to list ongoing vouchers: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 创建餐厅预约
     * POST /api/reservation
     */
    public Map<String, Object> createReservation(Long userId, String shopName,
                                                   String reserveDate, String reserveTime,
                                                   Integer peopleCount, String remark,
                                                   String authToken) {
        try {
            String url = aiProperties.getSeckill().getBaseUrl() + "/api/reservation";
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("userId", userId);
            body.put("shopName", shopName);
            body.put("reserveDate", reserveDate);
            body.put("reserveTime", reserveTime);
            body.put("peopleCount", peopleCount);
            if (remark != null && !remark.isBlank()) {
                body.put("remark", remark);
            }
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            if (authToken != null && !authToken.isBlank()) {
                headers.set("Authorization", authToken);
            }
            org.springframework.http.HttpEntity<Map<String, Object>> entity =
                    new org.springframework.http.HttpEntity<>(body, headers);
            String response = restTemplate.postForObject(url, entity, String.class);
            return parseResponse(response);
        } catch (Exception e) {
            log.error("Failed to create reservation: {}", e.getMessage());
            return errorResult("预约失败：" + e.getMessage());
        }
    }

    /**
     * 取消餐厅预约
     * DELETE /api/reservation/{id}?userId={userId}
     */
    public Map<String, Object> cancelReservation(Long reservationId, Long userId, String authToken) {
        try {
            String url = aiProperties.getSeckill().getBaseUrl()
                    + "/api/reservation/" + reservationId + "?userId=" + userId;
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            if (authToken != null && !authToken.isBlank()) {
                headers.set("Authorization", authToken);
            }
            org.springframework.http.HttpEntity<Void> entity =
                    new org.springframework.http.HttpEntity<>(null, headers);
            String response = restTemplate.exchange(url,
                    org.springframework.http.HttpMethod.DELETE, entity, String.class).getBody();
            return parseResponse(response);
        } catch (Exception e) {
            log.error("Failed to cancel reservation {}: {}", reservationId, e.getMessage());
            return errorResult("取消预约失败：" + e.getMessage());
        }
    }

    /**
     * 查询用户的预约列表
     * GET /api/reservation/list?userId={userId}
     */
    public List<Map<String, Object>> listReservations(Long userId, String authToken) {
        try {
            String url = aiProperties.getSeckill().getBaseUrl()
                    + "/api/reservation/list?userId=" + userId;
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            if (authToken != null && !authToken.isBlank()) {
                headers.set("Authorization", authToken);
            }
            org.springframework.http.HttpEntity<Void> entity =
                    new org.springframework.http.HttpEntity<>(null, headers);
            String response = restTemplate.exchange(url,
                    org.springframework.http.HttpMethod.GET, entity, String.class).getBody();
            JsonNode root = objectMapper.readTree(response);
            JsonNode dataNode = root.path("data");
            List<Map<String, Object>> result = new ArrayList<>();
            if (dataNode.isArray()) {
                for (JsonNode item : dataNode) {
                    Map<String, Object> r = new java.util.LinkedHashMap<>();
                    r.put("id", item.path("id").asLong());
                    r.put("shopName", item.path("shopName").asText());
                    r.put("reserveDate", item.path("reserveDate").asText());
                    r.put("reserveTime", item.path("reserveTime").asText());
                    r.put("peopleCount", item.path("peopleCount").asInt());
                    r.put("remark", item.path("remark").asText(""));
                    int status = item.path("status").asInt();
                    r.put("status", status);
                    r.put("statusText", statusText(status));
                    result.add(r);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to list reservations for user {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    private String statusText(int status) {
        switch (status) {
            case 1: return "待确认";
            case 2: return "已确认";
            case 3: return "已取消";
            case 4: return "已完成";
            default: return "未知";
        }
    }

    /**
     * 根据名称关键词搜索优惠券（模糊匹配 title 字段）
     * GET /api/voucher/list/1
     */
    public List<Map<String, Object>> searchVoucherByName(String keyword) {
        try {
            String url = aiProperties.getSeckill().getBaseUrl() + "/api/voucher/list/1";
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode dataNode = root.isArray() ? root : root.path("data");

            List<Map<String, Object>> result = new ArrayList<>();
            if (!dataNode.isArray()) return result;

            String lowerKeyword = keyword.toLowerCase();
            for (JsonNode item : dataNode) {
                String title = item.path("title").asText("");
                String subTitle = item.path("subTitle").asText("");
                // 标题或副标题包含关键词则匹配
                if (title.toLowerCase().contains(lowerKeyword)
                        || subTitle.toLowerCase().contains(lowerKeyword)) {
                    Map<String, Object> v = buildVoucherMap(item,
                            item.path("beginTime").asText(null),
                            item.path("endTime").asText(null));
                    int stock = item.path("stock").asInt(0);
                    v.put("stock", stock);
                    v.put("stockStatus", stock <= 0 ? "售罄" : stock <= 10 ? "库存紧张" : "库存充足");
                    result.add(v);
                }
            }
            if (result.isEmpty()) {
                log.info("No voucher found for keyword: {}", keyword);
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to search voucher by name [{}]: {}", keyword, e.getMessage());
            return new ArrayList<>();
        }
    }

    private Map<String, Object> buildVoucherMap(JsonNode item, String beginTime, String endTime) {
        Map<String, Object> activity = new HashMap<>();
        activity.put("id", item.path("id").asLong());
        activity.put("title", item.path("title").asText());
        activity.put("subTitle", item.path("subTitle").asText());
        // 金额从分转换为元，方便 LLM 直接展示
        activity.put("payValue", String.format("%.2f", item.path("payValue").asLong() / 100.0));
        activity.put("actualValue", String.format("%.2f", item.path("actualValue").asLong() / 100.0));
        activity.put("stock", item.path("stock").asInt());
        if (beginTime != null) activity.put("beginTime", beginTime);
        if (endTime != null) activity.put("endTime", endTime);
        return activity;
    }

    private Map<String, Object> parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            Map<String, Object> result = new HashMap<>();
            root.fields().forEachRemaining(entry ->
                    result.put(entry.getKey(), entry.getValue().isTextual()
                            ? entry.getValue().asText()
                            : entry.getValue()));
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("raw", json);
            return result;
        }
    }

    private Map<String, Object> errorResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("error", message);
        return result;
    }
}
