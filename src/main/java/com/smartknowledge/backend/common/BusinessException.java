package com.smartknowledge.backend.common;

public class BusinessException extends RuntimeException {
    public final int status;
    public final String code;
    public final Object data;
    public BusinessException(int status, String code, String message) { this(status, code, message, null); }
    public BusinessException(int status, String code, String message, Object data) {
        super(message); this.status = status; this.code = code; this.data = data;
    }
    public static BusinessException missing() { return new BusinessException(404, "NOT_FOUND", "数据不存在"); }
    public static BusinessException invalid(String message) { return new BusinessException(400, "INVALID_ARGUMENT", message); }
}
