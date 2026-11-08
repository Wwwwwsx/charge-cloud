# 架构设计与关键决策（面试复习底稿）

## 1. 为什么这么拆服务？

按**业务边界 + 变更频率 + 权限域**拆分：

| 服务 | 职责 | 为什么独立 |
|---|---|---|
| gateway | 统一入口 | 横切关注点（鉴权/限流）下沉到入口，业务服务不重复实现 |
| auth | 身份认证 | 凭据处理是最高敏感域，独立后可单独加固/扩容 |
| device | 设备资产 | 核心业务实体，读多写少 |
| alarm | 告警监控 | 写入来源多（设备联动、规则引擎），与设备解耦后互不影响发布 |
| ai (Python) | 智能分析 | 技术栈不同（Python/LLM 生态），独立进程隔离依赖与故障 |

## 2. 一次请求的完整链路（以"查询设备列表"为例）

```
1. 客户端 → POST/GET :8080/devices  + Authorization: Bearer <jwt>
2. Gateway JwtAuthGlobalFilter（order=-100）
   ├─ 白名单？→ 放行（/auth/login 等）
   ├─ 解析 JWT 签名与过期 → 失败返回 401 JSON
   └─ 通过 → RequestRateLimiter（Redis 令牌桶，按 userId 计数）
3. 路由匹配 Path=/devices/** → lb://charge-device（Nacos 发现实例）
4. charge-device DeviceController
   ├─ JwtUtil.tenantOf(header) 解析出 tenantId
   └─ DeviceService.page(tenantId, ...) → SQL 自动拼 tenant_id 条件
5. 响应原路返回（统一 CommonResult 包装）
```

## 3. 关键设计点

### 3.1 多租户行级隔离
- 表级：所有业务表带 `tenant_id`，唯一键带租户前缀（uk_tenant_code）
- 鉴权级：JWT 声明 `tid`，每个服务从 token 解析租户，**不信任客户端传参**
- 越权处理：查不到与无权限统一返回 404，防止越权探测
- 演进方向：数据量大 → schema 隔离 / ShardingSphere 分片

### 3.2 服务间调用与认证（两种模式）
- **用户流量**：客户端 JWT 全程携带，各服务自行验签（无状态）
- **服务流量**（device→alarm）：调用方用共享密钥本地签发 `typ=service` 的短期 JWT，
  接收方校验 `typ` 声明 —— 不把用户 token 跨服务透传（职责清晰、可审计）
- 密钥管理：演示放 yml，生产放 Nacos 配置中心

### 3.3 网关限流（Redis 令牌桶）
- `replenishRate=20/s`，`burstCapacity=40`，key = 登录用户 userId（匿名退化为 IP）
- 多网关实例共享 Redis 计数 → 水平扩容后限流仍然准确
- 验收：突发 50 请求 → 40 个 200 + 10 个 429

### 3.4 故障联动（Feign 跨服务）
```
设备上报 status=FAULT
  → device 服务本地事务更新状态 + 清 Redis 缓存
  → Feign 调 alarm /alarms/internal（服务间 JWT）
  → alarm 校验 typ=service → 写入告警记录
  → 失败降级：只记日志，不影响状态上报（主流程优先）
```

### 3.5 Redis 缓存策略
- 设备详情按 `charge:device:{tenant}:{code}` 缓存 60s
- 更新/删除/状态变更时**主动失效**（Cache-Aside）
- 缓存结构轻量序列化，避免为一个详情引入重序列化依赖

### 3.6 AI 侧架构（Python + LangChain）
- **工具即接口**：4 个 @tool 函数内部就是 HTTP 调 Java 接口，
  用户 JWT 通过 ContextVar 贯穿工具调用 → 权限仍由 Java 侧把关
- **双模式 Agent**：
  - Tool Calling（主力）：模型原生输出 `tool_calls`，结构化、准确
  - ReAct（降级）：模型不支持 function calling 时自动切换，
    Thought/Action/Observation 文本协议由框架解析
- **诚实性约束**：System Prompt 要求"数据必须来自工具、失败如实告知"，
  拒绝模型编造数字 —— 与项目一（RAG 引用溯源）同一套方法论
- 日报链路：Java 聚合真实数据 → 拼进 Prompt → 模型只做"组织与表达"，不做数据计算

## 4. 踩坑记录（面试可以主动讲）

| 坑 | 现象 | 解法 |
|---|---|---|
| 网关/服务缺 loadbalancer | `NoLoadBalancerClientFilter` 直接 503 "Unable to find instance" | 显式引入 spring-cloud-starter-loadbalancer（Boot 3 时代常见坑） |
| Nacos 3 必填配置 | 启动卡在交互式追问 token.secret.key | 配 Base64 密钥 + server.identity key/value |
| 通用异常泄漏 | 直接抛堆栈 | 全局异常处理器统一 500 文案，堆栈只进日志 |
| 飞书/Feign 依赖 | — | 同上 loadbalancer |

## 5. 项目数据流全景

```
设备硬件/模拟上报 ──► device(状态) ──Feign──► alarm(记录) ──► 看板/日报
                          │                      │
                       Redis 缓存            SQL 聚合统计
                          │                      │
用户 ──► gateway ─────────┴──────────────────────┤
                                                  │
                              charge-ai(Python) ──┘  HTTP+JWT
                                   │
                              LangChain Agent
                                   │ tool_calls
                              商汤日日新大模型
```
