package com.sergiofagundes.chess;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Postgres real e descartavel para os testes de integracao. O container sobe em
 * porta aleatoria, entao nao concorre com o docker-compose local nem com os
 * outros Postgres desta maquina.
 *
 * <p>Testcontainers 2.x moveu a classe para {@code org.testcontainers.postgresql}
 * e removeu o parametro generico que existia na 1.x.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}
