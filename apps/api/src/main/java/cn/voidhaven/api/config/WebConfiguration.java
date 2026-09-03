package cn.voidhaven.api.config;

import cn.voidhaven.api.web.PublicWriteRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final PublicWriteRateLimitInterceptor publicWriteRateLimit;

    public WebConfiguration(PublicWriteRateLimitInterceptor publicWriteRateLimit) { this.publicWriteRateLimit = publicWriteRateLimit; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(publicWriteRateLimit);
    }
}
