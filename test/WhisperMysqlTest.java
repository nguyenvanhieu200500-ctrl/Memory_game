package memorygame.test;

import java.sql.*;
import java.util.UUID;

/** Creates and removes only its own isolated test schema. */
public final class WhisperMysqlTest {
    public static void main(String[] args) throws Exception {
        String schema = "chat_test_" + UUID.randomUUID().toString().replace("-", "");
        String base = "jdbc:mysql://127.0.0.1:3307/";
        String options = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection c = DriverManager.getConnection(base + options, "root", "memory_game_root_123");
             Statement sql = c.createStatement()) {
            sql.executeUpdate("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            try {
                ProcessBuilder child = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                        "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"), "memorygame.test.WhisperSyncTest");
                child.environment().put("MEMORY_GAME_STORAGE", "mysql");
                child.environment().put("MEMORY_GAME_DB_URL", base + schema + options);
                child.environment().put("MEMORY_GAME_DB_USER", "root");
                child.environment().put("MEMORY_GAME_DB_PASSWORD", "memory_game_root_123");
                if (child.inheritIO().start().waitFor() != 0) throw new AssertionError("MySQL integration failed");
                try (ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM " + schema + ".direct_messages")) {
                    rows.next(); if (rows.getInt(1) != 103) throw new AssertionError("SQL persistence count");
                }
            } finally { sql.executeUpdate("DROP DATABASE " + schema); }
        }
        System.out.println("WHISPER_MYSQL_OK isolatedDatabase");
    }
}
