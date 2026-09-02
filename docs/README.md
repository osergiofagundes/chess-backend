# Documentação do chess

Documentação do backend do xadrez online. Escrita para dois leitores: **humanos** que precisam
entender e evoluir o sistema, e **agentes de IA** que precisam de contexto verificável antes de
editar. Toda afirmação sobre comportamento aponta para `arquivo.java:linha`, então dá para
conferir a fonte em vez de confiar no texto.

## Por onde começar

| Se você… | Comece por |
|---|---|
| nunca viu este projeto | [arquitetura.md](arquitetura.md) |
| vai consumir a API (frontend) | [api-rest.md](api-rest.md) e [websocket.md](websocket.md) |
| vai mexer no banco | [banco-de-dados.md](banco-de-dados.md) |
| precisa subir o ambiente | [desenvolvimento.md](desenvolvimento.md) |
| é um agente de IA | [../CLAUDE.md](../CLAUDE.md) primeiro |
| quer entender *por que* é assim | [decisoes.md](decisoes.md) |
| esbarrou num termo de xadrez | [glossario.md](glossario.md) |

## Índice

### Visão geral

- **[arquitetura.md](arquitetura.md)** — fronteiras do sistema, fluxo de uma requisição REST,
  fluxo de um lance de ponta a ponta, modelo de concorrência e limitações conhecidas.
- **[decisoes.md](decisoes.md)** — nove decisões técnicas registradas no formato
  contexto → decisão → consequência.

### Contratos

- **[api-rest.md](api-rest.md)** — todas as rotas REST com payloads, validações, status,
  catálogo de códigos de erro e exemplos `curl` encadeados.
- **[websocket.md](websocket.md)** — handshake, autenticação no frame CONNECT, destinos STOMP,
  os quatro eventos de partida e a fila de erros.

### Interno

- **[banco-de-dados.md](banco-de-dados.md)** — ERD, cada tabela coluna a coluna, o motivo de
  cada constraint e o fluxo de migrations do Flyway.
- **[modulos/auth.md](modulos/auth.md)** — registro, login, JWT, rotação de refresh token.
- **[modulos/game.md](modulos/game.md)** — máquina de estados da partida, código de convite,
  divisão entre `GameService` e `GamePlayService`.
- **[modulos/engine.md](modulos/engine.md)** — adaptador da chesslib e o replay do histórico
  UCI como fonte de verdade da posição.
- **[modulos/relogio.md](modulos/relogio.md)** — `ClockService`, `TimeoutScheduler` e
  `GameClockCoordinator`.
- **[modulos/common.md](modulos/common.md)** — `ApiError`, hierarquia de exceções e os dois
  caminhos de tratamento de erro (REST e STOMP).

### Operação

- **[configuracao.md](configuracao.md)** — variáveis de ambiente, perfis, actuator, checklist
  de produção.
- **[desenvolvimento.md](desenvolvimento.md)** — setup local, comandos, como criar uma
  migration, troubleshooting.
- **[testes.md](testes.md)** — as três camadas de teste, o que está coberto e o que não está.

### Apoio

- **[glossario.md](glossario.md)** — FEN, SAN, UCI, ply, incremento, flag e o resto do
  vocabulário de xadrez usado no código.

## Convenções desta documentação

- Português com acentos. Os **comentários dentro do código** seguem outro padrão: português
  sem acentos, como já estava no repositório.
- Diagramas em Mermaid — renderizam no GitHub e continuam legíveis como texto.
- Referências no formato `arquivo.java:linha`, relativas a `src/main/java/com/sergiofagundes/chess/`.
- A documentação descreve o sistema **como ele é hoje**, incluindo inconsistências. O que está
  errado ou faltando aparece como "limitação conhecida", não é escondido.
