package com.sergiofagundes.chess.game.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import com.sergiofagundes.chess.game.repository.GameRepository;

@Component
public class JoinCodeGenerator {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();

    private static final int LENGTH = 6;
    private static final int MAX_ATTEMPTS = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GameRepository gameRepository;

    JoinCodeGenerator(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    public String generate() {
        for (var attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            var code = randomCode();
            if (!gameRepository.existsByJoinCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Nao foi possivel gerar um codigo livre");
    }

    private static String randomCode() {
        var code = new StringBuilder(LENGTH);
        for (var index = 0; index < LENGTH; index++) {
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}
