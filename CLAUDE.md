# CLAUDE.md

Guia operacional para agentes de IA trabalhando neste repositório. Leia antes de editar
qualquer coisa. A documentação completa está em [`docs/`](docs/README.md) — este arquivo
traz só o que muda o resultado de uma edição.

## O que é este projeto

Backend de um xadrez online player-vs-player em tempo real. O servidor é a **única fonte de
verdade das regras**: o cliente propõe lances, quem valida e aplica é o backend.

- Ciclo de vida da partida (criar, entrar, consultar, cancelar): **REST**
- Jogo em andamento (lance, desistência, empate, relógio): **STOMP sobre WebSocket**

## Stack e versões

| Item | Versão | Onde |
|---|---|---|
| Java | 21 | `pom.xml` |
| Spring Boot | 4.1.1 | `pom.xml` |
| Postgres | 16 | `compose.yaml` |
| Flyway | via starter | `src/main/resources/db/migration/` |
| chesslib (bhlangonijr) | 1.3.7 | motor de regras |
| jjwt | 0.13.0 | access token |
| Testcontainers | 2.x | testes de integração |
| Lombok | via starter parent | só em entidade JPA |

## Comandos

```bash
docker compose up -d                   # Postgres + backend, tudo junto
docker compose up -d postgres          # só o banco, para rodar a app na IDE
./mvnw spring-boot:run                 # sobe a app (precisa do Postgres no ar)
./mvnw test                            # suíte completa (sobe containers)
./mvnw -Dtest=ClockServiceTest test    # uma classe só
./mvnw -DskipTests package             # empacota
```

Os testes de integração sobem containers Postgres de verdade: **o Docker precisa estar
rodando**, ou eles falham no startup, não na asserção.

## Mapa dos pacotes

`com.sergiofagundes.chess`

| Pacote | Responsabilidade | Doc |
|---|---|---|
| `auth` | registro, login, JWT, refresh token, autenticação STOMP | [modulos/auth.md](docs/modulos/auth.md) |
| `game` | ciclo de vida da partida, lances, eventos | [modulos/game.md](docs/modulos/game.md) |
| `game.engine` | adaptador da chesslib, validação de regras | [modulos/engine.md](docs/modulos/engine.md) |
| `game.service` | `GameService` (REST), `GamePlayService` (STOMP) e o relógio | [modulos/relogio.md](docs/modulos/relogio.md) |
| `user` | entidade `User` e repositório | [banco-de-dados.md](docs/banco-de-dados.md) |
| `common` | `ApiError`, exceções, handler global | [modulos/common.md](docs/modulos/common.md) |
| `config` | segurança, CORS, WebSocket, scheduler | [configuracao.md](docs/configuracao.md) |

## Convenções do código (siga-as)

- **Injeção por construtor package-private**, sem `@Autowired`. Veja `GameService.java:33`.
- **DTO é `record`**, sempre. Entidade JPA usa Lombok (`@Getter @Setter @NoArgsConstructor`);
  o resto do código não usa Lombok.
- **Erro de negócio é `BusinessException(HttpStatus, code, mensagem)`** — nunca devolva um
  `ResponseEntity` de erro montado no controller. O `GlobalExceptionHandler` converte para
  `ApiError`. Para 404, use `ResourceNotFoundException(code, mensagem)`.
- **Validação de entrada com Bean Validation** (`@Valid` + anotações no record), não com `if`
  dentro do service.
- **Comentários de código em português sem acentos** — é o padrão já estabelecido no repo.
  A documentação em `docs/` é em português **com** acentos.
- Comente o **porquê**, nunca o **o quê**. Os comentários existentes seguem isso
  (ex.: `SecurityConfig.java:41`, `V2__create_refresh_tokens.sql:4`).

## Regras que quebram o build se ignoradas

1. **`ddl-auto=validate`**: mudou entidade JPA? Crie a migration Flyway correspondente na
   mesma edição. Sem isso o contexto Spring não sobe — e todos os testes falham juntos.
2. **Enum Java × CHECK do banco**: `GameStatus`, `GameResult` e `Termination` são gravados
   como texto, e existem `CHECK` no banco espelhando os valores. Adicionar um valor no enum
   exige `ALTER TABLE ... DROP CONSTRAINT / ADD CONSTRAINT` em migration nova — foi
   exatamente o que `V5__add_clock.sql` fez para incluir `TIMEOUT`.
3. **Migration aplicada é imutável**: nunca edite um `V*.sql` que já rodou; crie o próximo.

## Estado em memória: a aplicação roda em instância única

Três coisas guardam estado fora do banco:

- `DrawOfferRegistry` — propostas de empate em um `ConcurrentHashMap`
- `TimeoutScheduler` — um `ScheduledFuture` por partida
- broker STOMP — `enableSimpleBroker` (`WebSocketConfig.java:36`), broker em memória

Consequência: **não existe suporte a mais de uma réplica**. Qualquer mudança que assuma
escala horizontal está errada sem antes trocar o broker (RabbitMQ/ActiveMQ) e mover os dois
registries para armazenamento compartilhado. Reiniciar o servidor perde as propostas de
empate e os agendamentos de timeout.

## Armadilhas que já existem (não "conserte" sem perguntar)

- O `Dockerfile` declara `EXPOSE 8088` porque essa era a porta default no último commit;
  o working copy mudou para **7777** e o `Dockerfile` ficou para trás. `EXPOSE` é apenas
  metadado e o `compose.yaml` publica 7777 corretamente, então nada quebra.
- **Três portas de Postgres** convivem: `5435` (default de `application.yml`), `5555` (host,
  no `compose.yaml`) e `5432` (dentro da rede do compose). Rodando a app na IDE contra o
  compose, passe `DB_URL` apontando para `5555`.
- **`CORS_ALLOWED_ORIGINS` tem defaults divergentes**: `http://localhost:9999` em
  `application.yml` e `.env.example`, `http://localhost:5175` no `compose.yaml`.
- **Jackson 2 e 3 convivem**: `RestAuthenticationEntryPoint.java:10` importa
  `tools.jackson.databind.ObjectMapper` (Jackson 3), enquanto as anotações seguem em
  `com.fasterxml.jackson.annotation` (`ApiError.java`, `GameEvent.java`). É o esperado no
  Spring Boot 4.
- `RefreshTokenService.revokeAllForUser` (`:71`) existe, mas **não tem chamador**.
- Não há limpeza de refresh tokens expirados; a tabela cresce indefinidamente.

## Onde encontrar o resto

| Preciso de... | Leia |
|---|---|
| visão geral, fluxos, concorrência | [docs/arquitetura.md](docs/arquitetura.md) |
| tabelas, constraints, migrations | [docs/banco-de-dados.md](docs/banco-de-dados.md) |
| rotas, payloads, códigos de erro | [docs/api-rest.md](docs/api-rest.md) |
| destinos STOMP e eventos | [docs/websocket.md](docs/websocket.md) |
| variáveis de ambiente e perfis | [docs/configuracao.md](docs/configuracao.md) |
| subir o ambiente, troubleshooting | [docs/desenvolvimento.md](docs/desenvolvimento.md) |
| como os testes são organizados | [docs/testes.md](docs/testes.md) |
| por que as coisas são assim | [docs/decisoes.md](docs/decisoes.md) |
| FEN, SAN, UCI, ply, flag | [docs/glossario.md](docs/glossario.md) |
