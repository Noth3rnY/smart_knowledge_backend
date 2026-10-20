package com.smartknowledge.backend.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class Json {
    private final ObjectMapper mapper;
    public Json(ObjectMapper mapper) { this.mapper = mapper; }
    public String write(Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("JSON serialization failed", e); }
    }
    public Object read(String value) {
        try { return mapper.readValue(value, Object.class); } catch (Exception e) { throw new IllegalStateException("Stored JSON invalid", e); }
    }
    public Map<String,Object> map(String value) {
        try { return mapper.readValue(value, new TypeReference<>() {}); } catch (Exception e) { throw new IllegalStateException("Stored snapshot invalid", e); }
    }
    public static long bytes(String text) { return text.getBytes(StandardCharsets.UTF_8).length; }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
