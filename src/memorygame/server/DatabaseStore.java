package memorygame.server;

import java.sql.*;
import java.util.*;

final class DatabaseStore {
    private static final String DEFAULT_URL = "jdbc:mysql://127.0.0.1:3307/memory_game"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
            + "&useUnicode=true&characterEncoding=UTF-8";

    private final String url;
    private final String user;
    private final String password;

    DatabaseStore() {
        url = environment("MEMORY_GAME_DB_URL", DEFAULT_URL);
        user = environment("MEMORY_GAME_DB_USER", "memory_game");
        password = environment("MEMORY_GAME_DB_PASSWORD", "memory_game_123");
        initializeWithRetry();
    }

    Map<String, GameServer.UserRecord> loadUsers() {
        Map<String, GameServer.UserRecord> users = new HashMap<>();
        try (Connection connection = open();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT username, password_hash, rating, wins, games, coins,
                            equipped_theme, equipped_title
                     FROM users
                     """)) {
            while (rows.next()) {
                GameServer.UserRecord record = new GameServer.UserRecord(
                        rows.getString("username"), rows.getString("password_hash"));
                record.rating = rows.getInt("rating");
                record.wins = rows.getInt("wins");
                record.games = rows.getInt("games");
                record.coins = rows.getInt("coins");
                record.equippedTheme = rows.getString("equipped_theme");
                record.equippedTitle = rows.getString("equipped_title");
                users.put(key(record.username), record);
            }

            try (ResultSet items = statement.executeQuery(
                    "SELECT username, product_id FROM user_items ORDER BY username, product_id")) {
                while (items.next()) {
                    GameServer.UserRecord record = users.get(key(items.getString("username")));
                    if (record != null) record.ownedItems.add(items.getString("product_id"));
                }
            }
            try (ResultSet history = statement.executeQuery("""
                    SELECT username, result_text FROM match_history
                    ORDER BY username, history_order
                    """)) {
                while (history.next()) {
                    GameServer.UserRecord record = users.get(key(history.getString("username")));
                    if (record != null) record.history.add(history.getString("result_text"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể đọc dữ liệu MySQL: " + e.getMessage(), e);
        }
        return users;
    }

    void saveUsers(Map<String, GameServer.UserRecord> users) {
        String upsertUser = """
                INSERT INTO users
                    (username, password_hash, rating, wins, losses, games, coins,
                     equipped_theme, equipped_title)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    password_hash = VALUES(password_hash), rating = VALUES(rating),
                    wins = VALUES(wins), losses = VALUES(losses), games = VALUES(games),
                    coins = VALUES(coins), equipped_theme = VALUES(equipped_theme),
                    equipped_title = VALUES(equipped_title)
                """;
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement upsert = connection.prepareStatement(upsertUser);
                 PreparedStatement deleteItems = connection.prepareStatement(
                         "DELETE FROM user_items WHERE username = ?");
                 PreparedStatement insertItem = connection.prepareStatement(
                         "INSERT INTO user_items(username, product_id) VALUES (?, ?)");
                 PreparedStatement deleteHistory = connection.prepareStatement(
                         "DELETE FROM match_history WHERE username = ?");
                 PreparedStatement insertHistory = connection.prepareStatement("""
                         INSERT INTO match_history(username, history_order, result_text)
                         VALUES (?, ?, ?)
                         """)) {
                for (GameServer.UserRecord record : users.values()) {
                    upsert.setString(1, record.username);
                    upsert.setString(2, record.passwordHash);
                    upsert.setInt(3, record.rating);
                    upsert.setInt(4, record.wins);
                    upsert.setInt(5, Math.max(0, record.games - record.wins));
                    upsert.setInt(6, record.games);
                    upsert.setInt(7, record.coins);
                    upsert.setString(8, record.equippedTheme);
                    upsert.setString(9, record.equippedTitle);
                    upsert.executeUpdate();

                    deleteItems.setString(1, record.username);
                    deleteItems.executeUpdate();
                    insertItem.clearBatch();
                    for (String product : record.ownedItems) {
                        insertItem.setString(1, record.username);
                        insertItem.setString(2, product);
                        insertItem.addBatch();
                    }
                    insertItem.executeBatch();

                    deleteHistory.setString(1, record.username);
                    deleteHistory.executeUpdate();
                    insertHistory.clearBatch();
                    for (int index = 0; index < record.history.size(); index++) {
                        insertHistory.setString(1, record.username);
                        insertHistory.setInt(2, index);
                        insertHistory.setString(3, record.history.get(index));
                        insertHistory.addBatch();
                    }
                    insertHistory.executeBatch();
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Không thể lưu dữ liệu MySQL: " + e.getMessage(), e);
        }
    }

    private void initializeWithRetry() {
        Exception last = null;
        for (int attempt = 1; attempt <= 45; attempt++) {
            try (Connection connection = open(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
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
                        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                        """);
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS user_items (
                            username VARCHAR(16) NOT NULL,
                            product_id VARCHAR(64) NOT NULL,
                            purchased_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (username, product_id),
                            CONSTRAINT fk_items_user FOREIGN KEY (username) REFERENCES users(username)
                                ON DELETE CASCADE
                        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                        """);
                statement.executeUpdate("""
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
                        ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
                        """);
                statement.executeUpdate("""
                        CREATE OR REPLACE VIEW leaderboard AS
                        SELECT username, rating, wins, losses, games, coins,
                               CASE WHEN games = 0 THEN 0
                                    ELSE ROUND(wins * 100.0 / games, 2) END AS win_rate
                        FROM users
                        """);
                return;
            } catch (Exception e) {
                last = e;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        throw new IllegalStateException("Không thể kết nối MySQL sau 45 giây: "
                + (last == null ? "không rõ nguyên nhân" : last.getMessage()), last);
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
