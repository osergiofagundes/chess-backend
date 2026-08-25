package com.sergiofagundes.chess.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select u from User u where lower(u.username) = lower(:value) or lower(u.email) = lower(:value)")
    Optional<User> findByUsernameOrEmail(@Param("value") String value);
}
