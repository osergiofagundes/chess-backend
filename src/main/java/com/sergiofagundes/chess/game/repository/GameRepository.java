package com.sergiofagundes.chess.game.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sergiofagundes.chess.game.Game;

import jakarta.persistence.LockModeType;

public interface GameRepository extends JpaRepository<Game, UUID> {

    Optional<Game> findByJoinCode(String joinCode);

    boolean existsByJoinCode(String joinCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Game g where g.id = :id")
    Optional<Game> findByIdForUpdate(@Param("id") UUID id);
}
