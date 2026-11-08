"""charge-ai：Python AI 分析服务（FastAPI + LangChain）
经网关路由为 /ai/**，端口 8085
"""
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel

from . import config, java_client
from .agent import run_agent

app = FastAPI(title="charge-ai", description="充电桩平台 AI 分析服务")


class ChatReq(BaseModel):
    question: str


@app.get("/ai/health")
def health():
    return {"status": "ok", "model": config.LLM_MODEL, "mode": config.AGENT_MODE}


@app.post("/ai/chat")
def chat(req: ChatReq, authorization: str = Header(default="")):
    """自然语言查数：设备/告警的查询与统计"""
    if not authorization:
        raise HTTPException(status_code=401, detail="缺少登录凭证")
    if not req.question or not req.question.strip():
        raise HTTPException(status_code=400, detail="问题不能为空")
    try:
        answer = run_agent(req.question.strip(), authorization)
        return {"code": 200, "msg": "success", "data": {"answer": answer}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"AI 处理失败: {e}")


@app.get("/ai/daily-report")
def daily_report(authorization: str = Header(default="")):
    """告警日报：拉取真实数据 → 大模型生成结构化日报"""
    if not authorization:
        raise HTTPException(status_code=401, detail="缺少登录凭证")

    result = java_client.daily_report_data(authorization)
    if not result.get("ok"):
        raise HTTPException(status_code=500, detail=result.get("error"))

    from langchain_openai import ChatOpenAI
    from langchain_core.messages import HumanMessage, SystemMessage

    stats = result["stats"]
    alarms = result["open_alarms"]
    alarm_lines = "\n".join(
        f"- [{a.get('level')}] {a.get('station')}/{a.get('deviceName')}: {a.get('content')}"
        for a in alarms
    ) or "- （无未处理告警）"

    model = ChatOpenAI(
        base_url=config.LLM_BASE_URL,
        api_key=config.LLM_API_KEY,
        model=config.LLM_MODEL,
        temperature=0,
        timeout=config.HTTP_TIMEOUT,
    )
    messages = [
        SystemMessage(content="你是充电桩运营平台的日报助手，用简体中文生成 Markdown 日报，"
                              "只基于给定数据，不要编造。结构：总体情况 / 重点关注 / 处置建议。"),
        HumanMessage(content=(
            f"今日告警统计：{stats}\n\n未处理告警明细：\n{alarm_lines}\n\n请生成告警日报。"
        )),
    ]
    report = model.invoke(messages).content
    return {"code": 200, "msg": "success", "data": {"report": report}}
