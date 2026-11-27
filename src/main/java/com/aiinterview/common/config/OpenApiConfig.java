package com.aiinterview.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置：访问 /swagger-ui.html 即可在线调试全部接口。
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI aiInterviewOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI 面试平台 API")
                        .version("1.0.0")
                        .description("简历解析 / 智能出题 / 实时问答 / AI 评分 / 面试报告")
                        .contact(new Contact().name("AI Interview Platform")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 accessToken，填入时无需加 Bearer 前缀")));
    }
}
