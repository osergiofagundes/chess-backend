CREATE TABLE games (
    id              UUID        PRIMARY KEY,
    join_code       VARCHAR(8)  NOT NULL UNIQUE,
    white_player_id UUID        REFERENCES users(id),
    black_player_id UUID        REFERENCES users(id),
    status          VARCHAR(16) NOT NULL,
    result          VARCHAR(16),
    termination     VARCHAR(32),
    current_fen     TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at      TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,

    CONSTRAINT chk_games_status CHECK (
        status IN ('WAITING', 'IN_PROGRESS', 'FINISHED', 'ABORTED')),
    CONSTRAINT chk_games_result CHECK (
        result IS NULL OR result IN ('WHITE_WIN', 'BLACK_WIN', 'DRAW')),
    CONSTRAINT chk_games_termination CHECK (
        termination IS NULL OR termination IN (
            'CHECKMATE', 'STALEMATE', 'INSUFFICIENT_MATERIAL',
            'THREEFOLD_REPETITION', 'FIFTY_MOVE', 'RESIGNATION',
            'DRAW_AGREEMENT')),

    CONSTRAINT chk_games_jogadores_distintos CHECK (
        white_player_id IS NULL
        OR black_player_id IS NULL
        OR white_player_id <> black_player_id),

    CONSTRAINT chk_games_dois_jogadores CHECK (
        status = 'WAITING'
        OR (white_player_id IS NOT NULL AND black_player_id IS NOT NULL))
);

CREATE INDEX idx_games_white ON games (white_player_id, created_at DESC);
CREATE INDEX idx_games_black ON games (black_player_id, created_at DESC);
