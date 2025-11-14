package com.iflytek.astron.workflow.domain;

import lombok.Data;

@Data
public class Result<T> {
    
    private Integer code;
    private String message;
    private T data;
    private String sid;
    
    public Result() {}
    
    public Result(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    
    public static <T> Result<T> success() {
        return new Result<>(0, "success");
    }
    
    public static <T> Result<T> success(T data) {
        return new Result<>(0, "success", data);
    }
    
    public static <T> Result<T> failure(String message) {
        return new Result<>(-1, message);
    }
    
    public static <T> Result<T> failure(Integer code, String message) {
        return new Result<>(code, message);
    }
}
