# WebSocket e STOMP

O que você encontra aqui: como conectar, como autenticar, todos os destinos de envio e
assinatura, o formato dos quatro eventos de partida e como os erros chegam ao cliente. É o
contrato que o frontend precisa implementar para o jogo funcionar.

## Conexão

| Item | Valor |
|---|---|
| Endpoint | `ws://localhost:7777/ws` (`wss://` em produção) |
| Protocolo | STOMP sobre WebSocket nativo — **sem SockJS** |
| Origens aceitas | `app.cors.allowed-origins` (`config/WebSocketConfig.java:30-31`) |
| Broker | `SimpleBroker` em memória, prefixos `/topic` e `/queue` |
| Prefixo de envio | `/app` |
| Prefixo de destino de usuário | `/user` |

O handshake HTTP é liberado no Spring Security (`config/SecurityConfig.java:43`). Isso **não**
significa que o WebSocket é público: a autenticação acontece um passo depois, no frame CONNECT.

## Autenticação: no CONNECT, não no handshake

O access token vai como header nativo do frame **CONNECT**:

```
CONNECT
accept-version:1.2
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9…
```

`StompAuthChannelInterceptor` (`auth/security/StompAuthChannelInterceptor.java:28-34`)
intercepta apenas o CONNECT, valida o JWT e associa o `AuthenticatedUser` à sessão STOMP. A
partir daí, todo frame daquela sessão já chega com o `Principal` — os handlers não revalidam
token.

Por que não no handshake? Porque o WebSocket nativo do browser **não permite definir headers
HTTP** no upgrade. As alternativas seriam token na query string (que vaza em logs de acesso) ou
cookie (que exigiria abrir o cookie para além de `/api/v1/auth`). O frame CONNECT resolve isso
de forma limpa. Ver [decisoes.md](decisoes.md).

Consequências práticas:

- **Um CONNECT sem token, ou com token inválido, é aceito** — só fica sem `Principal`. A
  primeira mensagem de jogo então falha com `UNAUTHENTICATED`
  (`game/GameWebSocketController.java:111`).
- **O token não é revalidado durante a sessão.** Uma conexão aberta continua funcionando mesmo
  depois de o access token expirar. Para renovar de fato, reconecte com um token novo.
- **Não há autorização por tópico.** Qualquer sessão conectada pode assinar
  `/topic/game/{qualquer-id}` e observar a partida. A autorização existe na **escrita**: todo
  handler chama `requirePlayer` (`game/service/GamePlayService.java:192`). Se sigilo de partida
  importar, isso precisa de um interceptor no SUBSCRIBE.

Exemplo com `@stomp/stompjs`:

```javascript
const client = new StompJs.Client({
  brokerURL: "ws://localhost:7777/ws",
  connectHeaders: { Authorization: `Bearer ${accessToken}` },
  onConnect: () => {
    client.subscribe(`/topic/game/${gameId}`, (frame) =>
      aoReceberEvento(JSON.parse(frame.body)),
    );
    client.subscribe("/user/queue/errors", (frame) =>
      aoReceberErro(JSON.parse(frame.body)),
    );
  },
});
client.activate();
```

## Destinos

### Envio (cliente → servidor)

| Destino | Corpo | O que faz |
|---|---|---|
| `/app/game/{gameId}/move` | `MoveMessage` | tenta um lance |
| `/app/game/{gameId}/resign` | vazio | desiste; o adversário vence |
| `/app/game/{gameId}/draw-offer` | vazio | propõe empate |
| `/app/game/{gameId}/draw-response` | `DrawResponseMessage` | aceita ou recusa a proposta |

### Assinatura (servidor → cliente)

| Destino | Conteúdo |
|---|---|
| `/topic/game/{gameId}` | eventos da partida, para os **dois** jogadores e qualquer observador |
| `/user/queue/errors` | erros, **só para quem enviou** a mensagem problemática |

Todos os quatro handlers são `void`: nada é devolvido como resposta direta ao remetente. O
resultado sempre chega pelo tópico — inclusive para quem jogou.

## Mensagens de entrada

### `MoveMessage`

```json
{ "from": "e2", "to": "e4", "promotion": null }
```

| Campo | Regra | Fonte |
|---|---|---|
| `from` | obrigatório, `^[a-h][1-8]$` | `game/dto/MoveMessage.java` |
| `to` | obrigatório, `^[a-h][1-8]$` | idem |
| `promotion` | opcional; se presente, `^[qrbn]$` (minúsculo) | idem |

Promoção é obrigatória quando o peão chega à última fileira — sem ela, o lance é rejeitado como
ilegal. Roque é enviado como o movimento do **rei**: `e1` → `g1` para o roque curto das brancas;
a torre é movida pelo motor.

```json
{ "from": "g7", "to": "h8", "promotion": "q" }
```

### `DrawResponseMessage`

```json
{ "accepted": true }
```

## Eventos da partida

Todos chegam em `/topic/game/{gameId}` com um campo **`type`** discriminando o tipo
(`game/dto/events/GameEvent.java:6-11`). Faça o switch por ele.

### `GAME_STARTED`

Publicado quando o segundo jogador entra. **Sai do REST**, no `POST /api/v1/games/join`
(`game/GameController.java:60-61`), não de uma mensagem STOMP.

```json
{
  "type": "GAME_STARTED",
  "whitePlayer": { "id": "0f4c…", "username": "magnus" },
  "blackPlayer": { "id": "9a21…", "username": "hikaru" }
}
```

Quem criou a partida descobre por aqui que ela começou — por isso é preciso assinar o tópico
logo depois de criar, sem esperar ninguém entrar.

### `MOVE`

Um lance foi aceito e persistido.

```json
{
  "type": "MOVE",
  "ply": 1,
  "uci": "e2e4",
  "san": "e4",
  "fenAfter": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
  "turn": "BLACK",
  "check": false,
  "whiteTimeLeftMs": 298450,
  "blackTimeLeftMs": 300000,
  "serverTimestamp": "2026-08-27T18:32:11.482Z"
}
```

| Campo | Uso no cliente |
|---|---|
| `ply` | número do meio-lance, começando em 1 — serve para detectar evento fora de ordem |
| `uci` / `san` | animar o lance e escrever a planilha |
| `fenAfter` | posição autoritativa depois do lance; use para conferir seu tabuleiro local |
| `turn` | de quem é a vez agora |
| `check` | o rei de quem joga agora está em xeque |
| `whiteTimeLeftMs` / `blackTimeLeftMs` | saldos **já com o incremento somado** para quem acabou de jogar |
| `serverTimestamp` | instante em que o servidor processou; use como marco zero para a contagem local |

Os relógios do evento são do momento do lance. O cliente continua descontando localmente a
partir de `serverTimestamp` e se corrige no evento seguinte.

### `DRAW_OFFERED`

```json
{ "type": "DRAW_OFFERED", "offeredBy": "WHITE" }
```

A proposta fica pendente até ser respondida, até alguém jogar um lance (o que a limpa
automaticamente, `game/service/GamePlayService.java:76`) ou até o servidor reiniciar — o
registro é em memória.

Os dois jogadores recebem o evento, inclusive quem propôs. Filtre por `offeredBy` para saber
se deve mostrar "empate proposto" ou "aguardando resposta".

### `GAME_OVER`

Último evento da partida, por qualquer motivo.

```json
{ "type": "GAME_OVER", "result": "BLACK_WIN", "termination": "CHECKMATE" }
```

| `result` | `termination` possível |
|---|---|
| `WHITE_WIN` / `BLACK_WIN` | `CHECKMATE`, `RESIGNATION`, `TIMEOUT` |
| `DRAW` | `STALEMATE`, `INSUFFICIENT_MATERIAL`, `THREEFOLD_REPETITION`, `FIFTY_MOVE`, `DRAW_AGREEMENT` |

Quando a partida acaba **por causa de um lance** (mate, afogamento, repetição…), chegam
**dois eventos em sequência**: primeiro o `MOVE`, depois o `GAME_OVER`
(`game/service/GamePlayService.java:93-94`). Anime o lance antes de mostrar o resultado.

## Erros

Erros vão só para quem enviou a mensagem, em `/user/queue/errors`, no formato
`ErrorMessage` (`game/dto/ErrorMessage.java`):

```json
{ "code": "NOT_YOUR_TURN", "message": "Nao e a sua vez" }
```

| `code` | Causa mais comum |
|---|---|
| `ILLEGAL_MOVE` | lance ilegal na posição atual, ou promoção faltando |
| `NOT_YOUR_TURN` | o adversário ainda não jogou |
| `NOT_A_PLAYER` | sessão de quem não joga essa partida |
| `GAME_NOT_IN_PROGRESS` | a partida acabou, foi cancelada ou nem começou |
| `NO_DRAW_OFFER` | resposta a um empate que ninguém propôs |
| `CANNOT_ANSWER_OWN_OFFER` | tentativa de aceitar a própria proposta |
| `UNAUTHENTICATED` | CONNECT sem token válido |
| `INTERNAL_ERROR` | falha inesperada (logada com stack trace no servidor) |

Os handlers ficam em `game/GameWebSocketController.java:88-104`. **O
`GlobalExceptionHandler` do REST não alcança mensagens STOMP** — daí a duplicação
proposital. Ver [modulos/common.md](modulos/common.md).

Note que não há `VALIDATION_ERROR` aqui: um `MoveMessage` que viole o regex é rejeitado pelo
`@Valid` antes do handler e cai no tratador genérico, chegando como `INTERNAL_ERROR`. Valide
as casas no cliente antes de enviar.

## Ciclo de vida de uma partida, ponta a ponta

```mermaid
sequenceDiagram
    participant B as Cliente brancas
    participant S as Servidor
    participant P as Cliente pretas

    B->>S: POST /api/v1/games (REST)
    S-->>B: 201 com joinCode e gameId
    B->>S: CONNECT + SUBSCRIBE /topic/game/{id}
    Note over B: mostra o codigo e espera

    P->>S: POST /api/v1/games/join (REST)
    S-->>P: 200 com o estado
    S->>B: GAME_STARTED
    S->>P: GAME_STARTED
    P->>S: CONNECT + SUBSCRIBE /topic/game/{id}

    B->>S: SEND /app/game/{id}/move e2e4
    S->>B: MOVE ply 1
    S->>P: MOVE ply 1

    P->>S: SEND /app/game/{id}/move e7e5
    S->>B: MOVE ply 2
    S->>P: MOVE ply 2

    Note over S: o tempo das brancas zera
    S->>B: GAME_OVER BLACK_WIN TIMEOUT
    S->>P: GAME_OVER BLACK_WIN TIMEOUT
```

## Reconexão

Não existe replay de eventos: o que passou enquanto o cliente estava fora, passou. A
recuperação é sempre a mesma:

1. Reconecte (CONNECT com um access token válido).
2. Assine `/topic/game/{gameId}` de novo.
3. Chame `GET /api/v1/games/{gameId}` e **substitua** o estado local pelo que voltou — ele traz
   a lista completa de lances, a posição e os relógios já corrigidos.

## Testando na mão

O jeito mais rápido é o teste que já existe: `GameWebSocketIntegrationTest` sobe servidor,
banco e cliente STOMP de verdade. Use-o como referência executável do protocolo:

```bash
./mvnw -Dtest=GameWebSocketIntegrationTest test
```

Para testar contra a aplicação rodando, abra duas abas do navegador, faça login com usuários
diferentes e use o console:

```javascript
// carregue @stomp/stompjs na pagina antes
const c = new StompJs.Client({
  brokerURL: "ws://localhost:7777/ws",
  connectHeaders: { Authorization: "Bearer " + token },
  debug: console.log,
});
c.onConnect = () => {
  c.subscribe("/topic/game/" + gameId, (m) => console.log("evento", m.body));
  c.subscribe("/user/queue/errors", (m) => console.warn("erro", m.body));
  c.publish({
    destination: `/app/game/${gameId}/move`,
    body: JSON.stringify({ from: "e2", to: "e4" }),
  });
};
c.activate();
```

Se nada chegar, o suspeito número um é o CORS: a origem da aba precisa estar em
`CORS_ALLOWED_ORIGINS`. O número dois é o token — sem `Principal`, a primeira mensagem volta
como `UNAUTHENTICATED` na fila de erros.

## Veja também

- [api-rest.md](api-rest.md) — como chegar até uma partida iniciada
- [modulos/game.md](modulos/game.md) — o que cada handler faz por dentro
- [modulos/relogio.md](modulos/relogio.md) — de onde vêm os tempos do `MOVE`
- [testes.md](testes.md) — o teste ponta a ponta do WebSocket
