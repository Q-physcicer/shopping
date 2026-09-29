package com.shop.common.exception;

import com.shop.common.util.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;


/**
 * 全局异常兜底（common，自动装配到所有 servlet 服务）：
 * - XmException：透传业务文案（兼容枚举构造与直接文案构造）
 * - 参数类型错误/超长写入等：统一收敛为"参数错误"，避免裸 500
 *   （原缺陷：前端 axios 对 500 直接跳 /error 错误页，用户填了一半的表单全丢）
 */
@ControllerAdvice
@ResponseBody
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class XmExceptionHandler {

    @ExceptionHandler(XmException.class)
    public Result handleException(XmException e){
        return Result.fail(e.getMessage(), null);
    }

    @ExceptionHandler({NumberFormatException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class})
    public Result handleParamError(Exception e){
        return Result.fail("参数格式错误，请检查输入", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result handleDataTooLong(DataIntegrityViolationException e){
        return Result.fail("数据超出长度限制，请检查输入", null);
    }
}
