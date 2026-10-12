package cn.voidhaven.api.config;
import cn.voidhaven.api.web.AdminAuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
public class AuthorizationWebConfig implements WebMvcConfigurer {
    private final AdminAuthorizationInterceptor interceptor;
    public AuthorizationWebConfig(AdminAuthorizationInterceptor interceptor) { this.interceptor = interceptor; }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(interceptor); }
}
