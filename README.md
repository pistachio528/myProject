# HMDP 秒杀系统 + 智能客服

一个完整的高并发秒杀系统，集成 AI 智能客服，包含后端、前端和基础设施配置。

## 项目总览

```
├── hmdp-seckill/          # 秒杀后端（Spring Boot + Redis + RabbitMQ）
├── hmdp-ai-chat/          # 智能客服后端（Spring Boot + Milvus + DeepSeek）
├── seckill-frontend/      # 前端（Vue 3 + Vite）
├── docker-compose.yml     # 基础设施（MySQL、Redis、RabbitMQ、Milvus）
├── jmeter/                # 压测脚本
```

---

## 一、hmdp-seckill — 秒杀后端

```
hmdp-seckill/
├── src/main/java/com/hmdp/
│   ├── HmdpSeckillApplication.java        # 启动类
│   ├── config/
│   │   ├── AsyncConfig.java               # 异步线程池配置
│   │   ├── MvcConfig.java                 # MVC 拦截器注册
│   │   ├── RabbitMQConfig.java            # RabbitMQ 队列/交换机声明
│   │   ├── RedissonConfig.java            # Redisson 分布式锁配置
│   │   ├── SeckillPreheatRunner.java      # 启动时预热：库存加载到 Redis
│   │   └── VoucherBloomFilter.java        # 布隆过滤器：拦截无效券ID
│   ├── controller/
│   │   ├── SeckillController.java         # 秒杀入口（抢购、查询结果）
│   │   ├── VoucherController.java         # 优惠券 CRUD
│   │   ├── UserController.java            # 用户登录/注册
│   │   └── ReservationController.java     # 预约功能
│   ├── service/
│   │   ├── SeckillService.java            # 秒杀核心接口
│   │   ├── StockManager.java             # 库存管理接口（Redis Lua 预扣）
│   │   ├── OrderService.java             # 订单创建/查询
│   │   ├── LockManager.java              # 分布式锁接口
│   │   ├── IdempotencyGuard.java         # 幂等性保障接口
│   │   ├── MessageRelay.java             # 本地消息表 → MQ 投递
│   │   ├── VoucherService.java           # 优惠券业务
│   │   ├── UserService.java              # 用户业务
│   │   ├── ReservationService.java       # 预约业务
│   │   └── impl/
│   │       ├── SeckillServiceImpl.java    # 秒杀核心实现（限流→预扣→异步下单）
│   │       ├── StockManagerImpl.java      # Redis Lua 脚本扣库存
│   │       ├── OrderServiceImpl.java      # RabbitMQ 消费者，落库创建订单
│   │       ├── LockManagerImpl.java       # Redisson 分布式锁实现
│   │       ├── IdempotencyGuardImpl.java  # Redis 幂等令牌实现
│   │       ├── MessageRelayImpl.java      # 本地消息表扫描 + 重投
│   │       ├── MessageTransactionService.java  # 事务消息（本地消息表写入）
│   │       ├── VoucherServiceImpl.java
│   │       ├── UserServiceImpl.java
│   │       └── ReservationServiceImpl.java
│   ├── strategy/                          # 限购策略（策略模式）
│   │   ├── LimitStrategy.java            # 策略接口
│   │   ├── LimitStrategyFactory.java     # 策略工厂
│   │   └── impl/
│   │       ├── OneOrderPerUserStrategy.java   # 每人限购1单
│   │       ├── OneDailyOrderStrategy.java     # 每天限购1单
│   │       └── NOrdersPerUserStrategy.java    # 每人限购N单
│   ├── interceptor/
│   │   └── LoginInterceptor.java          # Token 校验拦截器
│   ├── job/
│   │   ├── ReconciliationJob.java         # 定时对账（Redis vs DB 库存）
│   │   └── ReservationAutoConfirmJob.java # 预约自动确认
│   ├── entity/                            # 数据库实体
│   ├── dto/                               # 数据传输对象
│   ├── enums/                             # 枚举（限购类型、订单状态等）
│   ├── mapper/                            # MyBatis-Plus Mapper
│   ├── handler/
│   │   └── GlobalExceptionHandler.java    # 全局异常处理
│   └── utils/
│       └── UserHolder.java                # ThreadLocal 存储当前用户
├── src/main/resources/
│   ├── application.yml                    # 配置文件
│   ├── lua/
│   │   └── seckill_deduct.lua            # ⭐ Redis Lua 原子扣库存脚本
│   ├── db/migration/
│   │   └── V1__init.sql                  # Flyway 数据库初始化
│   └── mapper/
│       └── LocalMessageMapper.xml         # 本地消息表 SQL
├── Dockerfile                             # Docker 镜像构建
└── pom.xml
```

### 核心流程快速定位

| 想看什么 | 去哪里找 |
|---------|---------|
| 秒杀抢购入口 | `controller/SeckillController.java` |
| 秒杀核心逻辑（限流→预扣→下单） | `service/impl/SeckillServiceImpl.java` |
| Redis Lua 原子扣库存 | `resources/lua/seckill_deduct.lua` |
| RabbitMQ 异步下单消费 | `service/impl/OrderServiceImpl.java` |
| 分布式锁 | `service/impl/LockManagerImpl.java` |
| 幂等性保障 | `service/impl/IdempotencyGuardImpl.java` |
| 本地消息表（可靠投递） | `service/impl/MessageRelayImpl.java` |
| 限购策略 | `strategy/impl/` 目录 |
| 库存预热 | `config/SeckillPreheatRunner.java` |
| 布隆过滤器 | `config/VoucherBloomFilter.java` |
| 对账任务 | `job/ReconciliationJob.java` |

---

## 二、hmdp-ai-chat — 智能客服后端

```
hmdp-ai-chat/
├── src/main/java/com/hmdp/ai/
│   ├── AiChatApplication.java             # 启动类
│   ├── config/
│   │   ├── AiProperties.java             # LLM/RAG/限流等配置属性类
│   │   ├── MilvusConfig.java             # Milvus 向量数据库连接配置
│   │   ├── MilvusProperties.java         # Milvus 配置属性
│   │   ├── RestTemplateConfig.java       # HTTP 客户端配置
│   │   └── WebClientConfig.java          # WebClient 响应式 HTTP 配置
│   ├── controller/
│   │   ├── ChatController.java           # ⭐ 聊天入口（SSE 流式 + 普通）
│   │   ├── KnowledgeController.java      # 知识库管理接口
│   │   └── HealthController.java         # 健康检查
│   ├── engine/                            # AI 引擎层
│   │   ├── IntentResolver.java           # 意图识别接口
│   │   ├── LLMEngine.java               # LLM 调用接口
│   │   ├── RAGModule.java               # RAG 检索接口
│   │   ├── FunctionCallingEngine.java    # Function Calling 接口
│   │   ├── QualityGuard.java            # 回复质量检查接口
│   │   └── impl/
│   │       ├── IntentResolverImpl.java    # ⭐ 意图识别实现（LLM 分类）
│   │       ├── OpenAILLMEngine.java       # ⭐ LLM 调用实现（硅基流动 API）
│   │       ├── MilvusRAGModule.java       # ⭐ RAG 实现（Milvus 向量检索）
│   │       ├── FunctionCallingEngineImpl.java  # Function Calling 实现
│   │       └── QualityGuardImpl.java      # 回复质量校验实现
│   ├── service/                           # 业务服务层
│   │   ├── ConversationManager.java      # 对话上下文管理接口
│   │   ├── EmbeddingService.java         # 文本向量化服务
│   │   ├── ContentFilter.java           # 内容安全过滤
│   │   ├── RateLimiter.java             # 限流接口
│   │   ├── SeckillClient.java           # 调用秒杀系统（查库存/订单）
│   │   ├── TokenCounter.java            # Token 计数
│   │   ├── KnowledgeIndexStartupRunner.java  # 启动时加载知识库到 Milvus
│   │   └── impl/
│   │       ├── ConversationManagerImpl.java   # Redis 存储对话历史
│   │       └── RateLimiterImpl.java           # Redis 滑动窗口限流
│   ├── domain/                            # 领域模型
│   │   ├── ChatMessage.java              # 聊天消息
│   │   ├── DocumentChunk.java            # 文档分块
│   │   ├── KnowledgeDocument.java        # 知识文档
│   │   ├── UserContext.java              # 用户上下文
│   │   ├── dto/                          # 请求/响应 DTO
│   │   │   ├── SendMessageDTO.java       # 发送消息请求
│   │   │   ├── IntentResult.java         # 意图识别结果
│   │   │   ├── LLMRequest.java           # LLM 请求体
│   │   │   ├── LLMResponse.java          # LLM 响应体
│   │   │   ├── FunctionCall.java         # 函数调用定义
│   │   │   ├── FunctionResult.java       # 函数调用结果
│   │   │   ├── ToolDefinition.java       # 工具定义
│   │   │   ├── QualityCheckResult.java   # 质量检查结果
│   │   │   ├── ResolveContext.java       # 意图解析上下文
│   │   │   ├── FeedbackDTO.java          # 用户反馈
│   │   │   └── ValidationResult.java     # 校验结果
│   │   ├── entity/                       # 数据库实体
│   │   └── vo/                           # 视图对象
│   └── mapper/                           # MyBatis Mapper
├── src/main/resources/
│   ├── application.yml                   # 配置（⚠️ 不上传，用 .example）
│   ├── application.yml.example           # 配置模板（上传到 GitHub）
│   ├── knowledge/                        # ⭐ RAG 知识库文档
│   │   ├── product.md                    # 产品介绍
│   │   ├── rules.md                      # 业务规则
│   │   ├── faq.md                        # 常见问题
│   │   └── reservation.md               # 预约说明
│   ├── db/migration/                     # Flyway 数据库迁移
│   └── mapper/                           # MyBatis XML
└── pom.xml
```

### 核心流程快速定位

| 想看什么 | 去哪里找 |
|---------|---------|
| 聊天 API 入口（SSE 流式） | `controller/ChatController.java` |
| 意图识别逻辑 | `engine/impl/IntentResolverImpl.java` |
| LLM 调用（硅基流动/DeepSeek） | `engine/impl/OpenAILLMEngine.java` |
| RAG 向量检索 | `engine/impl/MilvusRAGModule.java` |
| Function Calling（查库存/下单） | `engine/impl/FunctionCallingEngineImpl.java` |
| 对话历史管理 | `service/impl/ConversationManagerImpl.java` |
| 知识库加载到 Milvus | `service/KnowledgeIndexStartupRunner.java` |
| 限流实现 | `service/impl/RateLimiterImpl.java` |
| 知识库原始文档 | `resources/knowledge/` 目录 |

---

## 三、seckill-frontend — 前端

```
seckill-frontend/
├── src/
│   ├── main.js                           # 入口文件
│   ├── App.vue                           # 根组件
│   ├── router/
│   │   └── index.js                      # 路由配置
│   ├── api/                              # 后端 API 封装
│   │   ├── http.js                       # Axios 实例（拦截器、Token 注入）
│   │   ├── user.js                       # 用户登录/注册 API
│   │   ├── seckill.js                    # 秒杀相关 API（抢购、查结果）
│   │   ├── voucher.js                    # 优惠券列表 API
│   │   └── chat.js                       # 智能客服 API（SSE 流式）
│   ├── views/                            # 页面
│   │   ├── LoginView.vue                 # 登录页
│   │   ├── VoucherListView.vue           # 优惠券列表页
│   │   ├── SeckillView.vue              # ⭐ 秒杀抢购页（倒计时+抢购按钮）
│   │   ├── OrderStatusView.vue          # 订单状态轮询页
│   │   └── MyOrdersView.vue             # 我的订单页
│   ├── components/
│   │   └── chat/                         # 智能客服组件
│   │       ├── ChatWidget.vue            # 客服悬浮入口
│   │       ├── ChatPanel.vue             # 聊天面板容器
│   │       ├── ChatInput.vue             # 输入框
│   │       ├── MessageList.vue           # 消息列表
│   │       ├── MessageBubble.vue         # 单条消息气泡
│   │       ├── FeedbackButton.vue        # 反馈按钮（👍👎）
│   │       └── composables/              # 组合式函数
│   │           ├── useChat.js            # 聊天状态管理
│   │           ├── useSSE.js             # SSE 流式接收
│   │           └── useAuth.js            # 认证状态
│   └── assets/
│       └── main.css                      # 全局样式
├── index.html
├── vite.config.js                        # Vite 配置（代理后端）
└── package.json
```

### 核心流程快速定位

| 想看什么 | 去哪里找 |
|---------|---------|
| 秒杀抢购页面 | `views/SeckillView.vue` |
| 倒计时 + 抢购按钮逻辑 | `views/SeckillView.vue` |
| 订单状态轮询 | `views/OrderStatusView.vue` |
| 智能客服聊天面板 | `components/chat/ChatPanel.vue` |
| SSE 流式接收 | `components/chat/composables/useSSE.js` |
| Axios 请求封装 + Token | `api/http.js` |
| 路由配置 | `router/index.js` |

---

## 四、docker-compose.yml — 基础设施

| 服务 | 端口 | 用途 |
|------|------|------|
| MySQL 8.0 | 3306 | 主数据库（订单、用户、优惠券） |
| Redis 7.2 | 6379 | 库存预扣、分布式锁、限流、对话缓存 |
| RabbitMQ 3.13 | 5672 / 15672 | 异步下单消息队列（15672 管理界面） |
| Milvus 2.3 | 19530 | 向量数据库（RAG 知识检索） |
| MinIO | 9001 | Milvus 对象存储后端 |
| etcd | - | Milvus 元数据存储 |

---

## 五、jmeter — 压测脚本

```
jmeter/
├── generate_tokens.sh                    # 批量生成测试用户 Token
├── View Results Tree-秒杀接口.jmx       # 正常秒杀压测
├── seckill-abnormal-test.jmx            # 异常场景压测
├── postman-seckill-abnormal.json        # Postman 异常测试集合
└── abnormal-test.sh                     # 异常测试脚本
```

---


## 快速启动

### 1. 启动基础设施

```bash
docker-compose up -d
```

### 2. 启动秒杀后端（端口 8081）

```bash
cd hmdp-seckill
mvn spring-boot:run
```

### 3. 启动智能客服后端（端口 8082）

```bash
cd hmdp-ai-chat

# ⚠️ 首次运行必须先配置：
#   cp src/main/resources/application.yml.example src/main/resources/application.yml
#   然后编辑 application.yml，填入你的硅基流动 API Key
#   获取地址：https://cloud.siliconflow.cn → 个人中心 → API密钥

mvn spring-boot:run
```

### 4. 启动前端（端口 5173）

```bash
cd seckill-frontend
npm install
npm run dev
```

---

## 技术栈

| 模块 | 技术 |
|------|------|
| 秒杀后端 | Spring Boot 3、Redis Lua 原子扣库存、RabbitMQ 异步下单、Redisson 分布式锁、Flyway、MyBatis-Plus |
| 智能客服 | Spring Boot 3、DeepSeek V3（硅基流动）、Milvus 向量检索（RAG）、SSE 流式输出、Function Calling |
| 前端 | Vue 3 Composition API、Vite 5、Axios、Vue Router、SSE EventSource |
| 基础设施 | Docker Compose、MySQL 8.0、Redis 7.2（混合持久化）、RabbitMQ 3.13、Milvus 2.3 |
| 压测 | JMeter |
