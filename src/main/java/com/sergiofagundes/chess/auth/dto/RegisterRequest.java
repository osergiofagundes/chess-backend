package com.sergiofagundes.chess.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_]{3,30}$",
                 message = "deve ter de 3 a 30 caracteres, apenas letras, números e _")
        String username,

        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank @Size(min = 8, max = 72, message = "deve ter de 8 a 72 caracteres")
        String password) {
}
