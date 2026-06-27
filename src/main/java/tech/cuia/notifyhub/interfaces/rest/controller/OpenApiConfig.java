package tech.cuia.notifyhub.interfaces.rest.controller;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI notifyHubOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("notify-hub API")
                        .version("0.1.0")
                        .description("""
                                Plataforma de notificações assíncronas multi-canal.
                                Obtenha um token em `POST /api/v1/auth/token` e use-o como Bearer.
                                """))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Token JWT obtido em /api/v1/auth/token")));
    }
}
