ALTER TABLE bot
    ADD COLUMN author_id BIGINT DEFAULT NULL,
    ADD COLUMN internal_name VARCHAR(64) DEFAULT NULL,
    ADD COLUMN key_version INT NOT NULL DEFAULT 1,
    ADD KEY idx_bot_author_id (author_id),
    ADD KEY idx_bot_author_internal (author_id, internal_name);
