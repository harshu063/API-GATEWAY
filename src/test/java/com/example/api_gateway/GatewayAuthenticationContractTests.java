package com.example.api_gateway;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.security.oauth2.jwt.JwtEncoderParameters.from;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class GatewayAuthenticationContractTests {

    private static final String EXPECTED_AUDIENCE = "platform-api";
    private static final RSAKey SIGNING_KEY = generateSigningKey();
    private static final HttpServer JWK_SERVER = startServer("/oauth2/jwks", exchange -> respond(
            exchange, 200, new JWKSet(SIGNING_KEY.toPublicJWK()).toString()));
    private static final HttpServer UPSTREAM = startServer("/", exchange -> {
        boolean created = exchange.getRequestURI().getPath().endsWith("/registrations")
                || ("POST".equals(exchange.getRequestMethod())
                && exchange.getRequestURI().getPath().endsWith("/products"));
        respond(exchange, created ? 201 : 200, "{\"ok\":true}");
    });
    private static final String ISSUER = "http://127.0.0.1:" + JWK_SERVER.getAddress().getPort();

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry properties) {
        properties.add("security.jwt.issuer", () -> ISSUER);
        properties.add("security.jwt.jwk-set-uri", () -> ISSUER + "/oauth2/jwks");
        properties.add("security.jwt.audience", () -> EXPECTED_AUDIENCE);
        properties.add("CATALOG_SERVICE_URI", () -> upstreamUri());
        properties.add("AUTH_SERVICE_URI", () -> upstreamUri());
        properties.add("PAYMENT_SERVICE_URI", () -> upstreamUri());
    }

    @AfterAll
    static void stopTestServers() {
        JWK_SERVER.stop(0);
        UPSTREAM.stop(0);
    }

    @Test
    void registrationIsPublicAndReachesTheAuthRoute() {
        webTestClient.post()
                .uri("/api/v1/auth/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(CREATED);
    }

    @Test
    void catalogReadIsPublicAsDefinedByTheVersionedApiContract() {
        webTestClient.get()
                .uri("/api/v1/products")
                .exchange()
                .expectStatus().isEqualTo(OK);
    }

    @Test
    void catalogWriteRequiresTheWriteScope() {
        webTestClient.post()
                .uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .header(HttpHeaders.AUTHORIZATION, bearerToken("https://wrong-issuer.invalid", EXPECTED_AUDIENCE,
                        "catalog.write"))
                .exchange()
                .expectStatus().isEqualTo(UNAUTHORIZED);

        webTestClient.post()
                .uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(ISSUER, EXPECTED_AUDIENCE, "catalog.read"))
                .exchange()
                .expectStatus().isEqualTo(FORBIDDEN);

        webTestClient.post()
                .uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(ISSUER, EXPECTED_AUDIENCE, "catalog.write"))
                .exchange()
                .expectStatus().isEqualTo(CREATED);
    }

    @Test
    void rejectsTokensWithTheWrongAudience() {
        webTestClient.post()
                .uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(ISSUER, "another-api", "catalog.write"))
                .exchange()
                .expectStatus().isEqualTo(UNAUTHORIZED);
    }

    private static String bearerToken(String issuer, String audience, String scope) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("contract-test-user")
                .issuedAt(now.minusSeconds(5))
                .expiresAt(now.plusSeconds(120))
                .audience(List.of(audience))
                .claim("scope", scope)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(SIGNING_KEY.getKeyID()).build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new JWKSet(SIGNING_KEY)));
        return "Bearer " + encoder.encode(from(header, claims)).getTokenValue();
    }

    private static RSAKey generateSigningKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("gateway-contract-test-key").keyUse(KeyUse.SIGNATURE).generate();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create test signing key", exception);
        }
    }

    private static HttpServer startServer(String path, com.sun.net.httpserver.HttpHandler handler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(path, handler);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start gateway contract test server", exception);
        }
    }

    private static String upstreamUri() {
        return "http://127.0.0.1:" + UPSTREAM.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }
}
