package com.example.kistest.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("KIS 모의투자 API 명세서")
                        .description("한국투자증권 OpenAPI를 활용한 실시간 차트 및 주식 거래 API")
                        .version("v1.0.0"));
    }
}