CREATE DATABASE IF NOT EXISTS memory_game
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE memory_game;

CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(16) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    rating INT NOT NULL DEFAULT 0,
    wins INT NOT NULL DEFAULT 0,
    losses INT NOT NULL DEFAULT 0,
    games INT NOT NULL DEFAULT 0,
    coins INT NOT NULL DEFAULT 0,
    equipped_theme VARCHAR(64) NOT NULL DEFAULT 'theme_classic',
    equipped_title VARCHAR(64) NOT NULL DEFAULT '',
    avatar MEDIUMTEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ranking (rating DESC, wins DESC),
    CONSTRAINT ck_users_balances CHECK (coins IS NOT NULL AND coins >= 0 AND rating IS NOT NULL AND rating >= 0),
    CONSTRAINT ck_users_stats CHECK (games IS NOT NULL AND games >= 0 AND wins IS NOT NULL AND wins >= 0 AND losses IS NOT NULL AND losses >= 0 AND wins <= games AND losses <= games)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS products (
    product_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(500) NOT NULL,
    price INT NOT NULL CHECK (price >= 0),
    product_type VARCHAR(16) NOT NULL,
    color VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    INDEX idx_products_sale (active, sort_order, product_id),
    CONSTRAINT ck_products_price CHECK (price >= 0),
    CONSTRAINT ck_products_active CHECK (active IN (0,1)),
    CONSTRAINT ck_products_type CHECK (product_type IN ('THEME','TITLE','LEGACY')),
    CONSTRAINT ck_products_order CHECK (sort_order >= 0)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_items (
    username VARCHAR(16) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    purchased_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (username, product_id),
    CONSTRAINT fk_items_user FOREIGN KEY (username) REFERENCES users(username)
        ON DELETE CASCADE,
    CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products(product_id)
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
    INDEX idx_history_user_time (username, played_at DESC),
    CONSTRAINT ck_history_order CHECK (history_order >= 0)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE OR REPLACE VIEW leaderboard AS
SELECT username, rating, wins, losses, games, coins,
       CASE WHEN games = 0 THEN 0
            ELSE ROUND(wins * 100.0 / games, 2) END AS win_rate
FROM users;

CREATE TABLE IF NOT EXISTS matches (
    match_id VARCHAR(64) PRIMARY KEY,
    room_code VARCHAR(32),
    board_size INT,
    card_theme VARCHAR(32),
    player_count INT,
    started_at BIGINT,
    ended_at BIGINT,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    abnormal BOOLEAN,
    INDEX idx_matches_end (ended_at),
    INDEX idx_matches_status (status, started_at),
    CONSTRAINT ck_matches_status CHECK (status IN ('PLAYING','COMPLETED','INTERRUPTED','LEGACY')),
    CONSTRAINT ck_matches_metadata CHECK (status='LEGACY' OR (board_size IS NOT NULL AND board_size IN (4,6,8) AND card_theme IS NOT NULL AND card_theme IN ('FRUIT','ANIMALS','FOOD','VEHICLES','UNKNOWN') AND player_count IS NOT NULL AND player_count BETWEEN 2 AND 4 AND started_at IS NOT NULL AND started_at >= 0)),
    CONSTRAINT ck_matches_time CHECK (ended_at IS NULL OR (ended_at >= 0 AND (started_at IS NULL OR ended_at >= started_at))),
    CONSTRAINT ck_matches_end CHECK (status='LEGACY' OR (status='PLAYING' AND ended_at IS NULL) OR (status IN ('COMPLETED','INTERRUPTED') AND ended_at IS NOT NULL AND reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason)) > 0 AND abnormal IS NOT NULL)),
    CONSTRAINT ck_matches_abnormal CHECK (abnormal IS NULL OR abnormal IN (0,1))
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS match_results (
    match_id VARCHAR(64) NOT NULL,
    username VARCHAR(16) NOT NULL,
    score INT,
    placement INT,
    forfeited BOOLEAN,
    winner BOOLEAN,
    win_credit BOOLEAN,
    coin_reward INT,
    rating_delta INT,
    rating_applied INT,
    result_text VARCHAR(500),
    recorded_at BIGINT,
    legacy_history_id BIGINT UNIQUE,
    legacy_order INT,
    PRIMARY KEY (match_id, username),
    CONSTRAINT fk_result_match FOREIGN KEY (match_id) REFERENCES matches(match_id),
    CONSTRAINT fk_result_user FOREIGN KEY (username) REFERENCES users(username),
    INDEX idx_results_user (username, match_id),
    INDEX idx_results_user_time (username, recorded_at DESC, match_id),
    CONSTRAINT ck_results_score CHECK (score IS NULL OR score >= 0),
    CONSTRAINT ck_results_placement CHECK (placement IS NULL OR placement BETWEEN 1 AND 4),
    CONSTRAINT ck_results_coins CHECK (coin_reward IS NULL OR coin_reward >= 0),
    CONSTRAINT ck_results_flags CHECK ((forfeited IS NULL OR forfeited IN (0,1)) AND (winner IS NULL OR winner IN (0,1)) AND (win_credit IS NULL OR win_credit IN (0,1))),
    CONSTRAINT ck_results_time CHECK (recorded_at IS NULL OR recorded_at >= 0),
    CONSTRAINT ck_results_legacy_order CHECK (legacy_order IS NULL OR legacy_order >= 0)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO products(product_id,name,description,price,product_type,color,sort_order) VALUES
('theme_classic', 'Mặt thẻ Cổ điển', 'Bề mặt 3D tối giản với họa tiết dập chìm', 0, 'THEME', '#6D28D9', 0),
('theme_ocean', 'Mặt thẻ Đại dương', 'Sóng nước chuyển động và ánh sáng lướt trên mặt thẻ', 100, 'THEME', '#0E7490', 1),
('theme_violet', 'Mặt thẻ Tím neon', 'Viền neon phát sáng và hạt sao chuyển động', 180, 'THEME', '#6D28D9', 2),
('theme_gold', 'Mặt thẻ Hoàng kim', 'Ánh kim quét chéo trên bề mặt thẻ 3D', 300, 'THEME', '#A16207', 3),
('title_memory', 'Danh hiệu Bậc thầy trí nhớ', 'Hào quang trí nhớ xanh chuyển động, hiển thị cạnh tên trong Lobby và phòng', 220, 'TITLE', '#38BDF8', 4),
('title_champion', 'Danh hiệu Nhà vô địch', 'Vương miện vàng với tia sáng và hạt kim tuyến cao cấp', 400, 'TITLE', '#FBBF24', 5);

CREATE TABLE IF NOT EXISTS match_snapshots (
            match_id VARCHAR(64) PRIMARY KEY,
            format_version INT NOT NULL,
            revision BIGINT NOT NULL DEFAULT 1,
            payload MEDIUMBLOB NOT NULL,
            checksum CHAR(64) NOT NULL,
            saved_at BIGINT NOT NULL,
            CONSTRAINT fk_snapshot_match FOREIGN KEY (match_id) REFERENCES matches(match_id) ON DELETE CASCADE,
            CONSTRAINT ck_snapshot_version CHECK (format_version > 0 AND revision > 0),
            CONSTRAINT ck_snapshot_time CHECK (saved_at >= 0)
        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friendships (
            sender VARCHAR(16) NOT NULL,
            recipient VARCHAR(16) NOT NULL,
            pair_low VARCHAR(16) GENERATED ALWAYS AS (LEAST(sender,recipient)) STORED,
            pair_high VARCHAR(16) GENERATED ALWAYS AS (GREATEST(sender,recipient)) STORED,
            request_id VARCHAR(64) NOT NULL UNIQUE,
            status VARCHAR(16) NOT NULL,
            created_at BIGINT NULL,
            updated_at BIGINT NOT NULL,
            PRIMARY KEY (sender,recipient),
            UNIQUE KEY uk_friend_pair (pair_low,pair_high),
            FOREIGN KEY (sender) REFERENCES users(username),
            FOREIGN KEY (recipient) REFERENCES users(username),
            CHECK (sender <> recipient),
            CHECK (status IN ('PENDING','ACCEPTED','DECLINED','EXPIRED')),
            CHECK (created_at IS NULL OR created_at >= 0),
            INDEX idx_friend_recipient (recipient,status),
            INDEX idx_friend_expiry (status,updated_at),
            INDEX idx_friend_sender (sender,status)
        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS direct_messages (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            sender VARCHAR(16) NOT NULL,
            recipient VARCHAR(16) NOT NULL,
            message_text VARCHAR(300) NOT NULL,
            sent_at BIGINT NOT NULL,
            read_at BIGINT NULL,
            legacy_key VARCHAR(160) UNIQUE,
            FOREIGN KEY (sender) REFERENCES users(username),
            FOREIGN KEY (recipient) REFERENCES users(username),
            CHECK (sender <> recipient),
            CHECK (CHAR_LENGTH(message_text) BETWEEN 1 AND 300),
            CHECK (sent_at >= 0 AND (read_at IS NULL OR read_at >= sent_at)),
            INDEX idx_dm_conversation (sender,recipient,id),
            INDEX idx_dm_received (recipient,sender,id)
        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS coin_transactions (
            operation_id VARCHAR(160) PRIMARY KEY,
            username VARCHAR(16) NOT NULL,
            amount INT NOT NULL,
            reason VARCHAR(32) NOT NULL,
            match_id VARCHAR(64) NULL,
            created_at BIGINT NOT NULL,
            FOREIGN KEY (username) REFERENCES users(username),
            FOREIGN KEY (match_id) REFERENCES matches(match_id),
            CHECK (created_at >= 0),
            CHECK (reason IN ('PURCHASE','MATCH_REWARD')),
            INDEX idx_coin_user_time (username,created_at,operation_id)
        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE INDEX idx_results_history ON match_results(username, recorded_at DESC, legacy_order ASC, match_id DESC);
