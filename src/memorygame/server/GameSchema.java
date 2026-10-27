package memorygame.server;

import java.sql.*;

/** Additive, restart-safe upgrade; legacy history is never deleted or rewarded again. */
final class GameSchema {
    static final String PRODUCTS = """
        CREATE TABLE IF NOT EXISTS products (
            product_id VARCHAR(64) PRIMARY KEY,
            name VARCHAR(160) NOT NULL,
            description VARCHAR(500) NOT NULL,
            price INT NOT NULL CHECK (price >= 0),
            product_type VARCHAR(16) NOT NULL,
            color VARCHAR(32) NOT NULL,
            active BOOLEAN NOT NULL DEFAULT TRUE,
            sort_order INT NOT NULL DEFAULT 0
        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        """;
    static final String MATCHES = """
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
            INDEX idx_matches_end (ended_at)
        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        """;
    static final String RESULTS = """
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
            INDEX idx_results_user (username, match_id)
        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        """;
    static final String SNAPSHOTS = """
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
        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        """;
    static final String FRIENDSHIPS = """
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
        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        """;
    static final String DIRECT_MESSAGES = """
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
        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        """;
    static final String COIN_TRANSACTIONS = """
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
        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
        """;
    static void upgrade(Connection c) throws SQLException {
        try(ResultSet columns=c.getMetaData().getColumns(c.getCatalog(),null,"users","password_hash")) {
            if(columns.next() && columns.getInt("COLUMN_SIZE")<255)try(Statement s=c.createStatement()) {
                s.executeUpdate("ALTER TABLE users MODIFY password_hash VARCHAR(255) NOT NULL");
            }
        }
        try (Statement s = c.createStatement()) {
            s.executeUpdate(PRODUCTS); s.executeUpdate(MATCHES); s.executeUpdate(RESULTS); s.executeUpdate(SNAPSHOTS);
            s.executeUpdate(FRIENDSHIPS); s.executeUpdate(DIRECT_MESSAGES); s.executeUpdate(COIN_TRANSACTIONS);
        }
        try (PreparedStatement p = c.prepareStatement("INSERT IGNORE INTO products(product_id,name,description,price,product_type,color,sort_order) VALUES (?,?,?,?,?,?,?)")) {
            int order = 0;
            for (var product : GameServer.SHOP_PRODUCTS) {
                p.setString(1, product.id()); p.setString(2, product.name()); p.setString(3, product.description());
                p.setInt(4, product.price()); p.setString(5, product.type()); p.setString(6, product.color()); p.setInt(7, order++);
                p.executeUpdate();
            }
        }
        // Preserve unknown old item IDs as archived catalog entries before adding the FK.
        try (Statement s = c.createStatement()) {
            s.executeUpdate("""
                INSERT IGNORE INTO products(product_id,name,description,price,product_type,color,active)
                SELECT DISTINCT product_id, product_id, 'Vật phẩm từ dữ liệu cũ', 0, 'LEGACY', '#808080', FALSE FROM user_items
                """);
        }
        boolean linked = false;
        try (ResultSet keys = c.getMetaData().getImportedKeys(c.getCatalog(), null, "user_items")) {
            while (keys.next()) if ("products".equalsIgnoreCase(keys.getString("PKTABLE_NAME"))) linked = true;
        }
        if (!linked) try (Statement s = c.createStatement()) {
            s.executeUpdate("ALTER TABLE user_items ADD CONSTRAINT fk_items_product FOREIGN KEY (product_id) REFERENCES products(product_id)");
        }
        ensureColumn(c, "match_results", "recorded_at", "BIGINT");
        try (Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE matches SET card_theme='UNKNOWN' WHERE status<>'LEGACY' AND (card_theme IS NULL OR card_theme='')");
            s.executeUpdate("UPDATE match_results r JOIN matches m ON m.match_id=r.match_id SET r.recorded_at=COALESCE(m.ended_at,m.started_at) WHERE r.recorded_at IS NULL");
        }
        ensureIndex(c, "match_results", "idx_results_history", "username, recorded_at DESC, legacy_order ASC, match_id DESC");
        ensureIndex(c, "match_results", "idx_results_user_time", "username, recorded_at DESC, match_id");
        ensureIndex(c, "match_history", "idx_history_user_time", "username, played_at DESC");
        ensureIndex(c, "matches", "idx_matches_status", "status, started_at");
        ensureIndex(c, "products", "idx_products_sale", "active, sort_order, product_id");
        ensureForeignKey(c, "user_items", "username", "users", "fk_items_user", " ON DELETE CASCADE");
        ensureForeignKey(c, "match_history", "username", "users", "fk_history_user", " ON DELETE CASCADE");
        for (String[] check : CHECKS) ensureCheck(c, check[0], check[1], check[2]);
        importHistory(c);
    }
    static final String[][] CHECKS = {
        {"users", "ck_users_balances", "coins IS NOT NULL AND coins >= 0 AND rating IS NOT NULL AND rating >= 0"},
        {"users", "ck_users_stats", "games IS NOT NULL AND games >= 0 AND wins IS NOT NULL AND wins >= 0 AND losses IS NOT NULL AND losses >= 0 AND wins <= games AND losses <= games"},
        {"products", "ck_products_price", "price >= 0"},
        {"products", "ck_products_active", "active IN (0,1)"},
        {"products", "ck_products_type", "product_type IN ('THEME','TITLE','LEGACY')"},
        {"products", "ck_products_order", "sort_order >= 0"},
        {"matches", "ck_matches_status", "status IN ('PLAYING','COMPLETED','INTERRUPTED','LEGACY')"},
        {"matches", "ck_matches_metadata", "status='LEGACY' OR (board_size IS NOT NULL AND board_size IN (4,6,8) AND card_theme IS NOT NULL AND card_theme IN ('FRUIT','ANIMALS','FOOD','VEHICLES','UNKNOWN') AND player_count IS NOT NULL AND player_count BETWEEN 2 AND 4 AND started_at IS NOT NULL AND started_at >= 0)"},
        {"matches", "ck_matches_time", "ended_at IS NULL OR (ended_at >= 0 AND (started_at IS NULL OR ended_at >= started_at))"},
        {"matches", "ck_matches_end", "status='LEGACY' OR (status='PLAYING' AND ended_at IS NULL) OR (status IN ('COMPLETED','INTERRUPTED') AND ended_at IS NOT NULL AND reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason)) > 0 AND abnormal IS NOT NULL)"},
        {"matches", "ck_matches_abnormal", "abnormal IS NULL OR abnormal IN (0,1)"},
        {"match_results", "ck_results_score", "score IS NULL OR score >= 0"},
        {"match_results", "ck_results_placement", "placement IS NULL OR placement BETWEEN 1 AND 4"},
        {"match_results", "ck_results_coins", "coin_reward IS NULL OR coin_reward >= 0"},
        {"match_results", "ck_results_flags", "(forfeited IS NULL OR forfeited IN (0,1)) AND (winner IS NULL OR winner IN (0,1)) AND (win_credit IS NULL OR win_credit IN (0,1))"},
        {"match_results", "ck_results_time", "recorded_at IS NULL OR recorded_at >= 0"},
        {"match_results", "ck_results_legacy_order", "legacy_order IS NULL OR legacy_order >= 0"},
        {"match_history", "ck_history_order", "history_order >= 0"}
    };

    private static void ensureColumn(Connection c, String table, String column, String type) throws SQLException {
        try (ResultSet r=c.getMetaData().getColumns(c.getCatalog(), null, table, column)) {
            if (r.next()) return;
        }
        try (Statement s=c.createStatement()) { s.executeUpdate("ALTER TABLE "+table+" ADD COLUMN "+column+" "+type); }
    }
    private static void ensureIndex(Connection c, String table, String name, String columns) throws SQLException {
        try (ResultSet r=c.getMetaData().getIndexInfo(c.getCatalog(),null,table,false,false)) {
            while(r.next()) if(name.equalsIgnoreCase(r.getString("INDEX_NAME"))) return;
        }
        try(Statement s=c.createStatement()) { s.executeUpdate("CREATE INDEX "+name+" ON "+table+" ("+columns+")"); }
    }
    private static void ensureForeignKey(Connection c, String table, String column, String target, String name, String action) throws SQLException {
        try(ResultSet r=c.getMetaData().getImportedKeys(c.getCatalog(),null,table)) {
            while(r.next()) if(target.equalsIgnoreCase(r.getString("PKTABLE_NAME")) && column.equalsIgnoreCase(r.getString("FKCOLUMN_NAME"))) return;
        }
        try(Statement s=c.createStatement()) { s.executeUpdate("ALTER TABLE "+table+" ADD CONSTRAINT "+name+" FOREIGN KEY ("+column+") REFERENCES "+target+"("+column+")"+action); }
    }
    private static void ensureCheck(Connection c, String table, String name, String expression) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME=? AND CONSTRAINT_TYPE='CHECK'")) {
            p.setString(1,table);p.setString(2,name);
            try(ResultSet r=p.executeQuery()) { if(r.next()) return; }
        }
        // Invalid old data causes an explicit migration failure; never silently clamp or delete it.
        try(Statement s=c.createStatement()) { s.executeUpdate("ALTER TABLE "+table+" ADD CONSTRAINT "+name+" CHECK ("+expression+")"); }
    }

    static void importHistory(Connection c) throws SQLException {
        c.setAutoCommit(false);
        try {
            copyHistory(c);
            c.commit();
        } catch (SQLException e) { c.rollback(); throw e; }
        finally { c.setAutoCommit(true); }
    }

    /** Also used inside the account-import transaction for old state.bin files. */
    static void copyHistory(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("""
                INSERT IGNORE INTO matches(match_id, ended_at, status, reason)
                SELECT CONCAT('legacy-', id), UNIX_TIMESTAMP(played_at) * 1000, 'LEGACY', 'Lịch sử trước nâng cấp'
                FROM match_history
                """);
            s.executeUpdate("""
                INSERT IGNORE INTO match_results(match_id, username, result_text, legacy_history_id, legacy_order, recorded_at)
                SELECT CONCAT('legacy-', id), username, result_text, id, history_order, UNIX_TIMESTAMP(played_at)*1000 FROM match_history
                """);
        }
    }
}
