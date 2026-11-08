package com.charge.common.result;

/**
 * 统一返回体：所有接口响应都包一层 {code, msg, data}
 * code=200 成功，非 200 失败
 */
public class CommonResult<T> {

    private int code;
    private String msg;
    private T data;

    public CommonResult() {
    }

    public CommonResult(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> CommonResult<T> ok(T data) {
        return new CommonResult<>(200, "success", data);
    }

    public static <T> CommonResult<T> ok() {
        return new CommonResult<>(200, "success", null);
    }

    public static <T> CommonResult<T> fail(int code, String msg) {
        return new CommonResult<>(code, msg, null);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
