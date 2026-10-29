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
import static org.springframework.security.oauth2.jwt.JwtEncoderParameters.from;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class GatewayPaymentScopeTests {

    private static final String AUDIENCE = "platform-api";
    private static final RSAKey SIGNING_KEY = signingKey();
    private static final HttpServer JWK_SERVER = server("/oauth2/jwks", exchange -> respond(
            exchange, 200, new JWKSet(SIGNING_KEY.toPublicJWK()).toString()));
    private static final HttpServer PAYMENT_SERVICE = server("/", exchange -> respond(exchange, 200, "{}"));
    private static final String ISSUER = "http://127.0.0.1:" + JWK_SERVER.getAddress().getPort();

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry properties) {
        properties.add("security.jwt.issuer", () -> ISSUER);
        properties.add("security.jwt.jwk-set-uri", () -> ISSUER + "/oauth2/jwks");
        properties.add("security.jwt.audience", () -> AUDIENCE);
        properties.add("PAYMENT_SERVICE_URI", () -> "http://127.0.0.1:" + PAYMENT_SERVICE.getAddress().getPort());
    }

    @AfterAll
    static void stopServers() {
        JWK_SERVER.stop(0);
        PAYMENT_SERVICE.stop(0);
    }

    @Test
    void forwardsPaymentWriteWhenTheAuthServiceIssuesScopeAsAJsonArray() {
        webTestClient.post()
                .uri("/payments/")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .header(HttpHeaders.AUTHORIZATION, bearerToken(List.of("catalog.read", "payments.write")))
                .exchange()
                .expectStatus().isOk();
    }

    private static String bearerToken(List<String> scopes) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject("payment-scope-test")
                .issuedAt(now.minusSeconds(5))
                .expiresAt(now.plusSeconds(120))
                .audience(List.of(AUDIENCE))
                .claim("scope", scopes)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(SIGNING_KEY.getKeyID()).build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new JWKSet(SIGNING_KEY)));
        return "Bearer " + encoder.encode(from(header, claims)).getTokenValue();
    }

    private static RSAKey signingKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("gateway-payment-scope-test").keyUse(KeyUse.SIGNATURE).generate();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create signing key", exception);
        }
    }

    private static HttpServer server(String path, com.sun.net.httpserver.HttpHandler handler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(path, handler);
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start test server", exception);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(response);
        }
    }
}
