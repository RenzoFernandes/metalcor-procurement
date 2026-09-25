package com.metalcor.procurement.config;

import com.metalcor.procurement.security.CurrentUserInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserInterceptor currentUserInterceptor;
    private final String corsAllowedOrigin;

    public WebMvcConfig(CurrentUserInterceptor currentUserInterceptor,
            @Value("${app.cors.allowed-origin:http://localhost:5173}") String corsAllowedOrigin) {
        this.currentUserInterceptor = currentUserInterceptor;
        this.corsAllowedOrigin = corsAllowedOrigin;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(currentUserInterceptor).addPathPatterns("/api/v1/**");
    }

    /** Browser front end (Vite dev server by default). No cookies are used, so credentials stay off. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**")
                .allowedOrigins(corsAllowedOrigin)
                .allowedMethods("GET", "POST")
                .allowedHeaders("*")
                .allowCredentials(false);
    }
}