# Arquitetura

O que você encontra aqui: as fronteiras do sistema, como uma requisição REST e um lance de
xadrez atravessam o código, o modelo de concorrência que impede dois lances simultâneos de se
atropelarem, e as limitações conhecidas da implementação atual.

## Fronteiras

O backend é um monolito Spring Boot com um único banco Postgres. Não há outros serviços, filas
externas nem cache distribuído.

```mermaid
flowchart LR
    C["Cliente web<br/>chess-fe, Next.js"]
    subgraph B["Backend — Spring Boot 4.1.1"]
        R["REST<br/>/api/v1/**"]
        W["STOMP<br/>/ws"]
        E["Motor de regras<br/>chesslib"]
        S["Scheduler<br/>2 threads"]
    end
    D[("Postgres 16")]

    C -->|"HTTP + Bearer JWT"| R
    C <-->|"WebSocket"| W
    R --> E
    W --> E
    R --> D
    W --> D
    S -->|"fim de tempo"| W
```

A divisão entre os dois canais é deliberada:

| Canal | Usado para | Por quê |
|---|---|---|
| REST | criar, entrar, consultar e cancelar partida | operações pontuais, iniciadas pelo cliente, que se beneficiam de status HTTP e de retry |
| STOMP | lance, desistência, empate, fim de tempo | precisam ser **empurradas** para o adversário no instante em que acontecem |

Uma consequência prática: o evento `GAME_STARTED` é publicado pelo **REST**
(`GameController.java:60`), quando o segundo jogador entra — e não por uma mensagem STOMP.
O cliente que criou a partida só descobre que ela começou porque já está inscrito no tópico.

## Camadas

```mermaid
flowchart TD
    subgraph Entrada
        AC["AuthController"]
        GC["GameController"]
        WS["GameWebSocketController"]
    end
    subgraph Aplicacao["Aplicação"]
        AS["AuthService"]
        GS["GameService"]
        GPS["GamePlayService"]
        CO["GameClockCoordinator"]
        PUB["GameEventPublisher"]
    end
    subgraph Dominio["Domínio"]
        RULES["ChessRulesService<br/>interface"]
        CLK["ClockService"]
        ENT["Game · Move · User · RefreshToken"]
    end
    subgraph Infra["Infraestrutura"]
        REPO["Repositórios JPA"]
        LIB["ChessLibRulesService<br/>chesslib"]
        SCHED["TimeoutScheduler"]
        REG["DrawOfferRegistry"]
    end

    AC --> AS
    GC --> GS
    GC --> CO
    GC --> PUB
    WS --> GPS
    WS --> CO
    WS --> PUB
    GS --> RULES
    GS --> CLK
    GPS --> RULES
    GPS --> CLK
    GPS --> REG
    CO --> SCHED
    CO --> GPS
    AS --> REPO
    GS --> REPO
    GPS --> REPO
    RULES -.implementado por.-> LIB
    REPO --> ENT
```

O ponto de inversão de dependência é o `ChessRulesService`
(`game/engine/ChessRulesService.java`): o domínio depende da interface, e a chesslib fica
isolada em `ChessLibRulesService`. Nenhuma classe fora do pacote `game.engine` importa
`com.github.bhlangonijr`.

## Fluxo de uma requisição REST

```mermaid
sequenceDiagram
    participant C as Cliente
    participant F as JwtAuthenticationFilter
    participant SC as SecurityFilterChain
    participant CT as Controller
    participant SV as Service transacional
    participant DB as Postgres

    C->>F: GET /api/v1/games/{id} + Bearer token
    F->>F: parse do JWT vira AuthenticatedUser
    F->>SC: SecurityContext preenchido
    SC->>CT: autorizado, ou 401 pelo EntryPoint
    CT->>SV: gameService.get(principal.id, gameId)
    SV->>DB: SELECT dentro da transação
    DB-->>SV: entidades
    SV-->>CT: GameStateResponse
    CT-->>C: 200 e JSON
    Note over SV,C: BusinessException em qualquer ponto vira<br/>ApiError com o status certo
```

Detalhes que importam:

- O filtro **nunca rejeita** a requisição. Se o header estiver ausente ou o token for inválido,
  ele simplesmente não popula o `SecurityContext`
  (`auth/security/JwtAuthenticationFilter.java:35`); quem devolve 401 é o `SecurityFilterChain`
  através do `RestAuthenticationEntryPoint`.
- A sessão é `STATELESS` (`config/SecurityConfig.java:34`): nenhum `JSESSIONID`, nenhum estado
  de autenticação no servidor.
- `open-in-view: false` no `application.yml` — nada de lazy loading depois que o service
  retorna. Por isso os DTOs são montados dentro da transação, em `GameService.state`
  (`game/service/GameService.java:112`).

## Fluxo de um lance

Este é o caminho crítico do sistema.

```mermaid
sequenceDiagram
    participant B as Brancas
    participant P as Pretas
    participant WC as GameWebSocketController
    participant PS as GamePlayService
    participant E as ChessRulesService
    participant DB as Postgres
    participant CO as GameClockCoordinator

    Note over B,WC: no CONNECT o JWT já virou o Principal da sessão
    B->>WC: SEND /app/game/{id}/move com from, to, promotion
    WC->>PS: applyMove(userId, gameId, message)
    PS->>DB: SELECT FOR UPDATE, lock da partida
    PS->>DB: SELECT uci FROM moves ORDER BY ply
    PS->>E: describe(histórico) para saber de quem é a vez
    alt não é a vez de quem enviou
        PS-->>WC: BusinessException NOT_YOUR_TURN
        WC-->>B: /user/queue/errors
    end
    PS->>PS: relógio zerado encerra por TIMEOUT
    PS->>E: applyMove(histórico, from, to, promotion)
    E-->>PS: uci, san, fenAfter, vez, xeque, desfecho
    PS->>PS: clock.consume desconta e soma o incremento
    PS->>DB: INSERT em moves e UPDATE em games
    PS-->>WC: MoveEvent, mais GameOverEvent se acabou
    WC->>B: /topic/game/{id}
    WC->>P: /topic/game/{id}
    WC->>CO: arm(gameId), ou disarm se a partida acabou
    CO->>CO: agenda o fim de tempo de quem joga agora
```

Observações sobre esse fluxo:

1. **A validação da vez usa o histórico, não a coluna `current_fen`.** `describe(history)`
   (`game/service/GamePlayService.java:57`) reconstrói a posição do zero. Ver
   [modulos/engine.md](modulos/engine.md).
2. **O relógio é conferido antes da regra.** Se o tempo de quem joga já acabou, o lance nem
   chega a ser avaliado: a partida termina por `TIMEOUT` (`GamePlayService.java:62-63`).
3. **A publicação acontece depois do service, no controller.** `GameEventPublisher.broadcast`
   é chamado por `GameWebSocketController.move` (`game/GameWebSocketController.java:53`), fora
   da transação — se o commit falhar, nada foi transmitido.
4. **O relógio é rearmado por último**, também no controller
   (`GameWebSocketController.java:79-86`), com `disarm` quando um `GameOverEvent` aparece na
   lista de eventos.

## Modelo de concorrência

Três fontes de escrita concorrente disputam a mesma partida: os dois jogadores e o scheduler
de timeout. A estratégia é **serializar por partida com lock pessimista**.

| Mecanismo | Onde | Papel |
|---|---|---|
| `SELECT FOR UPDATE` | `game/repository/GameRepository.java:21-23` | serializa lance, desistência, resposta de empate e a marcação de timeout |
| `TaskScheduler` de 2 threads | `config/SchedulingConfig.java` | dispara o fim de tempo; threads nomeadas `chess-clock-*` |
| `ConcurrentHashMap` | `DrawOfferRegistry`, `TimeoutScheduler` | estado volátil, fora de transação |

`GamePlayService.lockGame` (`:175`) é usado por `applyMove`, `resign` e `respondToDraw`.
`flagIfExpired` (`:108`) também pega o lock — é o que impede a corrida clássica: o jogador
envia o lance no último milissegundo enquanto o scheduler já acordou para derrubar a bandeira.
Um dos dois pega o lock primeiro; o outro encontra a partida em estado incompatível e desiste.
`offerDraw` (`:144`) é a única operação sem lock, porque só escreve no registry em memória.

O `arm` e o `disarm` do relógio ficam **fora** da transação de propósito: agendar dentro dela
poderia disparar o timeout antes do commit, e o scheduler leria um estado que ainda não existe
no banco.

## Fim de tempo

```mermaid
sequenceDiagram
    participant S as TimeoutScheduler
    participant CO as GameClockCoordinator
    participant PS as GamePlayService
    participant PUB as GameEventPublisher

    Note over S: acorda em tempo restante mais 250 ms
    S->>CO: onTimeout(gameId)
    CO->>PS: flagIfExpired(gameId)
    alt ainda sobra tempo, um lance chegou no meio
        PS-->>CO: lista vazia
        CO->>CO: arm(gameId) reagenda
    else o tempo realmente acabou
        PS-->>CO: GameOverEvent com termination TIMEOUT
        CO->>PUB: broadcast em /topic/game/{id}
    end
```

A margem de 250 ms (`GRACE`, `game/service/TimeoutScheduler.java:20`) evita que o scheduler
acorde microssegundos cedo demais, encontre "ainda sobra 1 ms" e entre em reagendamentos em
cascata. Detalhes em [modulos/relogio.md](modulos/relogio.md).

## Segurança em uma olhada

- Access token JWT HS256, TTL de 15 minutos, enviado no header `Authorization`.
- Refresh token opaco de 32 bytes, guardado **como hash SHA-256**, entregue em cookie
  `HttpOnly` com `Path=/api/v1/auth`, rotacionado a cada uso.
- Senha com BCrypt força 12 (`config/SecurityConfig.java:52`).
- CSRF desabilitado — é uma API sem sessão, e o cookie de refresh é `SameSite=Lax`.
- O handshake do WebSocket é `permitAll` (`SecurityConfig.java:43`); a autenticação real
  acontece no frame STOMP CONNECT. Ver [websocket.md](websocket.md).

## Limitações conhecidas

Nenhuma delas é bug: são consequências assumidas do escopo atual. Estão aqui para que ninguém
descubra do jeito difícil.

| Limitação | Efeito prático | Onde nasce |
|---|---|---|
| **Instância única** | broker, propostas de empate e agendamentos vivem na memória do processo | `WebSocketConfig.java:36`, `DrawOfferRegistry`, `TimeoutScheduler` |
| **Reinício perde timeouts** | uma partida em andamento fica sem agendamento até alguém interagir | `TimeoutScheduler` |
| **Sem replay de eventos** | cliente que cai no meio precisa de `GET /api/v1/games/{id}` para se ressincronizar | não há buffer de eventos |
| **Sem listagem de partidas** | não existe "minhas partidas" nem histórico | só `GET /{id}` em `GameController` |
| **Sem rota `/me`** | o cliente precisa guardar o `UserResponse` devolvido no login | `AuthController` |
| **Refresh tokens nunca são limpos** | a tabela `refresh_tokens` cresce indefinidamente | sem job de limpeza |
| **`revokeAllForUser` sem chamador** | não há "sair de todos os dispositivos" | `RefreshTokenService.java:71` |
| **Sem rate limiting** | login e registro aceitam tentativas ilimitadas | `SecurityConfig` |
| **Partida abandonada não expira** | uma partida `WAITING` fica para sempre; uma `IN_PROGRESS` só acaba quando um relógio zera | `GameService` |
| **Custo O(n) por lance** | todo o histórico é reproduzido a cada operação | [modulos/engine.md](modulos/engine.md) |

## Veja também

- [decisoes.md](decisoes.md) — o porquê de cada escolha acima
- [modulos/game.md](modulos/game.md) — a máquina de estados da partida
- [modulos/relogio.md](modulos/relogio.md) — a aritmética do relógio
- [banco-de-dados.md](banco-de-dados.md) — o esquema que sustenta tudo isso
