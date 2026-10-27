package memorygame.test;

import java.sql.*;
import java.util.UUID;

/** Creates and removes only its own isolated test schema. */
public final class WhisperMysqlTest {
    public static void main(String[] args) throws Exception {
        String password = System.getenv("MEMORY_GAME_TEST_DB_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Set MEMORY_GAME_TEST_DB_PASSWORD for the local test MySQL root account");
        String schema = "chat_test_" + UUID.randomUUID().toString().replace("-", "");
        String base = "jdbc:mysql://127.0.0.1:3307/";
        String options = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        try (Connection c = DriverManager.getConnection(base + options, "root", password);
             Statement sql = c.createStatement()) {
            sql.executeUpdate("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            try {
                ProcessBuilder child = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                        "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"), "memorygame.test.WhisperSyncTest");
                child.environment().put("MEMORY_GAME_DB_MIGRATE","true");
                child.environment().put("MEMORY_GAME_STORAGE", "mysql");
                child.environment().put("MEMORY_GAME_DB_URL", base + schema + options);
                child.environment().put("MEMORY_GAME_DB_USER", "root");
                child.environment().put("MEMORY_GAME_DB_PASSWORD", password);
                if (child.inheritIO().start().waitFor() != 0) throw new AssertionError("MySQL integration failed");
                try (ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM " + schema + ".direct_messages")) {
                    rows.next(); if (rows.getInt(1) != 103) throw new AssertionError("SQL persistence count");
                }
            } finally { sql.executeUpdate("DROP DATABASE " + schema); }
        }
        System.out.println("WHISPER_MYSQL_OK isolatedDatabase");
    }
}
