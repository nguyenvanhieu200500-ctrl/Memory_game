package memorygame.server;

import memorygame.common.Protocol;
import memorygame.common.Protocol.ChatEntry;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Server-owned private messages. Access is scoped to the authenticated account. */
final class WhisperStore {
    static final String SCHEMA = """
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
            ) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
            """;
    private record Saved(String recipient, ChatEntry entry) implements Serializable {}
    private final DatabaseStore database;
    private final Path file;
    private List<Saved> saved = new ArrayList<>();

    @SuppressWarnings("unchecked")
    WhisperStore(DatabaseStore database, Path file) {
        this.database = database;
        this.file = file;
        if (database == null && Files.exists(file)) {
            try (var in = new ObjectInputStream(Files.newInputStream(file))) {
                saved = (List<Saved>) in.readObject();
            } catch (IOException | ClassNotFoundException e) { throw new IllegalStateException("Không đọc được tin nhắn", e); }
        }
    }

    void append(String recipient, ChatEntry entry) throws Exception {
        if (database != null) {
            try (Connection c = database.open(); PreparedStatement p = c.prepareStatement(
                    "INSERT INTO private_messages(sender, recipient, message_text, sent_at) VALUES (?, ?, ?, ?)")) {
                p.setString(1, entry.username()); p.setString(2, recipient);
                p.setString(3, entry.text()); p.setLong(4, entry.timestamp()); p.executeUpdate();
            }
        } else {
            List<Saved> next = new ArrayList<>(saved); next.add(new Saved(recipient, entry));
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), "whispers-", ".tmp");
            try {
                try (var out = new ObjectOutputStream(Files.newOutputStream(temporary))) { out.writeObject(next); }
                try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
                saved = next;
            } finally { Files.deleteIfExists(temporary); }
        }
    }

    Map<String, List<ChatEntry>> history(String account) throws SQLException {
        Map<String, List<ChatEntry>> result = new LinkedHashMap<>();
        if (database != null) {
            try (Connection c = database.open(); PreparedStatement p = c.prepareStatement("""
                    SELECT * FROM (
                        SELECT id, sender, recipient, message_text, sent_at,
                            ROW_NUMBER() OVER (PARTITION BY CASE WHEN sender = ? THEN recipient ELSE sender END
                                              ORDER BY id DESC) AS position
                        FROM private_messages WHERE sender = ? OR recipient = ?
                    ) recent WHERE position <= ? ORDER BY id
                    """)) {
                p.setString(1, account); p.setString(2, account); p.setString(3, account);
                p.setInt(4, Protocol.CHAT_HISTORY_LIMIT);
                try (ResultSet rows = p.executeQuery()) {
                    while (rows.next()) add(result, account, rows.getString("recipient"),
                            new ChatEntry(rows.getString("sender"), rows.getString("message_text"), rows.getLong("sent_at")));
                }
            }
        } else for (Saved message : saved) add(result, account, message.recipient(), message.entry());
        return result;
    }

    private void add(Map<String, List<ChatEntry>> result, String account, String recipient, ChatEntry entry) {
        if (!account.equalsIgnoreCase(recipient) && !account.equalsIgnoreCase(entry.username())) return;
        String peer = account.equalsIgnoreCase(entry.username()) ? recipient : entry.username();
        List<ChatEntry> list = result.computeIfAbsent(peer, ignored -> new ArrayList<>());
        list.add(entry);
        while (list.size() > Protocol.CHAT_HISTORY_LIMIT) list.remove(0);
    }
}
