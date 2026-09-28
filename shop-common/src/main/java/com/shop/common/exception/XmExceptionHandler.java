package com.shop.common.exception;

import com.shop.common.util.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;


@ControllerAdvice
@ResponseBody
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class XmExceptionHandler {

    @ExceptionHandler(XmException.class)
    public Result handleException(XmException e){
        ExceptionEnum em = e.getExceptionEnum();
        return Result.fail(em.getMsg(), null);
    }
}
