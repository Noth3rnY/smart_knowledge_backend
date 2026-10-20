package com.smartknowledge.backend.account;

import com.smartknowledge.backend.common.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http,AccountService accounts,ObjectMapper json,@Value("${app.cors-origins}") String origins) throws Exception {
        CorsConfiguration cors=new CorsConfiguration(); cors.setAllowedOrigins(Arrays.asList(origins.split(","))); cors.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS")); cors.setAllowedHeaders(List.of("Authorization","Content-Type")); cors.setExposedHeaders(List.of("X-Request-Id"));
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",cors);
        http.csrf(c->c.disable()).cors(c->c.configurationSource(source)).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(c->c.requestMatchers("/api/v1/auth/register","/api/v1/auth/login","/api/v1/auth/refresh","/api/v1/plans","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html","/actuator/health").permitAll().anyRequest().authenticated())
            .exceptionHandling(c->c.authenticationEntryPoint((req,res,e)->write(res,json,401,"UNAUTHORIZED","请先登录或刷新令牌"))
                .accessDeniedHandler((req,res,e)->write(res,json,403,"FORBIDDEN","无权执行此操作")))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
                    String requestId=UUID.randomUUID().toString(); MDC.put("requestId",requestId); res.setHeader("X-Request-Id",requestId);
                    try {
                        String header=req.getHeader("Authorization");
                        if(header!=null && header.startsWith("Bearer ")) {
                            SessionUser user=accounts.authenticate(header.substring(7));
                            if(user!=null) SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,List.of()));
                        }
                        chain.doFilter(req,res);
                    } finally { MDC.remove("requestId"); SecurityContextHolder.clearContext(); }
                }
            },UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    private static void write(HttpServletResponse res,ObjectMapper json,int status,String code,String message) throws IOException {
        res.setStatus(status); res.setContentType("application/json;charset=UTF-8"); json.writeValue(res.getOutputStream(),Api.error(code,message,null));
    }
}
