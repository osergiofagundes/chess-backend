# chess

Backend de um **xadrez online player-vs-player em tempo real**. Dois jogadores, um código de
convite, relógio com incremento e todas as regras validadas no servidor.

O cliente nunca decide se um lance é legal: ele propõe, o backend valida com a
[chesslib](https://github.com/bhlangonijr/chesslib), persiste e transmite o resultado para os
dois jogadores por WebSocket.

```
┌──────────┐   REST  /api/v1/**   ┌──────────────┐   JDBC   ┌────────────┐
│ cliente  │─────────────────────▶│   backend    │─────────▶│  Postgres  │
│ (web)    │◀──── STOMP /ws ─────▶│ Spring Boot 4│          │     16     │
└──────────┘                      └──────────────┘          └────────────┘
```

## Stack

Java 21 · Spring Boot 4.1.1 · Spring Security (JWT) · Spring Data JPA · Flyway · Postgres 16 ·
STOMP sobre WebSocket · chesslib 1.3.7 · Testcontainers 2.x

## Subindo o projeto

### Tudo no Docker

```bash
cp .env.example .env      # ajuste JWT_SECRET e CORS_ALLOWED_ORIGINS
docker compose up -d
curl http://localhost:7777/actuator/health
```

A API sobe em `http://localhost:7777` e o Postgres fica exposto em `localhost:5555`.

### Banco no Docker, app na IDE

```bash
docker compose up -d postgres
DB_URL=jdbc:postgresql://localhost:5555/chess ./mvnw spring-boot:run
```

> O default de `application.yml` aponta para a porta **5435**, e o compose publica o Postgres
> em **5555** — por isso o `DB_URL` explícito. Detalhes em
> [docs/desenvolvimento.md](docs/desenvolvimento.md).

## Variáveis de ambiente

| Variável | Default | Para que serve |
|---|---|---|
| `SERVER_PORT` | `7777` | porta HTTP da aplicação |
| `DB_URL` | `jdbc:postgresql://localhost:5435/chess` | JDBC do Postgres |
| `DB_USERNAME` | `chess` | usuário do banco |
| `DB_PASSWORD` | `chess` | senha do banco |
| `JWT_SECRET` | segredo de desenvolvimento | assinatura HS256 do access token |
| `COOKIE_SECURE` | `false` | marca o cookie de refresh como `Secure` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:9999` | origens liberadas para REST e WebSocket |

Em produção, `JWT_SECRET` precisa ter no mínimo 256 bits reais e `COOKIE_SECURE` precisa ser
`true`. Checklist completo em [docs/configuracao.md](docs/configuracao.md).

## API em uma tabela

| Método | Rota | O que faz |
|---|---|---|
| `POST` | `/api/v1/auth/register` | cria a conta e já devolve os tokens |
| `POST` | `/api/v1/auth/login` | entra por username **ou** e-mail |
| `POST` | `/api/v1/auth/refresh` | rotaciona o refresh token e emite novo access token |
| `POST` | `/api/v1/auth/logout` | revoga o refresh token e limpa o cookie |
| `POST` | `/api/v1/games` | cria a partida e devolve o código de convite |
| `POST` | `/api/v1/games/join` | entra numa partida pelo código |
| `GET` | `/api/v1/games/{id}` | estado completo da partida |
| `DELETE` | `/api/v1/games/{id}` | cancela uma partida que ainda não começou |

Durante a partida, tudo passa pelo WebSocket em `/ws`: lance, desistência, proposta e resposta
de empate. Contratos completos em [docs/api-rest.md](docs/api-rest.md) e
[docs/websocket.md](docs/websocket.md).

## Testes

```bash
./mvnw test
```

Sobe Postgres real via Testcontainers — **o Docker precisa estar rodando**. A suíte cobre as
regras de xadrez, o relógio, os fluxos REST e uma partida de ponta a ponta por WebSocket.
Veja [docs/testes.md](docs/testes.md).

## Documentação

A documentação completa está em **[docs/](docs/README.md)**: arquitetura, banco de dados,
módulos, contratos de API, decisões técnicas e glossário. Agentes de IA devem começar pelo
[CLAUDE.md](CLAUDE.md).
