package com.sergiofagundes.chess.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.sergiofagundes.chess.TestcontainersConfiguration;
import com.sergiofagundes.chess.game.repository.GameRepository;
import com.sergiofagundes.chess.game.service.GameClockCoordinator;

/**
 * Exercita o caminho real: servidor de verdade, WebSocket de verdade, Postgres
 * de verdade. Sem transacao de teste -- cada caso cria seus proprios usuarios,
 * porque os handlers rodam em outra thread.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class GameWebSocketIntegrationTest {

    private static final int TIMEOUT_SECONDS = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameClockCoordinator clockCoordinator;

    @Autowired
    private TransactionTemplate transactions;

    private final HttpClient http = HttpClient.newHttpClient();
    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        // Spring 7 renomeou MappingJackson2MessageConverter (Jackson 3).
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @Test
    @DisplayName("lance valido chega aos dois jogadores com ply, SAN e FEN")
    void lanceValidoChegaAosDois() throws Exception {
        var partida = novaPartidaIniciada();

        var eventosBrancas = new LinkedBlockingQueue<Map<String, Object>>();
        var eventosPretas = new LinkedBlockingQueue<Map<String, Object>>();

        var sessaoBrancas = conectar(partida.tokenBrancas());
        var sessaoPretas = conectar(partida.tokenPretas());
        assinarTopico(sessaoBrancas, partida.gameId(), eventosBrancas);
        assinarTopico(sessaoPretas, partida.gameId(), eventosPretas);

        sessaoBrancas.send("/app/game/" + partida.gameId() + "/move", lance("e2", "e4"));

        for (var fila : java.util.List.of(eventosBrancas, eventosPretas)) {
            var evento = receber(fila);
            assertThat(evento).containsEntry("type", "MOVE");
            assertThat(evento).containsEntry("ply", 1);
            assertThat(evento).containsEntry("san", "e4");
            assertThat(evento).containsEntry("turn", "BLACK");
            assertThat((String) evento.get("fenAfter")).startsWith("rnbqkbnr/pppppppp/8/8/4P3");
        }
    }

    @Test
    @DisplayName("jogar fora da vez e recusado com NOT_YOUR_TURN")
    void recusaLanceForaDaVez() throws Exception {
        var partida = novaPartidaIniciada();

        var erros = new LinkedBlockingQueue<Map<String, Object>>();
        var sessaoPretas = conectar(partida.tokenPretas());
        assinarErros(sessaoPretas, erros);

        // Sao as brancas que comecam; as pretas tentam jogar primeiro.
        sessaoPretas.send("/app/game/" + partida.gameId() + "/move", lance("e7", "e5"));

        assertThat(receber(erros)).containsEntry("code", "NOT_YOUR_TURN");
    }

    @Test
    @DisplayName("lance ilegal e recusado com ILLEGAL_MOVE")
    void recusaLanceIlegal() throws Exception {
        var partida = novaPartidaIniciada();

        var erros = new LinkedBlockingQueue<Map<String, Object>>();
        var sessaoBrancas = conectar(partida.tokenBrancas());
        assinarErros(sessaoBrancas, erros);

        // Peao nao anda tres casas.
        sessaoBrancas.send("/app/game/" + partida.gameId() + "/move", lance("e2", "e5"));

        assertThat(receber(erros)).containsEntry("code", "ILLEGAL_MOVE");
    }

    @Test
    @DisplayName("quem nao joga a partida e recusado com NOT_A_PLAYER")
    void recusaEstranho() throws Exception {
        var partida = novaPartidaIniciada();
        var tokenEstranho = registrar("estranho_" + sufixo());

        var erros = new LinkedBlockingQueue<Map<String, Object>>();
        var sessaoEstranho = conectar(tokenEstranho);
        assinarErros(sessaoEstranho, erros);

        sessaoEstranho.send("/app/game/" + partida.gameId() + "/move", lance("e2", "e4"));

        assertThat(receber(erros)).containsEntry("code", "NOT_A_PLAYER");
    }

    @Test
    @DisplayName("desistencia encerra a partida e avisa os dois")
    void desistenciaEncerraAPartida() throws Exception {
        var partida = novaPartidaIniciada();

        var eventos = new LinkedBlockingQueue<Map<String, Object>>();
        var sessaoBrancas = conectar(partida.tokenBrancas());
        assinarTopico(sessaoBrancas, partida.gameId(), eventos);

        sessaoBrancas.send("/app/game/" + partida.gameId() + "/resign", Map.of());

        var evento = receber(eventos);
        assertThat(evento).containsEntry("type", "GAME_OVER");
        assertThat(evento).containsEntry("result", "BLACK_WIN");
        assertThat(evento).containsEntry("termination", "RESIGNATION");
    }

    @Test
    @DisplayName("xeque-mate encerra a partida junto com o lance")
    void mateEncerraAPartida() throws Exception {
        var partida = novaPartidaIniciada();

        var eventos = new LinkedBlockingQueue<Map<String, Object>>();
        var brancas = conectar(partida.tokenBrancas());
        var pretas = conectar(partida.tokenPretas());
        assinarTopico(brancas, partida.gameId(), eventos);

        // Mate do bobo: 1.f3 e5 2.g4 Qh4#
        var destino = "/app/game/" + partida.gameId() + "/move";
        brancas.send(destino, lance("f2", "f3"));
        receber(eventos);
        pretas.send(destino, lance("e7", "e5"));
        receber(eventos);
        brancas.send(destino, lance("g2", "g4"));
        receber(eventos);
        pretas.send(destino, lance("d8", "h4"));

        var moveEvent = receber(eventos);
        assertThat(moveEvent).containsEntry("san", "Qh4#");

        var gameOver = receber(eventos);
        assertThat(gameOver).containsEntry("type", "GAME_OVER");
        assertThat(gameOver).containsEntry("result", "BLACK_WIN");
        assertThat(gameOver).containsEntry("termination", "CHECKMATE");
    }

    @Test
    @DisplayName("partida termina por tempo quando o relogio de quem joga zera")
    void partidaTerminaPorTempo() throws Exception {
        var partida = novaPartidaIniciada();
        var id = UUID.fromString(partida.gameId());

        var eventos = new LinkedBlockingQueue<Map<String, Object>>();
        var sessaoPretas = conectar(partida.tokenPretas());
        assinarTopico(sessaoPretas, partida.gameId(), eventos);

        // Encurta o relogio das brancas em vez de esperar os 3 minutos reais.
        // O caminho exercitado continua sendo o de producao: agendamento,
        // recheque na hora do disparo e transmissao do GAME_OVER.
        transactions.executeWithoutResult(status -> {
            var game = gameRepository.findById(id).orElseThrow();
            game.setWhiteTimeLeftMs(300);
            game.setLastMoveAt(Instant.now());
        });
        clockCoordinator.arm(id);

        var evento = receber(eventos);
        assertThat(evento).containsEntry("type", "GAME_OVER");
        assertThat(evento).containsEntry("termination", "TIMEOUT");
        // Quem estava na vez perdeu: as brancas.
        assertThat(evento).containsEntry("result", "BLACK_WIN");
    }

    @Test
    @DisplayName("cada lance devolve os dois relogios ja atualizados")
    void lanceDevolveOsRelogios() throws Exception {
        var partida = novaPartidaIniciada();

        var eventos = new LinkedBlockingQueue<Map<String, Object>>();
        var brancas = conectar(partida.tokenBrancas());
        assinarTopico(brancas, partida.gameId(), eventos);

        brancas.send("/app/game/" + partida.gameId() + "/move", lance("e2", "e4"));

        var evento = receber(eventos);
        var brancasMs = ((Number) evento.get("whiteTimeLeftMs")).longValue();
        var pretasMs = ((Number) evento.get("blackTimeLeftMs")).longValue();

        // 3+2: as pretas nao jogaram ainda, e as brancas ganharam o incremento
        // descontado o pouco que levaram para lancar.
        assertThat(pretasMs).isEqualTo(180_000);
        assertThat(brancasMs).isGreaterThan(180_000).isLessThanOrEqualTo(182_000);
    }

    // --- infraestrutura do teste ---------------------------------------------

    private record Partida(String gameId, String tokenBrancas, String tokenPretas) {}

    private Partida novaPartidaIniciada() throws Exception {
        var criador = registrar("criador_" + sufixo());
        var convidado = registrar("convidado_" + sufixo());

        var criada = post("/api/v1/games", """
                {"preferredColor":"WHITE",
                 "timeControl":{"initialSeconds":180,"incrementSeconds":2}}
                """, criador);
        String gameId = JsonPath.read(criada, "$.id");
        String code = JsonPath.read(criada, "$.joinCode");

        post("/api/v1/games/join", "{\"code\":\"%s\"}".formatted(code), convidado);
        return new Partida(gameId, criador, convidado);
    }

    private String registrar(String username) throws Exception {
        var body = """
                {"username":"%s","email":"%s@example.com","password":"senha-forte-123"}
                """.formatted(username, username);
        return JsonPath.read(post("/api/v1/auth/register", body, null), "$.accessToken");
    }

    private String post(String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("POST %s -> %s", path, response.body()).isLessThan(300);
        return response.body();
    }

    private StompSession conectar(String token) throws Exception {
        var connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        return stompClient.connectAsync(
                        "ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void assinarTopico(StompSession session, String gameId,
                                      BlockingQueue<Map<String, Object>> destino) {
        session.subscribe("/topic/game/" + gameId, coletorPara(destino));
    }

    private static void assinarErros(StompSession session,
                                     BlockingQueue<Map<String, Object>> destino) {
        session.subscribe("/user/queue/errors", coletorPara(destino));
    }

    @SuppressWarnings("unchecked")
    private static StompFrameHandler coletorPara(BlockingQueue<Map<String, Object>> destino) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                // Map em vez das classes de evento: a interface selada nao
                // desserializa sem discriminador, e o teste so quer os campos.
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                destino.add((Map<String, Object>) payload);
            }
        };
    }

    private static Map<String, Object> receber(BlockingQueue<Map<String, Object>> fila)
            throws InterruptedException {
        var recebido = fila.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(recebido).as("nenhuma mensagem chegou em %ss", TIMEOUT_SECONDS).isNotNull();
        return recebido;
    }

    /**
     * Mapa, e nao String: o conversor JSON do cliente STOMP serializa uma String
     * como string JSON, e o servidor receberia texto onde espera um objeto.
     */
    private static Map<String, String> lance(String from, String to) {
        return Map.of("from", from, "to", to);
    }

    private static String sufixo() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
