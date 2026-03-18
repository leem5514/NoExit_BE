package com.E1i3.NoExit.domain.common.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class WebConfigs implements WebMvcConfigurer {

	@Bean
	public PasswordEncoder makePassword(){
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
	    registry.addMapping("/**")
	            .allowedOrigins(
	                    "https://www.noexxit.store",
	                    "https://server.noexxit.store",
	                    "http://localhost:3000",
	                    "http://localhost:8082"
	            )
				.allowedOrigins("https://www.noexxit.store")
				.allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")
				.allowedHeaders("*")
				.allowCredentials(true);  // 자격 증명을 허용
	}
}

