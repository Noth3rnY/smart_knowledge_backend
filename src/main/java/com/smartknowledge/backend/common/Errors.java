package com.smartknowledge.backend.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<?> business(BusinessException e) { return ResponseEntity.status(e.status).body(Api.error(e.code, e.getMessage(), e.data)); }
    @ExceptionHandler({BindException.class, HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class, jakarta.validation.ConstraintViolationException.class})
    ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(Api.error("INVALID_ARGUMENT", "请求参数格式或长度不正确", null)); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<?> method(Exception e) { return ResponseEntity.status(405).body(Api.error("METHOD_NOT_ALLOWED", "此接口不支持该请求方法", null)); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<?> media(Exception e) { return ResponseEntity.status(415).body(Api.error("UNSUPPORTED_MEDIA_TYPE", "请使用 application/json 请求体", null)); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> duplicate(Exception e) { return ResponseEntity.status(409).body(Api.error("DATA_CONFLICT", "数据或关联关系发生冲突", null)); }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<?> missing(Exception e) { return ResponseEntity.status(404).body(Api.error("NOT_FOUND", "接口不存在", null)); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unknown(Exception e) {
        // Do not log request payloads, SQL bind values, or provider errors.
        org.slf4j.LoggerFactory.getLogger(Errors.class).error("Request failed: {}", e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(Api.error("INTERNAL_ERROR", "服务内部错误，请凭 requestId 排查", null));
    }
}
