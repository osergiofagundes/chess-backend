package com.sergiofagundes.chess.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.sergiofagundes.chess.AbstractIntegrationTest;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String REFRESH_COOKIE = "refresh_token";

    // --- registro ------------------------------------------------------------

    @Test
    @DisplayName("registra usuario e devolve access token + cookie httpOnly de refresh")
    void registraUsuario() throws Exception {
        mockMvc.perform(register("magnus", "magnus@example.com", "senha-forte-123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.user.username").value("magnus"))
                // o refresh token nunca pode aparecer no corpo
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().exists(REFRESH_COOKIE))
                .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
                .andExpect(cookie().path(REFRESH_COOKIE, "/api/v1/auth"));
    }

    @Test
    @DisplayName("recusa username ja usado, ignorando maiusculas")
    void recusaUsernameDuplicado() throws Exception {
        mockMvc.perform(register("magnus", "a@example.com", "senha-forte-123"))
                .andExpect(status().isCreated());

        mockMvc.perform(register("MAGNUS", "outro@example.com", "senha-forte-123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    @DisplayName("recusa e-mail ja usado, ignorando maiusculas")
    void recusaEmailDuplicado() throws Exception {
        mockMvc.perform(register("magnus", "duplicado@example.com", "senha-forte-123"))
                .andExpect(status().isCreated());

        mockMvc.perform(register("hikaru", "DUPLICADO@example.com", "senha-forte-123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    @DisplayName("recusa senha curta e username invalido com erro por campo")
    void recusaDadosInvalidos() throws Exception {
        mockMvc.perform(register("ab", "nao-e-email", "123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    // --- login ---------------------------------------------------------------

    @Test
    @DisplayName("aceita login por username e por e-mail")
    void loginPorUsernameOuEmail() throws Exception {
        mockMvc.perform(register("magnus", "magnus@example.com", "senha-forte-123"))
                .andExpect(status().isCreated());

        mockMvc.perform(login("magnus", "senha-forte-123")).andExpect(status().isOk());
        mockMvc.perform(login("MAGNUS", "senha-forte-123")).andExpect(status().isOk());
        mockMvc.perform(login("magnus@example.com", "senha-forte-123")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("senha errada e usuario inexistente devolvem exatamente o mesmo erro")
    void naoRevelaSeUsuarioExiste() throws Exception {
        mockMvc.perform(register("magnus", "magnus@example.com", "senha-forte-123"))
                .andExpect(status().isCreated());

        mockMvc.perform(login("magnus", "senha-errada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(login("nao-existe", "senha-errada"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    // --- refresh -------------------------------------------------------------

    @Test
    @DisplayName("refresh emite um cookie novo e invalida o anterior (rotacao)")
    void refreshRotacionaOToken() throws Exception {
        var registro = mockMvc.perform(register("magnus", "magnus@example.com", "senha-forte-123"))
                .andExpect(status().isCreated())
                .andReturn();

        var primeiro = refreshCookie(registro);

        var renovado = mockMvc.perform(post("/api/v1/auth/refresh").cookie(primeiro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        var segundo = refreshCookie(renovado);
        assertThat(segundo.getValue()).isNotEqualTo(primeiro.getValue());

        // Este e o ponto da rotacao: o token antigo morre ao ser usado.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(primeiro))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(segundo))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("refresh sem cookie devolve 401")
    void refreshSemCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MISSING_REFRESH_TOKEN"));
    }

    // --- logout --------------------------------------------------------------

    @Test
    @DisplayName("logout revoga o refresh token e limpa o cookie")
    void logoutRevogaToken() throws Exception {
        var registro = mockMvc.perform(register("magnus", "magnus@example.com", "senha-forte-123"))
                .andExpect(status().isCreated())
                .andReturn();

        var token = refreshCookie(registro);

        mockMvc.perform(post("/api/v1/auth/logout").cookie(token))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    // --- rotas protegidas ----------------------------------------------------

    @Test
    @DisplayName("rota protegida sem token devolve 401 no formato ApiError")
    void rotaProtegidaSemToken() throws Exception {
        mockMvc.perform(get("/api/v1/qualquer-rota-protegida"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // --- helpers -------------------------------------------------------------

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register(
            String username, String email, String password) {
        return post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","email":"%s","password":"%s"}
                        """.formatted(username, email, password));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String usernameOrEmail, String password) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"usernameOrEmail":"%s","password":"%s"}
                        """.formatted(usernameOrEmail, password));
    }

    private static Cookie refreshCookie(MvcResult result) {
        var cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(cookie).as("cookie de refresh na resposta").isNotNull();
        return cookie;
    }
}
