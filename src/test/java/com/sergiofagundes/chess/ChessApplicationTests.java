package com.sergiofagundes.chess;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ChessApplicationTests {

    @Test
    void contextLoads() {
        // Garante que o contexto sobe e que as migrations do Flyway aplicam
        // contra um Postgres limpo -- inclusive o ddl-auto=validate das entidades.
    }
}
