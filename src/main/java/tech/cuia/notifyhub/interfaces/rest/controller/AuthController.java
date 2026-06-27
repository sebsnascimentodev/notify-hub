package tech.cuia.notifyhub.interfaces.rest.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.cuia.notifyhub.infrastructure.security.JwtTokenProvider;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication",
     description = "Emissão de tokens JWT. Endpoint de demonstração — em produção use OAuth2/OIDC.")
public class AuthController {

    private final JwtTokenProvider tokenProvider;

    @Value("${notify-hub.security.demo-client.id:demo}")
    private String demoClientId;

    @Value("${notify-hub.security.demo-client.secret:demo-secret}")
    private String demoClientSecret;

    public AuthController(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/token")
    @Operation(
            summary = "Emitir token JWT",
            description = "Autentica um cliente e retorna um token Bearer. " +
                          "Credenciais padrão para dev: clientId=demo / clientSecret=demo-secret."
    )
    public ResponseEntity<TokenResponse> issueToken(@Valid @RequestBody TokenRequest request) {
        if (!demoClientId.equals(request.clientId()) || !demoClientSecret.equals(request.clientSecret())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(tokenProvider.generateTokenResponse(request.clientId()));
    }

    // -------------------------------------------------------------------------
    // Inner records — escopados ao controller, não poluem o package de DTOs
    // -------------------------------------------------------------------------

    public record TokenRequest(
            @NotBlank(message = "clientId é obrigatório") String clientId,
            @NotBlank(message = "clientSecret é obrigatório") String clientSecret
    ) {}

    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type")   String tokenType,
            @JsonProperty("expires_in")   long expiresIn
    ) {}
}
