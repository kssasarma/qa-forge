package com.qaforge.bootstrap.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Springdoc OpenAPI metadata for the REST API documented in PRD §12. */
@Configuration
public class OpenApiConfig {

    private static final String BASIC_AUTH_SCHEME = "basicAuth";

    @Bean
    public OpenAPI qaForgeOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("QA Forge API")
                .description("AI-powered automated test generation and regression management")
                .version("v1"))
            .addSecurityItem(new SecurityRequirement().addList(BASIC_AUTH_SCHEME))
            .components(new io.swagger.v3.oas.models.Components()
                .addSecuritySchemes(BASIC_AUTH_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("basic")));
    }
}
