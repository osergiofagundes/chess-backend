package com.sergiofagundes.chess.auth.security;

import java.security.Principal;
import java.util.UUID;

public record AuthenticatedUser(UUID id, String username) implements Principal {

    @Override
    public String getName() {
        return id.toString();
    }
}
