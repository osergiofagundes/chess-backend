package com.sergiofagundes.chess.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sergiofagundes.chess.auth.dto.AuthResponse;
import com.sergiofagundes.chess.auth.dto.LoginRequest;
import com.sergiofagundes.chess.auth.dto.RegisterRequest;
import com.sergiofagundes.chess.auth.dto.UserResponse;
import com.sergiofagundes.chess.common.exception.BusinessException;
import com.sergiofagundes.chess.user.User;
import com.sergiofagundes.chess.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    private final String dummyHash;

    AuthService(UserRepository userRepository,
                PasswordEncoder passwordEncoder,
                JwtService jwtService,
                RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.dummyHash = passwordEncoder.encode("hash-descartavel-para-tempo-constante");
    }

    public record AuthResult(AuthResponse body, RefreshTokenService.IssuedToken refreshToken) {}

    @Transactional
    public AuthResult register(RegisterRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new BusinessException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Este username já está em uso");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Este e-mail já está em uso");
        }

        var user = userRepository.save(new User(
                request.username(),
                request.email().toLowerCase(),
                passwordEncoder.encode(request.password())));

        return issueFor(user);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        var user = userRepository.findByUsernameOrEmail(request.usernameOrEmail()).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return issueFor(user);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        return issueFor(refreshTokenService.consume(rawRefreshToken));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private AuthResult issueFor(User user) {
        var body = new AuthResponse(
                jwtService.generateAccessToken(user),
                jwtService.accessTokenTtlSeconds(),
                UserResponse.from(user));
        return new AuthResult(body, refreshTokenService.issue(user));
    }

    private static BusinessException invalidCredentials() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Credenciais inválidas");
    }
}
