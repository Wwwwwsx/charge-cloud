"""LangChain Agent：把 Java 接口包装成 Tool，模型按需调用

两种实现（面试考点）：
1. Tool Calling Agent —— 模型原生输出结构化 tool_calls（推荐，准确）
2. ReAct Agent —— 模型输出 Thought/Action/Observation 文本，由框架解析
   （模型不支持原生工具调用时的降级方案）
AGENT_MODE=auto 时先试 1，失败自动降级 2。
"""
import json
from contextvars import ContextVar

from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_core.tools import tool
from langchain_openai import ChatOpenAI

from . import config, java_client

# 当前请求的用户 token：工具通过它携带登录态（线程安全的上下文变量）
_token: ContextVar[str] = ContextVar("token")


def _call(result: dict) -> str:
    """统一把客户端结果转成给模型看的文本"""
    if not result.get("ok"):
        return f"调用失败：{result.get('error')}"
    return json.dumps(result.get("data"), ensure_ascii=False, default=str)


# ===================== 4 个业务工具 =====================

@tool
def list_devices(status: str = "", station: str = "", keyword: str = "") -> str:
    """查询充电桩列表。可选参数：status=IDLE/CHARGING/FAULT/OFFLINE，station=站点名，keyword=编码或名称关键词。"""
    return _call(java_client.list_devices(_token.get(), status, station, keyword))


@tool
def get_device(code: str) -> str:
    """按设备编码查询单台充电桩详情，如 code=CHG-0001。"""
    return _call(java_client.get_device(_token.get(), code))


@tool
def list_alarms(level: str = "", status: str = "") -> str:
    """查询告警记录。可选参数：level=WARNING/CRITICAL，status=OPEN/HANDLED。"""
    return _call(java_client.list_alarms(_token.get(), level, status))


@tool
def alarm_stats() -> str:
    """获取告警统计：未处理总数、按级别分布、故障最多的站点 Top3。"""
    return _call(java_client.alarm_stats(_token.get()))


TOOLS = [list_devices, get_device, list_alarms, alarm_stats]

SYSTEM_PROMPT = """你是充电桩运营平台的智能分析助手「充电小管家」。

工作守则：
1. 回答业务数据问题时，必须先调用工具查询，禁止凭记忆编造数字；
2. 工具返回失败时，如实告知用户失败原因，不要编造结果；
3. 回答用简体中文，简洁清晰，涉及数字时给出出处（来自哪个工具）；
4. 与充电桩无关的闲聊可以正常回应，但涉及平台数据一律走工具。
"""


def _model() -> ChatOpenAI:
    return ChatOpenAI(
        base_url=config.LLM_BASE_URL,
        api_key=config.LLM_API_KEY,
        model=config.LLM_MODEL,
        temperature=0,
        timeout=config.HTTP_TIMEOUT,
    )


def _build_tool_agent():
    """标准 Tool Calling Agent（模型原生支持 function calling 时使用）"""
    from langchain.agents import AgentExecutor, create_tool_calling_agent

    prompt = ChatPromptTemplate.from_messages([
        ("system", SYSTEM_PROMPT),
        MessagesPlaceholder("chat_history", optional=True),
        ("human", "{input}"),
        MessagesPlaceholder("agent_scratchpad"),
    ])
    agent = create_tool_calling_agent(_model(), TOOLS, prompt)
    return AgentExecutor(agent=agent, tools=TOOLS, handle_parsing_errors=True, max_iterations=6)


def _build_react_agent():
    """ReAct 降级方案：不依赖模型原生工具调用能力"""
    from langchain.agents import AgentExecutor, create_react_agent

    prompt = ChatPromptTemplate.from_messages([
        ("system", SYSTEM_PROMPT + """
你必须按以下格式思考和行动：

Thought: 我需要先做什么
Action: 工具名
Action Input: 工具参数
Observation: 工具返回结果
...（可以多轮）
Thought: 我已经拿到数据，可以回答了
Final Answer: 给用户的最终回答

可用工具：{tools}
工具名列表：{tool_names}
"""),
        ("human", "{input}"),
        MessagesPlaceholder("agent_scratchpad"),
    ])
    agent = create_react_agent(_model(), TOOLS, prompt)
    return AgentExecutor(agent=agent, tools=TOOLS, handle_parsing_errors=True, max_iterations=6)


_executor = None
_executor_mode = None


def _executor_for(mode: str):
    """按模式构建/缓存执行器"""
    global _executor, _executor_mode
    if _executor is None or _executor_mode != mode:
        _executor = _build_react_agent() if mode == "react" else _build_tool_agent()
        _executor_mode = mode
    return _executor


def run_agent(question: str, token: str) -> str:
    """执行一轮问答。auto 模式下工具调用失败自动降级 ReAct。"""
    _token.set(token)
    mode = config.AGENT_MODE

    if mode in ("auto", "tool"):
        try:
            result = _executor_for("tool").invoke(
                {"input": question, "chat_history": []})
            return str(result.get("output", ""))
        except Exception as e:
            if mode == "tool":
                raise
            msg = str(e).lower()
            # 模型不支持工具调用（参数校验/400 类错误）→ 降级 ReAct
            if any(k in msg for k in ("tool", "function", "400", "invalid")):
                _reset()
                result = _executor_for("react").invoke(
                    {"input": question, "chat_history": []})
                return str(result.get("output", ""))
            raise

    result = _executor_for("react").invoke({"input": question, "chat_history": []})
    return str(result.get("output", ""))


def _reset():
    global _executor, _executor_mode
    _executor = None
    _executor_mode = None
