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
        if (Files.exists(file)) {
            try (var in = new ObjectInputStream(Files.newInputStream(file))) {
                saved = (List<Saved>) in.readObject();
            } catch (IOException | ClassNotFoundException e) { throw new IllegalStateException("Không đọc được tin nhắn", e); }
        }
        if(database!=null && !saved.isEmpty()) importLegacy();
    }

    private void importLegacy() {
        try(Connection c=database.open()) {
            c.setAutoCommit(false);
            try(PreparedStatement p=c.prepareStatement("""
                INSERT INTO direct_messages(sender,recipient,message_text,sent_at,legacy_key)
                VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE legacy_key=VALUES(legacy_key)
                """)) {
                Map<String,Integer> occurrences=new HashMap<>();
                for(Saved message:saved) {
                    ChatEntry e=message.entry();
                    String fingerprint=DatabaseStore.checksum((e.username()+"\0"+message.recipient()+"\0"+e.timestamp()+"\0"+e.text()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    String identity="file:"+fingerprint+":"+occurrences.merge(fingerprint,1,Integer::sum);
                    p.setString(1,e.username());p.setString(2,message.recipient());p.setString(3,e.text());p.setLong(4,e.timestamp());p.setString(5,identity);p.executeUpdate();
                }
                c.commit();
            }catch(SQLException e){c.rollback();throw e;}
        }catch(SQLException e){throw new IllegalStateException("Không chuyển được tin nhắn cũ; giữ nguyên nguồn",e);}
    }

    record Page(List<ChatEntry> messages,long before,long newest,boolean hasMore) {}
    Page page(String account,String peer,long before) throws SQLException {
        if(before<=0)before=Long.MAX_VALUE;
        List<ChatEntry> entries=new ArrayList<>();long oldest=before,newest=0;boolean more=false;
        if(database!=null) {
            try(Connection c=database.open();PreparedStatement p=c.prepareStatement("""
                (SELECT id,sender,message_text,sent_at FROM direct_messages FORCE INDEX (idx_dm_conversation)
                 WHERE sender=? AND recipient=? AND id<? ORDER BY id DESC LIMIT 51)
                UNION ALL
                (SELECT id,sender,message_text,sent_at FROM direct_messages FORCE INDEX (idx_dm_conversation)
                 WHERE sender=? AND recipient=? AND id<? ORDER BY id DESC LIMIT 51)
                ORDER BY id DESC LIMIT 51
                """)) {
                p.setString(1,account);p.setString(2,peer);p.setLong(3,before);p.setString(4,peer);p.setString(5,account);p.setLong(6,before);
                try(ResultSet r=p.executeQuery()) {
                    while(r.next()) {
                        if(entries.size()==50){more=true;break;}
                        oldest=r.getLong(1);newest=Math.max(newest,oldest);
                        entries.add(new ChatEntry(r.getString(2),r.getString(3),r.getLong(4)));
                    }
                }
            }
        }else {
            for(int i=saved.size()-1;i>=0;i--) {
                Saved m=saved.get(i);long id=i+1L;
                if(id>=before || !(m.entry().username().equalsIgnoreCase(account)&&m.recipient().equalsIgnoreCase(peer)
                        || m.entry().username().equalsIgnoreCase(peer)&&m.recipient().equalsIgnoreCase(account)))continue;
                if(entries.size()==50){more=true;break;}
                oldest=id;newest=Math.max(newest,id);entries.add(m.entry());
            }
        }
        Collections.reverse(entries);return new Page(entries,oldest,newest,more);
    }
    void markRead(String account,String peer,long through) throws SQLException {
        if(database==null || through<=0)return;
        try(Connection c=database.open();PreparedStatement p=c.prepareStatement("UPDATE direct_messages SET read_at=GREATEST(sent_at,?) WHERE recipient=? AND sender=? AND id<=? AND read_at IS NULL")) {
            p.setLong(1,System.currentTimeMillis());p.setString(2,account);p.setString(3,peer);p.setLong(4,through);p.executeUpdate();
        }
    }

    long append(String recipient, ChatEntry entry) throws Exception {
        if (database != null) {
            try (Connection c = database.open(); PreparedStatement p = c.prepareStatement(
                    "INSERT INTO direct_messages(sender, recipient, message_text, sent_at) VALUES (?, ?, ?, ?)",Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, entry.username()); p.setString(2, recipient);
                p.setString(3, entry.text()); p.setLong(4, entry.timestamp()); p.executeUpdate();
                try(ResultSet keys=p.getGeneratedKeys()){if(!keys.next())throw new SQLException("Missing message ID");return keys.getLong(1);}
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
            return saved.size();
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
                        FROM direct_messages WHERE sender = ? OR recipient = ?
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
