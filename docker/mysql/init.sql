CREATE DATABASE IF NOT EXISTS memory_game
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE memory_game;

CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(16) PRIMARY KEY,
    password_hash CHAR(64) NOT NULL,
    rating INT NOT NULL DEFAULT 0,
    wins INT NOT NULL DEFAULT 0,
    losses INT NOT NULL DEFAULT 0,
    games INT NOT NULL DEFAULT 0,
    coins INT NOT NULL DEFAULT 0,
    equipped_theme VARCHAR(64) NOT NULL DEFAULT 'theme_classic',
    equipped_title VARCHAR(64) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ranking (rating DESC, wins DESC)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_items (
    username VARCHAR(16) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    purchased_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (username, product_id),
    CONSTRAINT fk_items_user FOREIGN KEY (username) REFERENCES users(username)
        ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS match_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    history_order INT NOT NULL,
    result_text VARCHAR(500) NOT NULL,
    played_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_history_position (username, history_order),
    CONSTRAINT fk_history_user FOREIGN KEY (username) REFERENCES users(username)
        ON DELETE CASCADE,
    INDEX idx_history_user_time (username, played_at DESC)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE OR REPLACE VIEW leaderboard AS
SELECT username, rating, wins, losses, games, coins,
       CASE WHEN games = 0 THEN 0
            ELSE ROUND(wins * 100.0 / games, 2) END AS win_rate
FROM users;
