package com.smartknowledge.backend.common;

import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;

public record Api<T>(String code, String message, T data, String requestId) {
    public static <T> Api<T> ok(T data) { return new Api<>("OK", "成功", data, requestIdValue()); }
    public static Api<Object> error(String code, String message, Object data) { return new Api<>(code, message, data, requestIdValue()); }
    private static String requestIdValue() { String id = MDC.get("requestId"); return id == null ? UUID.randomUUID().toString() : id; }
    public record Page<T>(List<T> items, long total, int page, int size) {}
}
