"""Agent 动作执行脚手架：白名单 + 签名令牌 + 批量执行

设计要点（面试考点）：
1. 动作白名单：只允许登记过的动作类型，模型和前端都无法构造任意操作；
2. 签名令牌：计划由服务端签名下发，前端确认时原样带回，
   验签通过才执行，防止前端篡改告警 id 或伪造计划；
3. 过期 + 防重放：令牌有有效期，执行后记入已用集合，重复提交直接拒绝；
4. 逐条执行：单条失败不中断，返回完整执行轨迹供前端展示。
"""
import base64
import hashlib
import hmac
import json
import time

from . import config, java_client

# 单次批量处置上限，防止一次误操作处理过多数据
MAX_BATCH = 50

# 已执行的令牌（防重放）。单实例内存足够；多实例部署可换成 Redis
_used: set[str] = set()


def _b64(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def _unb64(text: str) -> bytes:
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


def _sign(body: str) -> str:
    return hmac.new(config.ACTION_SECRET.encode(), body.encode(), hashlib.sha256).hexdigest()


def sign_action(payload: dict) -> str:
    """把动作计划签成不可篡改的令牌"""
    data = {**payload, "exp": int(time.time()) + config.ACTION_TTL}
    body = _b64(json.dumps(data, ensure_ascii=False, separators=(",", ":")).encode())
    return f"{body}.{_sign(body)}"


def verify_action(token: str) -> dict:
    """验签 + 校验过期、动作类型、参数范围，任一项不过直接拒绝"""
    try:
        body, sig = token.split(".", 1)
    except ValueError:
        raise ValueError("动作令牌格式错误")
    if not hmac.compare_digest(sig, _sign(body)):
        raise ValueError("动作令牌签名无效")
    if body in _used:
        raise ValueError("动作令牌已执行，请重新生成计划")

    data = json.loads(_unb64(body))
    if int(data.get("exp", 0)) < time.time():
        raise ValueError("动作令牌已过期，请重新生成计划")
    if data.get("type") != "HANDLE_ALARMS":
        raise ValueError("不支持的动作类型")

    ids = data.get("alarmIds") or []
    if not ids or len(ids) > MAX_BATCH:
        raise ValueError(f"告警数量需在 1~{MAX_BATCH} 之间")
    if not all(isinstance(i, int) for i in ids):
        raise ValueError("告警 id 非法")
    return data


def build_handle_plan(alarms: list[dict], scope: str) -> dict:
    """根据查询结果生成待确认的批量处置计划（只签名，不执行）"""
    ids = [int(a["id"]) for a in alarms if a.get("id") is not None]
    payload = {"type": "HANDLE_ALARMS", "alarmIds": ids, "summary": scope}
    return {
        "type": "HANDLE_ALARMS",
        "title": "批量处置告警",
        "summary": scope,
        "count": len(ids),
        "token": sign_action(payload),
    }


def execute(token: str, authorization: str) -> dict:
    """确认后执行：逐条调用 Java 接口，收集成功/失败轨迹"""
    data = verify_action(token)
    _used.add(token.split(".", 1)[0])

    results = []
    for alarm_id in data["alarmIds"]:
        r = java_client.handle_alarm(authorization, alarm_id)
        results.append({
            "alarmId": alarm_id,
            "ok": bool(r.get("ok")),
            "error": r.get("error"),
        })

    ok = sum(1 for r in results if r["ok"])
    return {
        "summary": data.get("summary", ""),
        "total": len(results),
        "ok": ok,
        "failed": len(results) - ok,
        "results": results,
    }
