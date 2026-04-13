package com.example.kistest.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**") // 💡 /api로 시작하는 모든 경로에 적용
                .allowedOrigins("http://localhost:3000") // 💡 Next.js 서버 주소
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 허용할 HTTP 메서드
                .allowedHeaders("*") // 모든 헤더 허용
                .allowCredentials(true) // 쿠키 인증이 필요한 경우 허용
                .maxAge(3600); // 프리플라이트(Preflight) 요청 캐싱 시간 (초 단위)
    }
}