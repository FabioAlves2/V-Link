package com.vlink.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

// Swagger UI: /swagger-ui/index.html — OpenAPI JSON: /v3/api-docs
// Sem um "security" global aqui: swagger-core não consegue distinguir @Operation(security = {})
// (explicitamente sem segurança) de "não especificado" — ambos usam {} como valor por defeito
// da anotação — por isso uma tentativa de exigir bearerAuth globalmente e anulá-lo por endpoint
// nos públicos nunca funcionaria de forma fiável. Em vez disso, @SecurityRequirement("bearerAuth")
// é adicionado explicitamente só aos controllers/métodos protegidos (opt-in, não opt-out).
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "V-Link API",
        version = "1.0",
        description = "API REST da V-Link — plataforma onde promotores publicam eventos de "
            + "voluntariado e voluntários se inscrevem. Autenticação por JWT: faz login em "
            + "/auth/login, copia o \"token\" da resposta e usa o botão Authorize abaixo.",
        contact = @Contact(name = "V-Link")
    )
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
public class OpenApiConfig {
}
