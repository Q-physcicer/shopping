package com.shopping.util;

import lombok.Data;
import org.springframework.stereotype.Component;


@Component
@Data
public class ResultMessage {

    private String code;
    private String msg;
    private Object data;

    public void success(String code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public void success(String code, String msg) {
        this.code = code;
        this.msg = msg;
        this.data = null;
    }

    public void success(String code, Object data) {
        this.code = code;
        this.msg = null;
        this.data = data;
    }

    public void fail(String code, String msg) {
        this.code = code;
        this.msg = msg;
        this.data = null;
    }

}
