"""Java 业务服务客户端：AI 的所有工具最终都打到这些 HTTP 接口上"""
import httpx

from . import config


def _get(token: str, base: str, path: str, params: dict | None = None) -> dict:
    """带用户 JWT 的 GET 请求；token 由网关签发，租户权限由 Java 侧校验"""
    url = f"{base}{path}"
    try:
        resp = httpx.get(
            url,
            params={k: v for k, v in (params or {}).items() if v not in (None, "")},
            headers={"Authorization": token},
            timeout=config.HTTP_TIMEOUT,
        )
        data = resp.json()
        if data.get("code") != 200:
            return {"ok": False, "error": data.get("msg", f"HTTP {resp.status_code}")}
        return {"ok": True, "data": data.get("data")}
    except Exception as e:  # 网络/解析异常都转成可读错误，让 Agent 诚实告知用户
        return {"ok": False, "error": f"服务调用失败: {e}"}


def _put(token: str, base: str, path: str) -> dict:
    """带用户 JWT 的 PUT 请求（Agent 写操作调用）"""
    url = f"{base}{path}"
    try:
        resp = httpx.put(
            url,
            headers={"Authorization": token},
            timeout=config.HTTP_TIMEOUT,
        )
        data = resp.json()
        if data.get("code") != 200:
            return {"ok": False, "error": data.get("msg", f"HTTP {resp.status_code}")}
        return {"ok": True, "data": data.get("data")}
    except Exception as e:
        return {"ok": False, "error": f"服务调用失败: {e}"}


def list_devices(token: str, status: str = "", station: str = "", keyword: str = "") -> dict:
    return _get(token, config.DEVICE_BASE_URL, "/devices",
                {"page": 1, "size": 20, "status": status, "station": station, "keyword": keyword})


def get_device(token: str, code: str) -> dict:
    return _get(token, config.DEVICE_BASE_URL, f"/devices/code/{code}")


def list_alarms(token: str, level: str = "", status: str = "", size: int = 20) -> dict:
    return _get(token, config.ALARM_BASE_URL, "/alarms",
                {"page": 1, "size": size, "level": level, "status": status})


def handle_alarm(token: str, alarm_id: int) -> dict:
    """标记单条告警为已处理（租户校验由 Java 侧按 JWT 完成）"""
    return _put(token, config.ALARM_BASE_URL, f"/alarms/{alarm_id}/handle")


def alarm_stats(token: str) -> dict:
    return _get(token, config.ALARM_BASE_URL, "/alarms/stats")


def daily_report_data(token: str) -> dict:
    """日报原料 = 统计 + 全部未处理告警"""
    stats = alarm_stats(token)
    if not stats.get("ok"):
        return stats
    alarms = list_alarms(token, status="OPEN")
    if not alarms.get("ok"):
        return alarms
    return {"ok": True, "stats": stats["data"], "open_alarms": alarms["data"].get("records", [])}
