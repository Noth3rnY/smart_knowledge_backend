package com.smartknowledge.backend.common;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Parameterized SQL for aggregate joins and transactions; simple entities use MyBatis-Plus mappers. */
@Repository
public class SqlMapper {
    private final JdbcTemplate jdbc;
    public SqlMapper(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private Object[] args(Object[] args) {
        return Arrays.stream(args).map(v -> v instanceof Instant i ? Timestamp.from(i) : v).toArray();
    }
    public List<Map<String,Object>> list(String sql, Object... parameters) {
        return jdbc.queryForList(sql, args(parameters)).stream().map(row -> {
            Map<String,Object> result = new LinkedHashMap<>();
            row.forEach((key,value) -> {
                StringBuilder camel = new StringBuilder(); boolean upper = false;
                for (char c : key.toCharArray()) { if(c == '_') upper = true; else { camel.append(upper ? Character.toUpperCase(c) : c); upper = false; } }
                if(value instanceof Timestamp t) value = t.toInstant();
                if(value instanceof LocalDateTime t) value = t.toInstant(ZoneOffset.UTC);
                result.put(camel.toString(), value);
            }); return result;
        }).toList();
    }
    public Map<String,Object> one(String sql, Object... args) { var rows = list(sql,args); return rows.isEmpty() ? null : rows.get(0); }
    public Map<String,Object> required(String sql, Object... args) { var row = one(sql,args); if(row == null) throw BusinessException.missing(); return row; }
    public long count(String sql, Object... args) { Long value = jdbc.queryForObject(sql, Long.class, args(args)); return value == null ? 0 : value; }
    public int update(String sql, Object... args) { return jdbc.update(sql,args(args)); }
    /** All writers lock the same user row, including quota and subscription changes. */
    public void lock(String user) { required("SELECT id FROM users WHERE id=? FOR UPDATE", user); }
    public static long number(Map<String,Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    public static boolean flag(Map<String,Object> row, String key) { Object v = row.get(key); return Boolean.TRUE.equals(v) || v instanceof Number n && n.intValue()!=0; }
    public static String uuid(String value) {
        try { String normalized = UUID.fromString(value).toString(); if(!normalized.equalsIgnoreCase(value)) throw new IllegalArgumentException(); return normalized; }
        catch (Exception e) { throw BusinessException.invalid("UUID 格式不正确"); }
    }
}
