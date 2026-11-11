"""配置中心：所有可变项都在 .env 里改，代码不写死"""
import os
from dotenv import load_dotenv

load_dotenv()

# ---- 大模型（OpenAI 兼容协议，支持商汤日日新/DeepSeek/智谱等任意兼容接口）----
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://token.sensenova.cn/v1")
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_MODEL = os.getenv("LLM_MODEL", "sensenova-6.8-flash-lite")

# ---- Java 业务服务直连地址（服务间调用走内网直连，不经网关，避免限流干扰）----
DEVICE_BASE_URL = os.getenv("DEVICE_BASE_URL", "http://127.0.0.1:8082")
ALARM_BASE_URL = os.getenv("ALARM_BASE_URL", "http://127.0.0.1:8083")

# ---- Agent 模式：auto=自动降级 / tool=仅工具调用 / react=仅 ReAct ----
AGENT_MODE = os.getenv("AGENT_MODE", "auto")

# ---- Agent 动作签名：计划令牌的 HMAC 密钥与有效期（秒）----
# 生产环境务必在 .env 中配置随机密钥
ACTION_SECRET = os.getenv("ACTION_SECRET", "charge-cloud-agent-action-secret")
ACTION_TTL = int(os.getenv("ACTION_TTL", "600"))

HTTP_TIMEOUT = float(os.getenv("HTTP_TIMEOUT", "15"))
