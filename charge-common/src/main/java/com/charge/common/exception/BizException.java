package com.charge.common.exception;

/**
 * 业务异常：抛出后由全局异常处理器转为统一返回体
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
