# Glossário

O que você encontra aqui: o vocabulário de xadrez e de sistema que aparece no código, no banco e
nos payloads — com o significado **e** onde cada termo vive na base.

---

## Notação e representação

### FEN — *Forsyth-Edwards Notation*

Uma linha de texto que descreve uma posição completa.

```
rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1
└──────── peças, da 8ª à 1ª fileira ────────┘ │  │   │ │ │
                                              │  │   │ │ └─ número do lance
                                              │  │   │ └─── meios-lances desde captura/peão
                                              │  │   └───── casa de en passant
                                              │  └───────── direitos de roque
                                              └──────────── de quem é a vez
```

**Onde aparece:** `Game.INITIAL_FEN` (`game/Game.java:32`), coluna `games.current_fen`, coluna
`moves.fen_after`, campos `currentFen` e `fenAfter` nos DTOs.

O quinto campo — meios-lances desde a última captura ou lance de peão — é o que a chesslib lê em
`getHalfMoveCounter()` para aplicar a regra dos 50 lances.

### SAN — *Standard Algebraic Notation*

A notação que humanos leem: `e4`, `Nf3`, `O-O`, `Qxd5+`, `gxh8=Q#`.

| Símbolo | Significado |
|---|---|
| `N B R Q K` | cavalo, bispo, torre, dama, rei (peão não tem letra) |
| `x` | captura |
| `+` | xeque |
| `#` | xeque-mate |
| `O-O` / `O-O-O` | roque curto / roque longo |
| `=Q` | promoção a dama |
| `Nbd2` | desambiguação: o cavalo da coluna b |

**Onde aparece:** coluna `moves.san`, campo `san` no `MoveEvent` e no `MoveResponse`. Gerado por
`MoveList.toSanArray()` (`game/engine/ChessLibRulesService.java:46`) — depende do contexto da
posição, por isso não pode ser derivado do UCI isolado. Use para a planilha da partida.

### UCI — *Universal Chess Interface*

A notação que máquinas usam: origem, destino e, se houver, a peça de promoção.

| Lance | UCI |
|---|---|
| Peão de e2 para e4 | `e2e4` |
| Roque curto das brancas | `e1g1` |
| Promoção a dama | `g7h8q` |
| Subpromoção a cavalo | `g7h8n` |

**Onde aparece:** coluna `moves.uci` (`VARCHAR(5)` — quatro caracteres, cinco com promoção),
campo `uci` nos eventos, e a lista devolvida por `MoveRepository.findUciHistory`, que é **a
entrada de todo o motor de regras**.

> A API não recebe UCI diretamente: o `MoveMessage` traz `from`, `to` e `promotion` separados, e
> o motor monta o UCI em `toUci` (`ChessLibRulesService.java:95`).

---

## Termos de partida

### Ply (meio-lance)

Um movimento de **um** jogador. Um "lance" completo no sentido comum são dois plies: um das
brancas e um das pretas.

- Ply 1 = primeiro movimento das brancas
- Ply 2 = primeiro movimento das pretas
- Ply ímpar = brancas; ply par = pretas

**Onde aparece:** coluna `moves.ply` (começa em 1, com `CHECK ply > 0`), campo `ply` no
`MoveEvent`, e a unicidade `uq_moves_game_ply`. Calculado como `history.size() + 1`
(`game/service/GamePlayService.java:70`).

O cliente pode usar o `ply` para detectar evento fora de ordem ou perdido.

### Turno / vez (`turn`, `sideToMove`)

De quem é a vez de jogar. Sempre derivado do replay do histórico, nunca de um contador.

**Onde aparece:** `PositionInfo.sideToMove`, `MoveResult.sideToMove`, campo `turn` no
`GameStateResponse` e no `MoveEvent`.

### Xeque (`check`)

O rei está sob ataque e o jogador é obrigado a resolver a ameaça.

**Onde aparece:** campo `check`, vindo de `board.isKingAttacked()`. Significa sempre **"o rei de
quem joga agora está atacado"** — depois de um lance das brancas que dá xeque, o evento traz
`check: true` com `turn: "BLACK"`.

### Xeque-mate (`CHECKMATE`)

Rei em xeque sem nenhum lance legal que resolva. Fim de partida com vencedor.

**Onde aparece:** `Termination.CHECKMATE`. Quem perde é **quem está na vez** — a inversão está
em `ChessLibRulesService.java:58-62`.

### Afogamento / *stalemate* (`STALEMATE`)

O jogador da vez **não** está em xeque, mas não tem nenhum lance legal. É **empate**, não
derrota — a confusão mais comum de quem está aprendendo.

### Material insuficiente (`INSUFFICIENT_MATERIAL`)

Nenhum dos lados tem peças suficientes para dar mate (rei contra rei, rei e bispo contra rei,
rei e cavalo contra rei). Empate imediato, detectado por `board.isInsufficientMaterial()`.

### Tríplice repetição (`THREEFOLD_REPETITION`)

A mesma posição — mesmas peças, mesma vez, mesmos direitos de roque e de en passant — ocorreu
três vezes.

**Nota de implementação:** aqui o empate é **automático**. Em torneio oficial, a tríplice
repetição é *reivindicável* pelo jogador, e só a quíntupla é automática. A simplificação está em
`ChessLibRulesService.java:70`.

### Regra dos 50 lances (`FIFTY_MOVE`)

50 lances de cada lado — **100 meios-lances** — sem nenhuma captura e sem nenhum movimento de
peão. Empate.

**Onde aparece:** `board.getHalfMoveCounter() >= 100` (`ChessLibRulesService.java:73`). O
contador é o quinto campo do FEN, zerado a cada captura ou lance de peão. `FiftyMoveRuleTest`
cobre a fronteira: 99 não empata, 100 empata. Também é automático, e não reivindicável.

### Desistência (`RESIGNATION`)

Um jogador abandona; o adversário vence. Sem confirmação e sem desfazer
(`GamePlayService.resign`).

### Empate por acordo (`DRAW_AGREEMENT`)

Um propõe, o outro aceita. A proposta pendente vive em memória, no `DrawOfferRegistry`, e é
apagada por qualquer lance.

### Flag / queda de bandeira (`TIMEOUT`)

O tempo de um jogador chegou a zero; o adversário vence. O nome vem dos relógios analógicos, em
que uma bandeirinha caía quando o ponteiro passava das 12.

**Onde aparece:** `Termination.TIMEOUT`, adicionado ao `CHECK` do banco na `V5__add_clock.sql`, e
o método `GamePlayService.flag` (`:123`).

---

## Movimentos especiais

### Roque (*castling*)

Rei e torre se movem juntos. Enviado como o movimento do **rei** — a torre é reposicionada pelo
motor.

| Roque | `from` → `to` | SAN |
|---|---|---|
| Curto, brancas | `e1` → `g1` | `O-O` |
| Longo, brancas | `e1` → `c1` | `O-O-O` |
| Curto, pretas | `e8` → `g8` | `O-O` |
| Longo, pretas | `e8` → `c8` | `O-O-O` |

Os direitos de roque restantes ficam no quarto campo do FEN (`KQkq`).

### En passant

Um peão que avança duas casas passando ao lado de um peão adversário pode ser capturado como se
tivesse avançado só uma — mas **apenas no lance imediatamente seguinte**.

Enviado como um lance normal (`e5` → `d6`); o peão capturado, que está em outra casa, é removido
pelo motor. A casa disponível fica no terceiro campo do FEN.

### Promoção e subpromoção

Peão que alcança a última fileira vira outra peça. **Promoção a dama** é o caso comum;
**subpromoção** é escolher torre, bispo ou cavalo — raro, mas às vezes necessário (um cavalo pode
dar mate onde a dama daria afogamento).

O campo `promotion` aceita `q`, `r`, `b` ou `n`, minúsculo. **Sem ele, um lance de promoção é
recusado como ilegal.**

### Cravada (*pin*)

Peça que não pode se mover porque expõe o rei a um xeque. Não é um conceito explícito no código:
o lance simplesmente não aparece em `board.legalMoves()`. Há teste dedicado ("recusa lance de
peca sob cravada absoluta").

---

## Controle de tempo

### Tempo inicial e incremento

`initialSeconds` é o tempo de cada jogador no começo; `incrementSeconds` é o bônus somado ao
relógio **depois** de cada lance (estilo Fischer).

Notação usual: **5+2** = 5 minutos com 2 segundos de incremento.

Limites aceitos: inicial de 10 a 10800 segundos (3 horas), incremento de 0 a 60
(`game/dto/TimeControl.java`).

| Categoria | Tempo típico |
|---|---|
| Bullet | 1+0, 2+1 |
| Blitz | 3+2, 5+0 |
| Rápido | 10+0, 15+10 |
| Clássico | 30+0 e acima |

### Saldo e marco temporal

O banco guarda o **saldo congelado no último lance** (`white_time_left_ms`,
`black_time_left_ms`) e o instante desse lance (`last_move_at`). O tempo real é sempre
calculado: `saldo - (agora - last_move_at)`. Ver [modulos/relogio.md](modulos/relogio.md).

---

## Termos do sistema

### Join code (código de convite)

Código de 6 caracteres que identifica uma partida em espera. Alfabeto
`23456789ABCDEFGHJKMNPQRSTUVWXYZ` — sem `0`, `O`, `1`, `I` e `L`, que são os caracteres mais
confundidos ao ditar ou copiar.

**Onde aparece:** coluna `games.join_code` (única), `JoinCodeGenerator`, campo `joinCode` no
`GameStateResponse`. Normalizado no `JoinGameRequest` (trim e maiúsculas).

### `GameStatus`

| Valor | Significado |
|---|---|
| `WAITING` | criada, esperando o segundo jogador |
| `IN_PROGRESS` | em andamento, relógio correndo |
| `FINISHED` | terminada com resultado |
| `ABORTED` | cancelada antes de começar, sem resultado |

### `GameResult`

`WHITE_WIN`, `BLACK_WIN`, `DRAW` — ou `null` enquanto a partida não terminou.

### `Termination`

Como a partida acabou: `CHECKMATE`, `STALEMATE`, `INSUFFICIENT_MATERIAL`,
`THREEFOLD_REPETITION`, `FIFTY_MOVE`, `RESIGNATION`, `DRAW_AGREEMENT`, `TIMEOUT`.

### STOMP

*Simple Text Oriented Messaging Protocol* — o protocolo de mensagens que roda sobre o WebSocket.
Traz o vocabulário de destinos (`/topic`, `/queue`, `/app`), frames (`CONNECT`, `SUBSCRIBE`,
`SEND`) e headers. Ver [websocket.md](websocket.md).

### Access token e refresh token

O primeiro autentica cada requisição e dura 15 minutos; o segundo serve só para obter um access
token novo e dura 7 dias. Ver [modulos/auth.md](modulos/auth.md).

---

## Referências externas

- [Regras da FIDE](https://handbook.fide.com/chapter/E012023) — as regras oficiais
- [chesslib](https://github.com/bhlangonijr/chesslib) — a biblioteca usada
- [Especificação STOMP 1.2](https://stomp.github.io/stomp-specification-1.2.html)

## Veja também

- [modulos/engine.md](modulos/engine.md) — como as regras são aplicadas
- [modulos/relogio.md](modulos/relogio.md) — a aritmética do tempo
- [websocket.md](websocket.md) — onde esses termos aparecem nos payloads
