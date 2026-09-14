-- seckill_deduct.lua
-- 秒杀原子扣减脚本：时间窗口校验 + 库存扣减
-- 限购校验由上层 LimitStrategy 负责（已在调用此脚本前完成）
--
-- KEYS[1] = seckill:activity:{voucherId}   活动信息 Hash
-- KEYS[2] = seckill:stock:{voucherId}      库存计数 String
--
-- ARGV[1] = currentTimeMillis  当前时间戳（毫秒）
--
-- 返回值：
--   0 = 成功
--   1 = 活动不存在
--   2 = 活动未开始
--   3 = 活动已结束
--   4 = 库存不足
--   5 = 库存 key 不存在（需要从 DB 重建缓存）
--   6 = 库存为负数（数据异常，需要从 DB 重建缓存）

local activityKey = KEYS[1]
local stockKey    = KEYS[2]
local now         = tonumber(ARGV[1])

-- 1. 检查活动信息是否存在
local startTime = redis.call('HGET', activityKey, 'startTime')
if startTime == false then
    return 1  -- 活动不存在
end

-- 2. 时间窗口校验
local endTime = tonumber(redis.call('HGET', activityKey, 'endTime'))
startTime = tonumber(startTime)

if now < startTime then
    return 2  -- 活动未开始
end

if now > endTime then
    return 3  -- 活动已结束
end

-- 3. 库存校验（区分 key 不存在、负数异常、库存为 0）
local stock = redis.call('GET', stockKey)
if stock == false then
    return 5  -- 库存 key 不存在，需要从 DB 重建
end
stock = tonumber(stock)
if stock < 0 then
    return 6  -- 库存为负数（数据异常，需要从 DB 重建）
end
if stock < 1 then
    return 4  -- 库存不足（真正售罄）
end

-- 4. 原子扣减库存
local newStock = redis.call('DECRBY', stockKey, 1)

-- 5. 兜底：如果扣减后变成负数，说明并发竞争导致异常，回滚
if newStock < 0 then
    redis.call('INCRBY', stockKey, 1)
    return 4  -- 库存不足
end

return 0  -- 成功
