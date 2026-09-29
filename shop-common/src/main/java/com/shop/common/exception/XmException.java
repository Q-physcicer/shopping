package com.shop.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 业务异常。除枚举外支持直接传文案（如校验类失败），message 由全局处理器透传给前端。
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class XmException extends RuntimeException{

    private ExceptionEnum exceptionEnum;

    private String message;

    public XmException(ExceptionEnum exceptionEnum) {
        this.exceptionEnum = exceptionEnum;
    }

    public XmException(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        // 兼容两种构造：显式文案优先，否则回落枚举文案
        return message != null ? message
                : (exceptionEnum != null ? exceptionEnum.getMsg() : "系统异常");
    }

    public int getCode() {
        return exceptionEnum != null ? exceptionEnum.getCode() : 0;
    }
}
