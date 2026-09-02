# Decisões técnicas

O que você encontra aqui: nove decisões que moldaram este sistema, no formato
**contexto → decisão → consequência**. São as escolhas que não dá para deduzir lendo o código,
só o resultado delas.

Elas não são imutáveis. Registrar o contexto serve justamente para que uma revisão futura saiba
o que estava em jogo — e o que precisa mudar junto.

---

## 1. Todas as regras de xadrez no servidor

**Contexto.** Alguém precisa decidir se um lance é legal. Poderia ser o cliente — ele já desenha
o tabuleiro e precisa mostrar os lances possíveis de qualquer jeito. Validar dos dois lados
duplica lógica.

**Decisão.** O servidor é a única autoridade. O cliente envia origem, destino e promoção; toda
validação, aplicação e detecção de fim de partida acontece em `ChessLibRulesService`.

**Consequência.**

- Um cliente adulterado não consegue trapacear: o lance ilegal simplesmente não passa.
- Cada lance custa uma ida e volta ao servidor. O cliente pode animar de forma otimista, mas
  precisa saber reverter quando vier `ILLEGAL_MOVE`.
- O frontend ainda precisa de lógica de xadrez para destacar lances possíveis — a duplicação não
  desaparece, mas a versão do cliente é só cosmética.

---

## 2. A posição vem do replay do histórico, não do FEN salvo

**Contexto.** Existem duas formas de saber a posição atual: ler `games.current_fen` ou
reproduzir todos os lances de `moves`. A primeira é O(1); a segunda, O(n).

**Decisão.** Reproduzir o histórico. `current_fen` fica como cache de leitura, e a tabela
`moves` é a fonte de verdade.

**Consequência.**

- Tríplice repetição, regra dos 50 lances e notação SAN correta saem de graça — um FEN isolado
  não carrega as posições anteriores.
- Some uma classe inteira de bugs: não existe "o FEN salvo divergiu do histórico".
- O custo é O(n) por operação. Com 100 a 150 meios-lances por partida, isso é trabalho de
  microssegundos.
- **Quando revisitar:** milhares de partidas simultâneas. A saída seria manter um `Board` em
  cache por partida ativa, sem mudar a interface `ChessRulesService`.

Detalhes em [modulos/engine.md](modulos/engine.md).

---

## 3. Access token JWT curto + refresh token opaco em cookie

**Contexto.** Três caminhos possíveis: sessão no servidor (simples, mas com estado e difícil de
escalar), um JWT longo (sem estado, mas impossível de revogar), ou a combinação dos dois.

**Decisão.** Access token JWT HS256 de 15 minutos no header `Authorization`, mais refresh token
opaco de 7 dias em cookie `HttpOnly` restrito a `/api/v1/auth`.

**Consequência.**

- Validar uma requisição não toca o banco: só verifica a assinatura.
- O access token **não é revogável**; a mitigação é durar pouco. Logout não corta acesso
  instantaneamente.
- O refresh token, sendo opaco e persistido, é revogável de verdade.
- O cookie `HttpOnly` protege o refresh contra XSS. O access token, guardado em memória pelo
  cliente, é o que fica exposto — e é justamente o de vida curta.
- O `Path` restrito faz o cookie não acompanhar as rotas de partida, reduzindo a superfície de
  CSRF (que já está mitigado por `SameSite=Lax`).

---

## 4. Refresh token guardado como hash, com rotação a cada uso

**Contexto.** O refresh token precisa ser verificável pelo servidor, o que normalmente significa
guardá-lo. Guardar em claro transforma um vazamento de banco em sequestro de todas as sessões.

**Decisão.** Persistir apenas o SHA-256 hexadecimal (`refresh_tokens.token_hash`), e revogar o
token apresentado no momento em que ele é usado, emitindo outro.

**Consequência.**

- Vazar a tabela não permite forjar sessão: o valor em claro só existe no cookie do cliente.
- SHA-256 puro basta — o token tem 256 bits de entropia real, então não há ataque de dicionário;
  BCrypt só acrescentaria 250 ms a cada refresh.
- A rotação limita drasticamente a janela útil de um token roubado.
- **O que ficou faltando:** detecção de reuso. Um token já revogado, se apresentado, devolve 401
  — mas o sistema não conclui "houve roubo" nem revoga a cadeia. `revokeAllForUser` existe para
  isso e não tem chamador.
- A tabela nunca é limpa: tokens revogados e expirados ficam para sempre.

---

## 5. Autenticação do WebSocket no frame CONNECT

**Contexto.** O `WebSocket` nativo do browser **não permite definir headers HTTP** no handshake.
As alternativas seriam token na query string (que aparece em log de acesso e em referer) ou
cookie (que exigiria alargar o `Path` do refresh, ou criar outro cookie).

**Decisão.** Liberar o handshake `/ws/**` no Spring Security e autenticar no frame STOMP
CONNECT, via `StompAuthChannelInterceptor`.

**Consequência.**

- O token nunca aparece em URL.
- `permitAll` no `/ws/**` é enganoso à primeira vista: parece endpoint público, mas a
  autenticação só mudou de lugar. Daí o comentário explicativo em `SecurityConfig.java:41`.
- O token **não é revalidado** durante a sessão: uma conexão aberta continua funcionando depois
  que o access token expira. Para renovar de fato, é preciso reconectar.
- Não há autorização no SUBSCRIBE: qualquer sessão conectada pode observar
  `/topic/game/{qualquer-id}`. A autorização real está na escrita, via `requirePlayer`. Se
  sigilo de partida virar requisito, é preciso um interceptor no SUBSCRIBE.

---

## 6. Lock pessimista por partida

**Contexto.** Três atores escrevem na mesma partida: os dois jogadores e o scheduler de timeout.
Sem controle, dois lances concorrentes poderiam receber o mesmo `ply`, ou um lance e um timeout
poderiam encerrar a partida duas vezes. As opções eram versionamento otimista (`@Version` com
retry) ou lock pessimista (`SELECT FOR UPDATE`).

**Decisão.** Lock pessimista, via `GameRepository.findByIdForUpdate`.

**Consequência.**

- A serialização é simples e correta: quem chega primeiro processa, o outro espera.
- Não é preciso escrever laço de retry nem tratar `OptimisticLockException` — que é justamente o
  código que costuma ficar errado.
- O lock é por linha, então partidas diferentes não se bloqueiam.
- Uma transação lenta segura a partida — mas as transações aqui são curtas: uma leitura de
  histórico, um replay e dois inserts.
- Em xadrez, conflito real é raro (só um lado joga por vez), então o custo do lock pessimista é
  quase sempre zero.
- `uq_moves_game_ply` continua no banco como segunda linha de defesa.

---

## 7. Broker STOMP em memória e registries locais

**Contexto.** O Spring oferece um broker simples embutido ou a integração com um broker externo
(RabbitMQ, ActiveMQ). Propostas de empate e agendamentos de relógio também precisam de algum
lugar para viver.

**Decisão.** `enableSimpleBroker`, `DrawOfferRegistry` e `TimeoutScheduler` em memória.

**Consequência.**

- Zero infraestrutura adicional: o sistema inteiro é uma aplicação e um banco.
- **A aplicação só funciona com uma réplica.** Duas instâncias não compartilham tópicos: dois
  jogadores conectados em instâncias diferentes não veriam os lances um do outro.
- Reiniciar perde as propostas de empate pendentes e os agendamentos de timeout.
- **Quando revisitar:** ao precisar de mais de uma instância, ou de resistência a restart. O
  caminho é trocar por broker externo, mover as propostas para uma coluna em `games` e recuperar
  os agendamentos no startup varrendo as partidas `IN_PROGRESS`.

Esta é a decisão com maior custo futuro do documento, e a mais fácil de esquecer.

---

## 8. Flyway com `ddl-auto: validate`

**Contexto.** O Hibernate sabe gerar esquema a partir das entidades (`ddl-auto: update`), o que é
cômodo em desenvolvimento. Mas o resultado não é revisável, não é versionado e não sobrevive a
uma revisão de código séria.

**Decisão.** Todo DDL é escrito à mão em migrations Flyway. O Hibernate só **valida**.

**Consequência.**

- O esquema é revisável em pull request, como qualquer código.
- Dá para escrever constraints que o Hibernate nunca geraria: `CHECK` de enum, índice único
  funcional sobre `LOWER(...)`, regras compostas de ocupação de lados.
- Toda mudança de entidade **exige** migration na mesma edição — do contrário, o contexto não
  sobe. Isso é atrito de propósito.
- O banco protege seus invariantes mesmo contra script manual, não só contra a aplicação.
- O `contextLoads` vira um teste de esquema barato e efetivo.

---

## 9. chesslib em vez de motor próprio, atrás de uma interface

**Contexto.** Implementar regras de xadrez corretamente é notoriamente trabalhoso: en passant,
direito de roque, promoção, cravada absoluta, tríplice repetição, material insuficiente. Cada um
com casos de borda.

**Decisão.** Usar a `chesslib` (via JitPack), isolada atrás de `ChessRulesService`, com
`ChessLibRulesService` como única classe que a conhece.

**Consequência.**

- Meses de trabalho economizados, e regras corretas desde o primeiro dia.
- Dependência de uma biblioteca de terceiros distribuída por JitPack — que precisa estar
  acessível no build (o repositório está declarado no `pom.xml`).
- A troca continua barata: 21 testes de regra escritos contra a **interface** funcionam como
  suíte de conformidade para qualquer implementação nova.
- Exceções da biblioteca não vazam: `parse` (`ChessLibRulesService.java:87`) converte tudo em
  `IllegalMoveException`, e há teste garantindo isso.

---

## Decisões menores, mas deliberadas

| Escolha | Motivo |
|---|---|
| Alfabeto do código de convite sem `0/O` e `1/I/L` | os pares que mais confundem ao ditar ou copiar |
| `PreferredColor.RANDOM` resolvido na criação | a resposta já informa a cor definitiva; nada muda depois |
| `404` em vez de `403` para partida de terceiro | um 403 confirmaria que aquele ID existe |
| Verificação de BCrypt descartável no login | tempo de resposta constante impede enumerar contas |
| Margem de 250 ms no agendamento de timeout | evita reagendamento em cascata no fim de partidas apertadas |
| `arm`/`disarm` fora da transação | agendar antes do commit deixaria o scheduler ler estado inexistente |
| Services devolvem eventos, não estado | mantém o service ignorante quanto ao transporte |
| Recusa de empate é silenciosa | simplicidade; nenhum evento é emitido |
| Comentário só para o **porquê** | o código já diz o quê |

## Veja também

- [arquitetura.md](arquitetura.md) — como essas decisões se combinam
- [modulos/engine.md](modulos/engine.md) — decisões 1, 2 e 9 em detalhe
- [modulos/auth.md](modulos/auth.md) — decisões 3, 4 e 5 em detalhe
- [modulos/relogio.md](modulos/relogio.md) — decisões 6 e 7 na prática
