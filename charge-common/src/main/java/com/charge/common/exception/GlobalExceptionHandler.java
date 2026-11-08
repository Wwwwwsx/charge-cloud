package com.charge.common.exception;

import com.charge.common.result.CommonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：业务异常返回友好提示，未知异常返回 500 且不泄漏堆栈
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public CommonResult<Void> handleBiz(BizException e) {
        return CommonResult.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public CommonResult<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return CommonResult.fail(500, "系统繁忙，请稍后重试");
    }
}
