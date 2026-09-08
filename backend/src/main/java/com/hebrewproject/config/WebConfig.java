package com.hebrewproject.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Allows the local frontend dev server (different origin/port from the API)
// to call /api/** during development. Restricted to localhost dev ports, not
// a wildcard. 3000 is Next.js's default dev port (the frontend being built
// now); 5173 is Vite's (the scaffold being retired) - kept for now since
// nothing has removed that scaffold yet.
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:3000", "http://127.0.0.1:3000",
                        "http://localhost:5173", "http://127.0.0.1:5173")
                .allowedMethods("GET");
    }
}
