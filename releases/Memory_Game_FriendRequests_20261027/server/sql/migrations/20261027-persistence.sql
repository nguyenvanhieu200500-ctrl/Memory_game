-- Additive migration. Run against the existing game database.
-- Java startup additionally imports friends.properties and private-messages.bin when present.
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

CREATE TABLE IF NOT EXISTS private_messages (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                sender VARCHAR(16) NOT NULL,
                recipient VARCHAR(16) NOT NULL,
                message_text VARCHAR(300) NOT NULL,
                sent_at BIGINT NOT NULL,
                INDEX idx_private_sender (sender, id),
                INDEX idx_private_recipient (recipient, id),
                FOREIGN KEY (sender) REFERENCES users(username) ON DELETE CASCADE,
                FOREIGN KEY (recipient) REFERENCES users(username) ON DELETE CASCADE
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
INSERT INTO direct_messages(sender,recipient,message_text,sent_at,legacy_key)
SELECT sender,recipient,message_text,sent_at,CONCAT('private_messages:',id)
FROM private_messages old WHERE NOT EXISTS
(SELECT 1 FROM direct_messages d WHERE d.legacy_key=CONCAT('private_messages:',old.id));
