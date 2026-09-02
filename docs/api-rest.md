# API REST

O que você encontra aqui: todas as rotas HTTP com autenticação exigida, payloads de entrada e
saída, validações, status possíveis e o catálogo completo de códigos de erro. O jogo em si não
está aqui — lances trafegam por WebSocket, documentado em [websocket.md](websocket.md).

Base: `http://localhost:7777` (configurável por `SERVER_PORT`).
Prefixo: `/api/v1`.

## Autenticação em duas peças

| Peça | Onde vive | Validade | Para que serve |
|---|---|---|---|
| **Access token** (JWT) | header `Authorization: Bearer …` | 15 minutos | autenticar cada requisição e o CONNECT do WebSocket |
| **Refresh token** (opaco) | cookie `refresh_token`, `HttpOnly` | 7 dias | obter um novo access token sem pedir a senha de novo |

O cookie é emitido pelo servidor com:

```
Set-Cookie: refresh_token=<valor>; Path=/api/v1/auth; Max-Age=604800;
            HttpOnly; SameSite=Lax[; Secure]
```

`Secure` só aparece quando `COOKIE_SECURE=true` (`auth/security/RefreshTokenCookies.java:43`).
O `Path` restrito a `/api/v1/auth` significa que o cookie **não é enviado** nas rotas de
partida — o que reduz a superfície de CSRF. Como o cookie é `HttpOnly`, o JavaScript do
cliente nunca o lê; basta usar `credentials: "include"` nas chamadas de auth.

Rotas públicas: tudo em `/api/v1/auth/**`, `/actuator/health/**` e o handshake `/ws/**`
(`config/SecurityConfig.java:39-43`). **Todo o resto exige o access token.**

---

## Autenticação

### `POST /api/v1/auth/register`

Cria a conta e já devolve a sessão — não é preciso fazer login em seguida.

**Público.** Corpo:

```json
{
  "username": "magnus",
  "email": "magnus@example.com",
  "password": "senha-forte-123"
}
```

| Campo | Regra | Fonte |
|---|---|---|
| `username` | obrigatório, `^[A-Za-z0-9_]{3,30}$` | `auth/dto/RegisterRequest.java` |
| `email` | obrigatório, formato de e-mail, até 255 caracteres | idem |
| `password` | obrigatório, de 8 a 72 caracteres | idem (72 é o limite do BCrypt) |

**`201 Created`** com o cookie de refresh e:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9…",
  "expiresInSeconds": 900,
  "user": {
    "id": "0f4c1e2a-…",
    "username": "magnus",
    "email": "magnus@example.com"
  }
}
```

Este é o único lugar onde os dados do usuário chegam ao cliente — **não existe rota `/me`**.
Guarde o objeto `user` no cliente.

Erros: `409 USERNAME_TAKEN`, `409 EMAIL_TAKEN`, `400 VALIDATION_ERROR`.

### `POST /api/v1/auth/login`

**Público.** Aceita username **ou** e-mail no mesmo campo, ambos sem diferenciar maiúsculas:

```json
{ "usernameOrEmail": "magnus", "password": "senha-forte-123" }
```

**`200 OK`** com o mesmo corpo do registro, mais o cookie.

Erros: `401 INVALID_CREDENTIALS`, `400 VALIDATION_ERROR`.

> Usuário inexistente e senha errada devolvem **exatamente a mesma resposta**, e o serviço
> ainda executa uma verificação de hash descartável quando o usuário não existe
> (`auth/service/AuthService.java:61`), para que o tempo de resposta não denuncie quais contas
> existem.

### `POST /api/v1/auth/refresh`

**Público, mas exige o cookie.** Sem corpo. O cliente precisa enviar cookies
(`credentials: "include"` no fetch).

**`200 OK`** com um access token novo, um `refresh_token` novo no cookie, e o token anterior
**revogado no mesmo instante** — cada refresh token só funciona uma vez
(`auth/service/RefreshTokenService.java:49`).

Erros: `401 MISSING_REFRESH_TOKEN` (cookie ausente), `401 INVALID_REFRESH_TOKEN` (não existe,
já foi usado, foi revogado ou expirou).

### `POST /api/v1/auth/logout`

**Público, mas exige o cookie.** Sem corpo. Revoga o refresh token e devolve um `Set-Cookie`
com `Max-Age=0`.

**`204 No Content`** — sempre, mesmo sem cookie ou com um token já revogado. Logout não vaza
informação nem falha (`auth/AuthController.java:48-55`).

O access token **continua válido até expirar**: não há blacklist. O cliente deve descartá-lo.

---

## Partidas

Todas exigem `Authorization: Bearer <accessToken>`.

### `POST /api/v1/games`

Cria uma partida em espera e devolve o código de convite.

```json
{
  "preferredColor": "RANDOM",
  "timeControl": { "initialSeconds": 300, "incrementSeconds": 2 }
}
```

| Campo | Regra |
|---|---|
| `preferredColor` | obrigatório: `WHITE`, `BLACK` ou `RANDOM` |
| `timeControl.initialSeconds` | obrigatório, de 10 a 10800 (3 horas) |
| `timeControl.incrementSeconds` | obrigatório, de 0 a 60 |

`RANDOM` é resolvido **na criação**, com `SecureRandom` (`game/dto/PreferredColor.java`) — a
resposta já diz de que cor você ficou.

**`201 Created`** com o [GameStateResponse](#gamestateresponse). O `status` vem `WAITING`, o
lado do adversário vem `null`, e `joinCode` traz o código para compartilhar.

Erros: `400 VALIDATION_ERROR`, `401 UNAUTHENTICATED`.

### `POST /api/v1/games/join`

Entra numa partida em espera. **Inicia a partida**: o status vira `IN_PROGRESS`, o relógio
começa a correr e o evento `GAME_STARTED` é publicado em `/topic/game/{id}`.

```json
{ "code": "K7M2QP" }
```

O código é normalizado no próprio DTO — `trim` e maiúsculas
(`game/dto/JoinGameRequest.java`) — então `" k7m2qp "` funciona.

**`200 OK`** com o `GameStateResponse` já iniciado.

| Erro | Quando |
|---|---|
| `404 INVALID_JOIN_CODE` | nenhuma partida com esse código |
| `409 GAME_NOT_WAITING` | a partida já começou, terminou ou foi cancelada |
| `409 CANNOT_JOIN_OWN_GAME` | você já está nessa partida |
| `400 VALIDATION_ERROR` | código em branco ou com mais de 8 caracteres |

> **Importante para o cliente:** inscreva-se em `/topic/game/{gameId}` **antes** de chamar esta
> rota (quem cria) ou imediatamente depois (quem entra). O `GAME_STARTED` sai daqui, do REST —
> não do WebSocket (`game/GameController.java:60-61`).

### `GET /api/v1/games/{gameId}`

Estado completo da partida, incluindo a lista de lances. É a rota de **ressincronização**
depois de uma queda de conexão.

**`200 OK`** com o [GameStateResponse](#gamestateresponse).

Erros: `404 GAME_NOT_FOUND`, `401 UNAUTHENTICATED`.

> Quem não joga a partida recebe **404, não 403** (`game/service/GameService.java:101`). É
> deliberado: um 403 confirmaria que aquele ID existe.

### `DELETE /api/v1/games/{gameId}`

Cancela uma partida que ainda não começou. O status vira `ABORTED`.

**`204 No Content`**.

| Erro | Quando |
|---|---|
| `409 GAME_ALREADY_STARTED` | a partida já saiu de `WAITING` |
| `404 GAME_NOT_FOUND` | não existe, ou você não joga nela |

> Qualquer um dos jogadores pode cancelar, não só quem criou. Como em `WAITING` só existe um
> jogador, na prática dá no mesmo.

---

## Saúde

### `GET /actuator/health`

**Público.** Devolve `{"status":"UP"}`. É o healthcheck usado pelo `compose.yaml`. Detalhes só
aparecem para requisições autenticadas (`management.endpoint.health.show-details:
when-authorized`).

---

## `GameStateResponse`

O corpo devolvido por todas as rotas de partida (`game/dto/GameStateResponse.java`):

```json
{
  "id": "6f3c9b4e-…",
  "joinCode": "K7M2QP",
  "status": "IN_PROGRESS",
  "whitePlayer": { "id": "0f4c…", "username": "magnus" },
  "blackPlayer": { "id": "9a21…", "username": "hikaru" },
  "yourColor": "WHITE",
  "currentFen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  "turn": "WHITE",
  "check": false,
  "result": null,
  "termination": null,
  "initialTimeSeconds": 300,
  "incrementSeconds": 2,
  "whiteTimeLeftMs": 298450,
  "blackTimeLeftMs": 300000,
  "moves": [
    { "ply": 1, "uci": "e2e4", "san": "e4", "fenAfter": "rnbqkbnr/…" }
  ]
}
```

| Campo | Notas |
|---|---|
| `status` | `WAITING`, `IN_PROGRESS`, `FINISHED`, `ABORTED` |
| `whitePlayer` / `blackPlayer` | `null` enquanto o lado está vago |
| `yourColor` | calculado para **quem pediu**; `null` não acontece nestas rotas, já que estranhos recebem 404 |
| `turn` | de quem é a vez, derivado do replay do histórico |
| `check` | o rei de quem joga agora está em xeque |
| `result` | `WHITE_WIN`, `BLACK_WIN`, `DRAW` ou `null` |
| `termination` | `CHECKMATE`, `STALEMATE`, `INSUFFICIENT_MATERIAL`, `THREEFOLD_REPETITION`, `FIFTY_MOVE`, `RESIGNATION`, `DRAW_AGREEMENT`, `TIMEOUT` ou `null` |
| `whiteTimeLeftMs` / `blackTimeLeftMs` | **já descontam** o tempo corrido desde o último lance, no instante da resposta |
| `moves` | ordenados por `ply` crescente |

Os relógios são um retrato do instante da resposta. Cabe ao cliente continuar a contagem
localmente e se corrigir a cada `MoveEvent`.

## `ApiError`

Todo erro sai neste formato (`common/dto/ApiError.java`). Campos vazios são omitidos.

```json
{
  "timestamp": "2026-08-27T18:32:11.482Z",
  "status": 409,
  "code": "CANNOT_JOIN_OWN_GAME",
  "message": "Voce ja esta nesta partida",
  "path": "/api/v1/games/join"
}
```

Erros de validação incluem `fieldErrors`:

```json
{
  "timestamp": "2026-08-27T18:32:11.482Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Dados inválidos",
  "path": "/api/v1/auth/register",
  "fieldErrors": [
    { "field": "password", "message": "deve ter de 8 a 72 caracteres" },
    { "field": "username", "message": "deve ter de 3 a 30 caracteres, apenas letras, números e _" }
  ]
}
```

**Trate sempre pelo `code`, nunca pela `message`** — as mensagens são texto para humanos e
podem mudar.

## Catálogo de códigos de erro

Levantado de todos os pontos que lançam erro em `src/main/java`.

### Autenticação

| Código | Status | Quando | Origem |
|---|---|---|---|
| `USERNAME_TAKEN` | 409 | username já em uso (ignorando maiúsculas) | `AuthService.java:42` |
| `EMAIL_TAKEN` | 409 | e-mail já em uso | `AuthService.java:45` |
| `INVALID_CREDENTIALS` | 401 | senha errada **ou** usuário inexistente | `AuthService.java:89` |
| `MISSING_REFRESH_TOKEN` | 401 | refresh sem o cookie | `AuthController.java:63` |
| `INVALID_REFRESH_TOKEN` | 401 | token desconhecido, usado, revogado ou expirado | `RefreshTokenService.java:76` |
| `UNAUTHENTICATED` | 401 | rota protegida sem token válido | `RestAuthenticationEntryPoint.java:29` |

### Partidas

| Código | Status | Quando | Origem |
|---|---|---|---|
| `GAME_NOT_FOUND` | 404 | partida inexistente, ou você não joga nela | `GameService.java:101`, `GamePlayService.java:202` |
| `INVALID_JOIN_CODE` | 404 | código de convite não existe | `GameService.java:63` |
| `GAME_NOT_WAITING` | 409 | tentou entrar numa partida que não está em espera | `GameService.java:67` |
| `CANNOT_JOIN_OWN_GAME` | 409 | tentou entrar na própria partida | `GameService.java:71` |
| `GAME_ALREADY_STARTED` | 409 | tentou cancelar partida já iniciada | `GameService.java:89` |
| `USER_NOT_FOUND` | 404 | token válido de usuário que não existe mais | `GameService.java:108` |

### Jogo em andamento (chegam pelo WebSocket)

| Código | Status | Quando | Origem |
|---|---|---|---|
| `GAME_NOT_IN_PROGRESS` | 409 | ação de jogo em partida que não está rolando | `GamePlayService.java:186` |
| `NOT_A_PLAYER` | 403 | você não é um dos dois jogadores | `GamePlayService.java:195` |
| `NOT_YOUR_TURN` | 409 | lance fora da vez | `GamePlayService.java:57` |
| `NO_DRAW_OFFER` | 409 | respondeu a um empate que ninguém propôs | `GamePlayService.java:158` |
| `CANNOT_ANSWER_OWN_OFFER` | 409 | tentou aceitar a própria proposta | `GamePlayService.java:162` |
| `ILLEGAL_MOVE` | 422 | lance ilegal na posição atual | `GlobalExceptionHandler.java:41` |

### Genéricos

| Código | Status | Quando | Origem |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | Bean Validation reprovou a entrada | `GlobalExceptionHandler.java:35` |
| `ACCESS_DENIED` | 403 | negado pelo Spring Security | `GlobalExceptionHandler.java:47` |
| `INTERNAL_ERROR` | 500 | qualquer exceção não prevista (é logada com stack trace) | `GlobalExceptionHandler.java:54` |

## Roteiro completo com curl

Do zero até uma partida começada, com dois usuários.

```bash
API=http://localhost:7777

# 1) jogador 1 se registra e guarda o access token
BRANCAS=$(curl -s -X POST "$API/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"magnus","email":"magnus@example.com","password":"senha-forte-123"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

# 2) cria a partida e captura o codigo de convite
PARTIDA=$(curl -s -X POST "$API/api/v1/games" \
  -H "Authorization: Bearer $BRANCAS" -H 'Content-Type: application/json' \
  -d '{"preferredColor":"WHITE","timeControl":{"initialSeconds":300,"incrementSeconds":2}}')
CODIGO=$(echo "$PARTIDA" | sed -n 's/.*"joinCode":"\([^"]*\)".*/\1/p')
GAME_ID=$(echo "$PARTIDA" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')
echo "codigo: $CODIGO  partida: $GAME_ID"

# 3) jogador 2 se registra
PRETAS=$(curl -s -X POST "$API/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"hikaru","email":"hikaru@example.com","password":"senha-forte-123"}' \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

# 4) jogador 2 entra: a partida comeca e o relogio dispara
curl -s -X POST "$API/api/v1/games/join" \
  -H "Authorization: Bearer $PRETAS" -H 'Content-Type: application/json' \
  -d "{\"code\":\"$CODIGO\"}"

# 5) qualquer um consulta o estado
curl -s "$API/api/v1/games/$GAME_ID" -H "Authorization: Bearer $BRANCAS"
```

Daqui em diante, os lances vão por WebSocket — continue em [websocket.md](websocket.md).

Para exercitar o refresh, use um cookie jar:

```bash
curl -s -c cookies.txt -X POST "$API/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"usernameOrEmail":"magnus","password":"senha-forte-123"}'

curl -s -b cookies.txt -c cookies.txt -X POST "$API/api/v1/auth/refresh"
curl -s -b cookies.txt -X POST "$API/api/v1/auth/logout" -i
```

## Veja também

- [websocket.md](websocket.md) — o que acontece depois que a partida começa
- [modulos/auth.md](modulos/auth.md) — como os tokens são gerados e validados
- [modulos/common.md](modulos/common.md) — como adicionar um código de erro novo
- [configuracao.md](configuracao.md) — CORS, portas e o cookie `Secure`
