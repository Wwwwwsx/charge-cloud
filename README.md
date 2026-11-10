# charge-cloud · 充电桩运营微服务云平台（含 AI 分析）

面向充电站运营场景的**多租户微服务系统**：充电桩资产管理、告警监控、AI 自然语言查数与告警日报。
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
                        │   Tool Calling Agent（4 个工具，ReAct 降级）    │
                        │   告警日报生成  ·  自然语言查数                  │
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
# 配置 .env（LLM_API_KEY 等）
.venv/bin/uvicorn app.main:app --port 8085 &
```

## 演示账号

| 账号 | 密码 | 租户 | 用途 |
|---|---|---|---|
| admin | admin123 | 1 星星充电 | 主演示（6 台设备） |
| operator1 | op123456 | 1 | 普通角色 |
| operator2 | operator123 | 2 另一运营商 | 验证多租户隔离（2 台设备） |

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
