# Desenvolvimento

O que você encontra aqui: como subir o ambiente, os comandos do dia a dia, como criar uma
migration e o que fazer quando algo não sobe.

## Pré-requisitos

| Ferramenta | Versão | Nota |
|---|---|---|
| JDK | 21 | `java -version` precisa mostrar 21 |
| Docker | recente, com Compose v2 | necessário também para os testes |
| Maven | — | use o wrapper `./mvnw`, não precisa instalar |

No Windows, use `mvnw.cmd` no PowerShell/cmd, ou `./mvnw` no Git Bash.

## Caminho 1: tudo no Docker

O mais rápido para ver o sistema de pé.

```bash
cp .env.example .env
docker compose up -d
docker compose logs -f backend
```

Sobe Postgres 16 e o backend; o backend só inicia depois que o healthcheck do banco passa. A
imagem é construída em dois estágios (build com Maven, runtime com JRE Alpine e usuário não-root).

```bash
curl http://localhost:7777/actuator/health   # {"status":"UP"}
```

| Serviço | Endereço |
|---|---|
| API | `http://localhost:7777` |
| WebSocket | `ws://localhost:7777/ws` |
| Postgres | `localhost:5555` (usuário `chess`, banco `chess`) |

Depois de mudar código: `docker compose up -d --build backend`.

## Caminho 2: banco no Docker, aplicação na IDE

O caminho do dia a dia — permite debug e hot reload.

```bash
docker compose up -d postgres
DB_URL=jdbc:postgresql://localhost:5555/chess ./mvnw spring-boot:run
```

> **Por que o `DB_URL` explícito:** o default do `application.yml` aponta para a porta **5435**,
> e o compose publica o Postgres em **5555**. Sem a variável, a aplicação tenta conectar num
> lugar onde não há nada.

Na IDE, configure na run configuration:

```
DB_URL=jdbc:postgresql://localhost:5555/chess
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

Ajuste o CORS para a porta real do seu frontend — sem isso, o browser bloqueia tanto as chamadas
REST quanto o handshake do WebSocket.

Para logs detalhados de SQL e STOMP, ative o perfil `dev`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Comandos

```bash
./mvnw spring-boot:run                        # sobe a aplicação
./mvnw test                                   # todos os testes
./mvnw -Dtest=ClockServiceTest test           # uma classe
./mvnw -Dtest=ClockServiceTest#consomeEIncrementa test   # um método
./mvnw -DskipTests package                    # gera o jar
./mvnw clean                                  # limpa target/
java -jar target/chess-0.0.1-SNAPSHOT.jar     # roda o jar
```

Docker:

```bash
docker compose up -d              # sobe tudo
docker compose up -d postgres     # só o banco
docker compose logs -f backend    # acompanha os logs
docker compose ps                 # estado e healthchecks
docker compose down               # para tudo, mantendo os dados
docker compose down -v            # para tudo e APAGA o volume do banco
docker compose up -d --build      # reconstrói a imagem
```

## Trabalhando com o banco

Conectando:

```bash
docker compose exec postgres psql -U chess -d chess
psql -h localhost -p 5555 -U chess -d chess     # se tiver psql local
```

Consultas úteis:

```sql
\dt                                    -- tabelas
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT id, join_code, status, result, termination FROM games ORDER BY created_at DESC LIMIT 10;
SELECT ply, uci, san, time_spent_ms FROM moves WHERE game_id = '…' ORDER BY ply;
```

**Zerar o banco** (desenvolvimento):

```bash
docker compose down -v && docker compose up -d postgres
```

O Flyway reaplica todas as migrations do zero na próxima subida.

### Criando uma migration

1. Crie `src/main/resources/db/migration/V6__descricao_curta.sql` — **dois** underscores depois
   da versão.
2. Escreva o DDL. Se a tabela já tem dados, siga o padrão da `V5`: coluna com `DEFAULT`,
   `UPDATE` de backfill, remover o `DEFAULT`, aplicar `NOT NULL`.
3. Se mexeu em enum Java, atualize o `CHECK` correspondente na mesma migration.
4. Ajuste a entidade JPA.
5. Rode `./mvnw test` — o `contextLoads` aplica tudo contra um Postgres limpo e valida as
   entidades.

**Nunca edite uma migration já aplicada:** o Flyway guarda o checksum e recusa subir se ele
mudar. Em desenvolvimento, o conserto é `docker compose down -v`.

## Exercitando a API

O roteiro completo com `curl` — registrar dois usuários, criar e entrar numa partida — está em
[api-rest.md](api-rest.md#roteiro-completo-com-curl). Para o WebSocket, veja
[websocket.md](websocket.md#testando-na-mão).

Um atalho útil: o teste `GameWebSocketIntegrationTest` é um cliente STOMP completo e funcional.
Quando estiver em dúvida sobre o protocolo, leia-o.

## Troubleshooting

### A aplicação não sobe

**`Schema-validation: missing column [...]` ou `wrong column type`**
A entidade JPA não bate com o esquema. Falta a migration correspondente, ou o tipo do campo Java
não corresponde ao da coluna. É o `ddl-auto: validate` fazendo o trabalho dele.

**`Connection refused` no Postgres**
Porta errada (5435 x 5555) ou banco no ar. Confira `docker compose ps` e o `DB_URL`.

**`Validate failed: Migration checksum mismatch`**
Uma migration já aplicada foi editada. Reverta a edição, ou `docker compose down -v` em
desenvolvimento.

**`The signing key's size is ... bits which is not secure enough`**
`JWT_SECRET` menor que 256 bits. Gere um com `openssl rand -base64 48`.

**`Port 7777 already in use`**
Outra instância está rodando (talvez no compose). `docker compose stop backend` ou mude
`SERVER_PORT`.

### Os testes falham

**Testes de integração falham no startup**
Docker não está rodando, ou a imagem `postgres:16-alpine` não pode ser baixada. Testcontainers
precisa de um daemon acessível.

**Só `GameWebSocketIntegrationTest` falha, de forma intermitente**
Ele espera eventos com timeout de 5 segundos. Em máquina lenta ou sob carga, isso aperta. Rode
a classe isolada para confirmar.

### O frontend não conecta

**CORS bloqueando**
A origem exata (protocolo, host e porta) precisa estar em `CORS_ALLOWED_ORIGINS`.
`http://localhost:3000` e `http://127.0.0.1:3000` são origens **diferentes**.

**WebSocket conecta mas nada acontece**
O `Authorization` não foi enviado no frame CONNECT, ou o token expirou. A primeira mensagem volta
como `UNAUTHENTICATED` em `/user/queue/errors` — assine essa fila enquanto desenvolve.

**401 em todas as rotas de partida**
Access token expirado (15 minutos). Chame `POST /api/v1/auth/refresh` com o cookie.

**O refresh não funciona no navegador**
Faltou `credentials: "include"` no fetch. O cookie é `HttpOnly` e só é enviado quando o cliente
pede explicitamente.

### O relógio parece errado

Os tempos do `GameStateResponse` são um retrato do instante da resposta, e os do `MoveEvent`, do
instante do lance. O cliente conta localmente entre eles. Se estiver divergindo muito, confira
se o cliente está usando `serverTimestamp` como marco. Ver [modulos/relogio.md](modulos/relogio.md).

## Estrutura do projeto

```
chess/
├── CLAUDE.md              instruções para agentes de IA
├── README.md              porta de entrada
├── compose.yaml           Postgres + backend
├── Dockerfile             build multi-estágio
├── pom.xml                dependências e build
├── docs/                  esta documentação
└── src/
    ├── main/
    │   ├── java/com/sergiofagundes/chess/
    │   │   ├── auth/      autenticação e tokens
    │   │   ├── common/    erros compartilhados
    │   │   ├── config/    segurança, CORS, WebSocket, scheduler
    │   │   ├── game/      partidas, lances, motor de regras
    │   │   └── user/      entidade User
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       └── db/migration/    V1 a V5
    └── test/java/com/sergiofagundes/chess/
```

## Veja também

- [configuracao.md](configuracao.md) — todas as variáveis em detalhe
- [testes.md](testes.md) — como a suíte é organizada
- [banco-de-dados.md](banco-de-dados.md) — esquema e migrations
- [../CLAUDE.md](../CLAUDE.md) — o resumo para agentes
