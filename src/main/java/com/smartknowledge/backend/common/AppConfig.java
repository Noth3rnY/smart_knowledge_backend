package com.smartknowledge.backend.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("SmartKnowledgeDB 课程项目 API").version("1.0.0").description("本地优先笔记的可选云端服务；令牌由 /auth/login 获取。"))
            .components(new Components().addSecuritySchemes("bearer", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")))
            .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
