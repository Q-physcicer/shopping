package com.shopping.exception;

import com.shopping.util.Result;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;


@ControllerAdvice
@ResponseBody
public class XmExceptionHandler {

    @ExceptionHandler(XmException.class)
    public Result handleException(XmException e){
        ExceptionEnum em = e.getExceptionEnum();
        return Result.fail(em.getMsg(), null);
    }
}
