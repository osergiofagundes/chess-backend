# Configuração

O que você encontra aqui: cada variável de ambiente com default e efeito, os blocos de
configuração da aplicação, o que o actuator expõe e o checklist do que precisa mudar antes de
ir para produção.

Arquivos envolvidos: `src/main/resources/application.yml`, `application-dev.yml`, `.env`,
`.env.example`, `compose.yaml`.

## Variáveis de ambiente

| Variável | Default | Lida em | Efeito |
|---|---|---|---|
| `SERVER_PORT` | `7777` | `application.yml` | porta HTTP da aplicação |
| `DB_URL` | `jdbc:postgresql://localhost:5435/chess` | `application.yml` | URL JDBC do Postgres |
| `DB_USERNAME` | `chess` | `application.yml` | usuário do banco |
| `DB_PASSWORD` | `chess` | `application.yml` | senha do banco |
| `JWT_SECRET` | segredo de desenvolvimento | `app.jwt.secret` | chave HMAC do access token |
| `COOKIE_SECURE` | `false` | `app.auth.cookie-secure` | marca o cookie de refresh como `Secure` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:9999` | `app.cors.allowed-origins` | origens liberadas para REST **e** WebSocket |

`CORS_ALLOWED_ORIGINS` aceita uma lista separada por vírgula — o Spring converte para
`List<String>`:

```
CORS_ALLOWED_ORIGINS=http://localhost:3000,https://chess.exemplo.com
```

### Cuidado com os defaults divergentes

Os mesmos parâmetros têm valores diferentes dependendo de onde você olha:

| Parâmetro | `application.yml` | `compose.yaml` | `.env.example` |
|---|---|---|---|
| porta do Postgres | `5435` | `5432` interno, `5555` no host | — |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:9999` | `http://localhost:5175` | `http://localhost:9999` |

Nenhum desses valores está errado no seu contexto, mas eles **não combinam entre si**. Rodando
a app fora do compose, defina `DB_URL` e `CORS_ALLOWED_ORIGINS` explicitamente em vez de confiar
no default. Veja [desenvolvimento.md](desenvolvimento.md).

Detalhe adicional: o `Dockerfile` declara `EXPOSE 8088` — a porta default no último commit, antes de
o working copy passar para 7777. `EXPOSE` é apenas documentação da imagem; quem publica a porta
é o `compose.yaml`, e ele publica 7777 corretamente. Nada quebra por causa disso.

## O arquivo `.env`

Lido automaticamente pelo `docker compose`. **Não é lido** quando você roda a app pelo Maven —
nesse caso, exporte as variáveis no shell ou configure a IDE.

```bash
cp .env.example .env
```

`.env` está no `.gitignore` e no `.dockerignore`: não vai para o repositório nem para dentro da
imagem.

## Blocos da aplicação

### `app.jwt`

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:…}
    access-token-ttl: PT15M
    refresh-token-ttl: P7D
```

Mapeado por `JwtProperties` (`auth/security/JwtProperties.java`), um record com
`@ConfigurationProperties("app.jwt")`, ativado pelo `@ConfigurationPropertiesScan` em
`ChessApplication`.

Os TTLs são `Duration` no formato ISO-8601: `PT15M` = 15 minutos, `P7D` = 7 dias. Estão fixos
no YAML — para torná-los configuráveis por ambiente, é preciso adicionar o placeholder.

> **O segredo precisa ter pelo menos 256 bits (32 bytes) reais.** `Keys.hmacShaKeyFor`
> (`JwtService.java:29`) rejeita chaves menores para HS256, e a aplicação **não sobe**. Gere um
> assim:
> ```bash
> openssl rand -base64 48
> ```

Trocar o `JWT_SECRET` invalida todos os access tokens existentes — os clientes precisam
refazer o refresh. Os refresh tokens continuam válidos, porque não dependem dessa chave.

### `app.auth`

```yaml
app:
  auth:
    cookie-secure: ${COOKIE_SECURE:false}
```

Lido diretamente por `@Value` em `RefreshTokenCookies` (`:26`). Com `true`, o cookie ganha o
atributo `Secure` e o navegador só o envia por HTTPS. Deixar `false` em produção significa
expor o refresh token em texto claro na rede.

### `app.cors`

```yaml
app:
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:9999}
```

Consumido em dois lugares:

| Onde | Uso |
|---|---|
| `config/CorsConfig.java` | CORS de `/api/**`: métodos `GET, POST, PUT, DELETE, OPTIONS`, todos os headers, `allowCredentials(true)`, cache de preflight de 1 hora |
| `config/WebSocketConfig.java:31` | `setAllowedOriginPatterns` no handshake de `/ws` |

`allowCredentials(true)` é necessário para o cookie de refresh funcionar cross-origin — e por
isso a lista de origens **não pode ser `*`**. O Spring rejeita a combinação.

CORS cobre `/api/**` apenas. `/actuator/**` não está incluído.

### Datasource e JPA

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate.jdbc.time_zone: UTC
```

| Configuração | Por quê |
|---|---|
| `ddl-auto: validate` | o Hibernate confere entidades contra o esquema e **recusa subir** se divergirem; quem cria tabela é o Flyway |
| `open-in-view: false` | a sessão JPA fecha com a transação; sem lazy loading acidental na serialização |
| `hibernate.jdbc.time_zone: UTC` | todo `Instant` vai e volta em UTC, independente do fuso da máquina |

### Flyway

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
```

Migrations aplicadas no startup, antes da validação do Hibernate. Ver
[banco-de-dados.md](banco-de-dados.md).

## Perfis

Só existe um perfil além do default: **`dev`**, em `application-dev.yml`, que aumenta o log:

```yaml
logging:
  level:
    org.springframework.web.socket: DEBUG
    org.springframework.messaging: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.orm.jdbc.bind: TRACE
```

Ativando:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
SPRING_PROFILES_ACTIVE=dev java -jar target/chess-0.0.1-SNAPSHOT.jar
```

`org.hibernate.orm.jdbc.bind: TRACE` imprime os **valores** dos parâmetros SQL — incluindo
hashes de senha e de token. Nunca ative em produção.

Não existe `application-prod.yml`: em produção, tudo vem de variáveis de ambiente.

## Actuator

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when-authorized
```

| Endpoint | Acesso | Conteúdo |
|---|---|---|
| `/actuator/health` | público (`SecurityConfig.java:39`) | `{"status":"UP"}` |
| `/actuator/info` | exige autenticação | vazio, sem contribuidores configurados |

`show-details: when-authorized` faz o corpo detalhado (banco, disco) só aparecer para
requisições autenticadas — o público vê apenas `UP` ou `DOWN`, sem topologia interna.

O `compose.yaml` usa esse endpoint como healthcheck, com `start_period: 40s` para dar tempo às
migrations.

## Segurança da aplicação

`config/SecurityConfig.java`:

| Configuração | Valor | Motivo |
|---|---|---|
| CSRF | desabilitado | API sem sessão; o cookie de refresh é `SameSite=Lax` e restrito a `/api/v1/auth` |
| Sessão | `STATELESS` | nada de `JSESSIONID` |
| `httpBasic`, `formLogin`, `logout` | desabilitados | autenticação é só por JWT |
| Rotas públicas | `/actuator/health/**`, `/api/v1/auth/**`, `/ws/**` | o resto exige token |
| Filtro JWT | antes de `UsernamePasswordAuthenticationFilter` | popula o `SecurityContext` |
| `PasswordEncoder` | BCrypt força 12 | custo deliberado contra força bruta |

`/ws/**` liberado **não** significa WebSocket público: a autenticação acontece no frame STOMP
CONNECT. Ver [websocket.md](websocket.md).

## Scheduler

`config/SchedulingConfig.java` define o `TaskScheduler` usado pelos timeouts de relógio:

| Ajuste | Valor | Motivo |
|---|---|---|
| `poolSize` | 2 | as tarefas são curtas; o trabalho pesado está na transação |
| `threadNamePrefix` | `chess-clock-` | identifica a origem nos logs e em thread dumps |
| `removeOnCancelPolicy` | `true` | tarefas canceladas saem da fila em vez de se acumular |

Duas threads bastam porque cada disparo só consulta e talvez encerre uma partida. Um volume
grande de partidas simultâneas expirando ao mesmo tempo pediria mais.

## Checklist de produção

| Item | Como |
|---|---|
| `JWT_SECRET` forte | `openssl rand -base64 48`, guardado em cofre de segredos |
| `COOKIE_SECURE=true` | obrigatório; sem isso o refresh token trafega em claro |
| `CORS_ALLOWED_ORIGINS` explícito | só os domínios reais do frontend, nunca `*` |
| `DB_PASSWORD` forte | não usar o `chess` do compose |
| HTTPS na frente | terminação TLS em proxy reverso; `wss://` para o WebSocket |
| Perfil `dev` desligado | vaza SQL e valores de parâmetros no log |
| `.env` fora do git | já coberto pelo `.gitignore` |
| Uma réplica só | o broker e os registries são em memória — ver [arquitetura.md](arquitetura.md#limitações-conhecidas) |

O que **não** está resolvido e precisa de decisão antes de um ambiente público: rate limiting no
login e no registro, limpeza de refresh tokens expirados, e recuperação dos timeouts de relógio
depois de um restart.

## Veja também

- [desenvolvimento.md](desenvolvimento.md) — subir o ambiente local
- [banco-de-dados.md](banco-de-dados.md) — Flyway e `ddl-auto`
- [modulos/auth.md](modulos/auth.md) — o que os TTLs significam na prática
- [websocket.md](websocket.md) — CORS no handshake
