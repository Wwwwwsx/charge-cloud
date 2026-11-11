# charge-cloud · 充电桩运营微服务云平台（含 AI 运营 Agent）

<<<<<<< HEAD
面向充电站运营场景的**多租户微服务系统**：充电桩资产管理、告警监控、AI 自然语言查数与告警日报。
模拟充电桩云端开发服务商面向多个运营商家使用的场景，各个运营商数据分离。

=======
面向充电站运营场景的**多租户微服务系统**：充电桩资产管理、告警监控、
AI 自然语言查数、告警日报与告警自动处置。
>>>>>>> 75119eb (feat: AI 升级为可处置告警的运营 Agent，批量处置增加签名确认)
在线体验平台：http://124.222.85.26:8080
## 架构总览

```
                        ┌──────────────────────────────────────────────┐
   用户/前端             │              Java 微服务（Spring Cloud）        │
  ──────────►  :8080    │                                              │
   统一入口    Gateway   │  ┌─────────┐  ┌──────────┐  ┌───────────┐    │
             JWT 鉴权   │  │ charge- │  │ charge-  │  │ charge-   │    │
             Redis 限流  │  │  auth   │  │ device   │  │  alarm    │    │
             路由转发    │  │  :8081  │  │  :8082   │  │  :8083    │    │
                        │  │ 注册登录 │  │ 设备CRUD  │  │ 规则/记录  │    │
                        │  │ 签发JWT │  │ Redis缓存 │  │ 统计聚合   │    │
                        │  └────┬────┘  └────┬─────┘  └─────┬─────┘    │
                        │       │           │  Feign 故障联动 │          │
                        │       │           └──────────────►│          │
                        │  ┌────┴──────────────────────────┴─────┐    │
                        │  │  Nacos :8848（注册中心） MySQL  Redis  │    │
                        │  └──────────────────────────────────────┘    │
                        └──────────────────┬───────────────────────────┘
                                           │ HTTP + 用户 JWT（服务间直连）
                        ┌──────────────────▼───────────────────────────┐
                        │   charge-ai  :8085  Python（FastAPI+LangChain）│
                        │   Tool Calling Agent（6 个工具，ReAct 降级）    │
                        │   查数/日报  ·  告警自动处置（规划-确认-执行）   │
                        └──────────────────┬───────────────────────────┘
                                           │ OpenAI 兼容协议
                                   ┌───────▼────────┐
                                   │ 商汤日日新大模型  │
                                   └─────────────────┘
```

## 技术栈

| 侧 | 技术 |
|---|---|
| Java 业务 | Java 17 · Spring Boot 3.5 · Spring Cloud 2025 · Nacos · Gateway · OpenFeign · LoadBalancer |
| 数据 | MySQL 8（MyBatis-Plus）· Redis（缓存 + 网关限流） |
| Python AI | FastAPI · LangChain 0.3 · Tool Calling Agent / ReAct 降级 |
| 模型 | 商汤日日新（OpenAI 兼容协议，可换 DeepSeek/智谱等任何兼容接口） |

## Agent 动作执行（Human-in-the-loop）

AI 不只是问答，能真正处置告警：

1. **单条处置**：Agent 调 `handle_alarm` 立即执行（低风险）；
2. **批量处置**：Agent 调 `plan_batch_handle` 生成计划 → 服务端 HMAC 签名令牌 → 前端展示计划卡片 → 用户点「确认执行」→ `POST /ai/actions/execute` 验签后逐条调用 Java 接口；
3. **安全约束**：动作白名单、令牌 10 分钟过期、防重放、单次最多 50 条；租户权限仍由 Java 按 JWT 强制校验；
4. **执行轨迹**：逐条返回成功/失败，前端展示并自动刷新告警列表。

```
用户："把未处理的 WARNING 告警都处理掉"
  → Agent 查询 → 生成处置计划（签名令牌）
  → 用户确认 → 验签 → 逐条 PUT /alarms/{id}/handle
  → 返回 {total, ok, failed, results[]}
```

## 端口规划

| 组件 | 端口 | 说明 |
|---|---|---|
| charge-gateway | 8080 | 统一入口（鉴权 + 限流） |
| charge-auth | 8081 | 认证服务 |
| charge-device | 8082 | 设备服务 |
| charge-alarm | 8083 | 告警服务 |
| charge-ai | 8085 | Python AI 服务 |
| Nacos | 8848 / 9090 | 注册中心 / 控制台 |
| MySQL / Redis | 3306 / 6379 | 数据库 / 缓存 |

## 快速启动

```bash
# 1. 中间件（本机已安装）
mysql -uroot -p123456 < sql/init.sql      # 初始化数据库
brew services start mysql redis
cd middleware/nacos && sh bin/startup.sh -m standalone   # Nacos

# 2. Java 服务（顺序无所谓）
mvn -DskipTests package
java -jar charge-auth/target/charge-auth-1.0.0.jar &
java -jar charge-device/target/charge-device-1.0.0.jar &
java -jar charge-alarm/target/charge-alarm-1.0.0.jar &
java -jar charge-gateway/target/charge-gateway-1.0.0.jar &

# 3. Python AI 服务
cd charge-ai && python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
# 配置 .env（LLM_API_KEY、ACTION_SECRET 等）
.venv/bin/uvicorn app.main:app --port 8085 &
```

## 演示账号

| 账号 | 密码 | 租户 | 用途 |
|---|---|---|---|
| admin | admin | 100 公开演示 | 演示账号（6 台设备与告警） |
| operator2 | operator123 | 2 另一运营商 | 验证多租户隔离（2 台设备） |

私有管理员账号不在仓库中公开，部署时单独在数据库中配置。

## 验收测试（全部通过）

1. 无 token 访问 → **401 拦截**
2. admin 登录 → 签发 JWT ✅
3. admin 查设备 → 6 台（租户1）✅
4. 租户2 查设备 → 2 台（**隔离生效**）✅
5. 告警列表/统计 → SQL 聚合正确 ✅
6. 设备上报 FAULT → **Feign 跨服务自动建告警**（4→5 条）✅
7. 网关限流 → 突发 50 请求产生 **10 个 429** ✅
8. AI 自然语言查数 → 真实数据 + 标注来源工具 ✅
9. AI 告警日报 → Markdown 结构化输出 ✅
10. Agent 单条处置告警 → 状态变为 HANDLED ✅
11. Agent 批量处置计划 → 确认执行 → 全部处理并返回执行轨迹 ✅
12. 篡改 / 过期 / 重复使用动作令牌 → **400 拒绝** ✅
