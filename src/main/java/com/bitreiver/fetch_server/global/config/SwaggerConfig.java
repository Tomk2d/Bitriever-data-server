package com.bitreiver.fetch_server.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    
    @Bean
    public OpenAPI openAPI() {
        // JWT 인증 제거 (서버 간 통신만 사용)
        return new OpenAPI()
            .info(new Info()
                .title("Bitriever Fetch Server API")
                .description("Bitriever Fetch Server(Spring Boot) API - 거래소 데이터 수집 및 거래내역 관리 서버")
                .version("1.0.0"));
    }
}

