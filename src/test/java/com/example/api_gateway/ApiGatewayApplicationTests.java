package com.example.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiGatewayApplicationTests {

    @Autowired
    private RouteDefinitionLocator routeDefinitionLocator;

    @Test
    void contextLoads() {
    }

    @Test
    void catalogRoutesUseConfiguredDatabaseBackedService() {
        Map<String, RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions()
                .collectList()
                .blockOptional()
                .orElseThrow()
                .stream()
                .collect(toMap(RouteDefinition::getId, Function.identity()));

        assertThat(routes.get("catalog-service-v1").getUri()).isEqualTo(URI.create("http://localhost:8082"));
        assertThat(routes.get("catalog-service-v1").getFilters()).extracting("name").containsExactly("StripPrefix");
        assertThat(routes.get("catalog-service-legacy").getUri()).isEqualTo(URI.create("http://localhost:8082"));
    }
}
