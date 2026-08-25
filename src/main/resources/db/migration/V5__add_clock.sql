ALTER TABLE games
    ADD COLUMN initial_time_seconds INT NOT NULL DEFAULT 300,
    ADD COLUMN increment_seconds    INT NOT NULL DEFAULT 0,
    ADD COLUMN white_time_left_ms   BIGINT,
    ADD COLUMN black_time_left_ms   BIGINT,
    ADD COLUMN last_move_at         TIMESTAMPTZ;

UPDATE games
   SET white_time_left_ms = initial_time_seconds * 1000,
       black_time_left_ms = initial_time_seconds * 1000;

ALTER TABLE games
    ALTER COLUMN initial_time_seconds DROP DEFAULT,
    ALTER COLUMN increment_seconds    DROP DEFAULT,
    ALTER COLUMN white_time_left_ms   SET NOT NULL,
    ALTER COLUMN black_time_left_ms   SET NOT NULL;

ALTER TABLE games
    ADD CONSTRAINT chk_games_tempo_valido CHECK (
        initial_time_seconds > 0 AND increment_seconds >= 0);

ALTER TABLE games DROP CONSTRAINT chk_games_termination;
ALTER TABLE games ADD CONSTRAINT chk_games_termination CHECK (
    termination IS NULL OR termination IN (
        'CHECKMATE', 'STALEMATE', 'INSUFFICIENT_MATERIAL',
        'THREEFOLD_REPETITION', 'FIFTY_MOVE', 'RESIGNATION',
        'DRAW_AGREEMENT', 'TIMEOUT'));

ALTER TABLE moves ADD COLUMN time_spent_ms INT;
