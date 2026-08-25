package com.sergiofagundes.chess.game.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sergiofagundes.chess.game.Move;

public interface MoveRepository extends JpaRepository<Move, Long> {

    List<Move> findByGameIdOrderByPlyAsc(UUID gameId);

    @Query("select m.uci from Move m where m.game.id = :gameId order by m.ply asc")
    List<String> findUciHistory(@Param("gameId") UUID gameId);

    @Query("select coalesce(max(m.ply), 0) from Move m where m.game.id = :gameId")
    int findLastPly(@Param("gameId") UUID gameId);
}
