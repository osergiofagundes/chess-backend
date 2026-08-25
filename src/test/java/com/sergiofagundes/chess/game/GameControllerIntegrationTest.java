package com.sergiofagundes.chess.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.sergiofagundes.chess.AbstractIntegrationTest;

class GameControllerIntegrationTest extends AbstractIntegrationTest {

    // --- criar ---------------------------------------------------------------

    @Test
    @DisplayName("cria partida em espera, com codigo e o criador nas brancas")
    void criaPartida() throws Exception {
        var criador = registerAndAuthorize("criador");

        mockMvc.perform(criar(criador, "WHITE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.joinCode").isNotEmpty())
                .andExpect(jsonPath("$.whitePlayer.username").value("criador"))
                .andExpect(jsonPath("$.blackPlayer").doesNotExist())
                .andExpect(jsonPath("$.yourColor").value("WHITE"))
                .andExpect(jsonPath("$.turn").value("WHITE"))
                .andExpect(jsonPath("$.moves").isEmpty());
    }

    @Test
    @DisplayName("codigo tem 6 caracteres e evita 0/O e 1/I/L")
    void codigoSemCaracteresAmbiguos() throws Exception {
        var criador = registerAndAuthorize("criador");

        assertThat(codigoDe(criador, "BLACK"))
                .hasSize(6)
                .matches("[2-9A-HJKMNP-Z]+");
    }

    @Test
    @DisplayName("cor BLACK poe o criador nas pretas e deixa as brancas vagas")
    void criaEscolhendoPretas() throws Exception {
        var criador = registerAndAuthorize("criador");

        mockMvc.perform(criar(criador, "BLACK"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.blackPlayer.username").value("criador"))
                .andExpect(jsonPath("$.whitePlayer").doesNotExist())
                .andExpect(jsonPath("$.yourColor").value("BLACK"));
    }

    // --- entrar --------------------------------------------------------------

    @Test
    @DisplayName("entrar por codigo inicia a partida e ocupa o lado vago")
    void entrarPorCodigo() throws Exception {
        var criador = registerAndAuthorize("criador");
        var convidado = registerAndAuthorize("convidado");
        var code = codigoDe(criador, "WHITE");

        mockMvc.perform(entrar(convidado, code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.whitePlayer.username").value("criador"))
                .andExpect(jsonPath("$.blackPlayer.username").value("convidado"))
                .andExpect(jsonPath("$.yourColor").value("BLACK"));
    }

    @Test
    @DisplayName("codigo em minusculas e com espacos ainda funciona")
    void entrarComCodigoDesalinhado() throws Exception {
        var criador = registerAndAuthorize("criador");
        var convidado = registerAndAuthorize("convidado");
        var code = codigoDe(criador, "WHITE");

        mockMvc.perform(entrar(convidado, "  " + code.toLowerCase() + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("recusa entrar na propria partida")
    void recusaEntrarNaPropriaPartida() throws Exception {
        var criador = registerAndAuthorize("criador");
        var code = codigoDe(criador, "WHITE");

        mockMvc.perform(entrar(criador, code))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_JOIN_OWN_GAME"));
    }

    @Test
    @DisplayName("recusa codigo inexistente")
    void recusaCodigoInexistente() throws Exception {
        var jogador = registerAndAuthorize("jogador");

        mockMvc.perform(entrar(jogador, "ZZZZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_JOIN_CODE"));
    }

    @Test
    @DisplayName("recusa um terceiro jogador em partida ja iniciada")
    void recusaTerceiroJogador() throws Exception {
        var criador = registerAndAuthorize("criador");
        var convidado = registerAndAuthorize("convidado");
        var intruso = registerAndAuthorize("intruso");
        var code = codigoDe(criador, "WHITE");

        mockMvc.perform(entrar(convidado, code)).andExpect(status().isOk());

        mockMvc.perform(entrar(intruso, code))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GAME_NOT_WAITING"));
    }

    // --- ler -----------------------------------------------------------------

    @Test
    @DisplayName("quem nao joga a partida recebe 404, e nao 403")
    void naoJogadorNaoEnxergaAPartida() throws Exception {
        var criador = registerAndAuthorize("criador");
        var estranho = registerAndAuthorize("estranho");
        var id = idDe(criador, "WHITE");

        mockMvc.perform(get("/api/v1/games/" + id).header(HttpHeaders.AUTHORIZATION, estranho))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GAME_NOT_FOUND"));
    }

    @Test
    @DisplayName("sem token a partida nem e consultada")
    void semTokenNaoLe() throws Exception {
        var criador = registerAndAuthorize("criador");
        var id = idDe(criador, "WHITE");

        mockMvc.perform(get("/api/v1/games/" + id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // --- cancelar ------------------------------------------------------------

    @Test
    @DisplayName("criador cancela a partida enquanto ela espera")
    void cancelaPartidaEmEspera() throws Exception {
        var criador = registerAndAuthorize("criador");
        var id = idDe(criador, "WHITE");

        mockMvc.perform(delete("/api/v1/games/" + id).header(HttpHeaders.AUTHORIZATION, criador))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/games/" + id).header(HttpHeaders.AUTHORIZATION, criador))
                .andExpect(jsonPath("$.status").value("ABORTED"));
    }

    @Test
    @DisplayName("nao cancela partida que ja comecou")
    void naoCancelaPartidaIniciada() throws Exception {
        var criador = registerAndAuthorize("criador");
        var convidado = registerAndAuthorize("convidado");
        var code = codigoDe(criador, "WHITE");

        var iniciada = mockMvc.perform(entrar(convidado, code))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(iniciada, "$.id");

        mockMvc.perform(delete("/api/v1/games/" + id).header(HttpHeaders.AUTHORIZATION, criador))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GAME_ALREADY_STARTED"));
    }

    // --- helpers -------------------------------------------------------------

    private static MockHttpServletRequestBuilder criar(String authorization, String color) {
        return post("/api/v1/games")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"preferredColor":"%s",
                         "timeControl":{"initialSeconds":180,"incrementSeconds":2}}
                        """.formatted(color));
    }

    private static MockHttpServletRequestBuilder entrar(String authorization, String code) {
        return post("/api/v1/games/join")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code));
    }

    private String corpoDaCriacao(String authorization, String color) throws Exception {
        return mockMvc.perform(criar(authorization, color))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String codigoDe(String authorization, String color) throws Exception {
        return JsonPath.read(corpoDaCriacao(authorization, color), "$.joinCode");
    }

    private String idDe(String authorization, String color) throws Exception {
        return JsonPath.read(corpoDaCriacao(authorization, color), "$.id");
    }
}
