package com.example.pubsub.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 設定。フロントエンド(Next.js / localhost:3000)からの
 * ブラウザ直アクセスを許可する。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }
}
