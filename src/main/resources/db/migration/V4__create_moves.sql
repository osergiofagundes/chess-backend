CREATE TABLE moves (
    id         BIGSERIAL   PRIMARY KEY,
    game_id    UUID        NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    ply        INT         NOT NULL,
    uci        VARCHAR(5)  NOT NULL,
    san        VARCHAR(10) NOT NULL,
    fen_after  TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_moves_game_ply UNIQUE (game_id, ply),
    CONSTRAINT chk_moves_ply CHECK (ply > 0)
);
