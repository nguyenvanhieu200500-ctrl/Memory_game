package memorygame.client;

import memorygame.common.Protocol;
import memorygame.common.Protocol.ChatEntry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local chat history, separated by server and account. Never stores credentials. */
final class WhisperHistory {
    record Conversation(String peer, List<ChatEntry> messages) {
        ChatEntry latest() { return messages.get(messages.size() - 1); }
    }
    private final Path file;
    private final Map<String, Conversation> conversations = new HashMap<>();

    WhisperHistory() { file = null; }

    WhisperHistory(Path directory, String host, int port, String account) throws IOException {
        String scope = host.toLowerCase(Locale.ROOT) + ":" + port + "/" + account.toLowerCase(Locale.ROOT);
        file = directory.resolve(UUID.nameUUIDFromBytes(scope.getBytes(StandardCharsets.UTF_8)) + ".properties");
        if (!Files.exists(file)) return;
        Properties data = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { data.load(reader); }
        try {
            int count = Integer.parseInt(data.getProperty("count", "0"));
            for (int i = 0; i < count; i++) {
                String prefix = "chat." + i + ".", peer = data.getProperty(prefix + "peer");
                int size = Integer.parseInt(data.getProperty(prefix + "count", "0"));
                List<ChatEntry> messages = new ArrayList<>();
                for (int j = Math.max(0, size - Protocol.CHAT_HISTORY_LIMIT); j < size; j++) {
                    String entry = prefix + j + ".";
                    messages.add(new ChatEntry(Objects.requireNonNull(data.getProperty(entry + "from")),
                            Objects.requireNonNull(data.getProperty(entry + "text")), Long.parseLong(data.getProperty(entry + "time"))));
                }
                if (!messages.isEmpty()) conversations.put(key(Objects.requireNonNull(peer)), new Conversation(peer, List.copyOf(messages)));
            }
        } catch (RuntimeException e) { throw new IOException("Lịch sử trò chuyện không hợp lệ", e); }
    }
    private static String key(String peer) { return peer.toLowerCase(Locale.ROOT); }
    List<Conversation> conversations() {
        return conversations.values().stream().sorted(Comparator.comparingLong((Conversation c) -> c.latest().timestamp()).reversed()
                .thenComparing(Conversation::peer)).toList();
    }
    List<ChatEntry> messages(String peer) {
        Conversation conversation = conversations.get(key(peer));
        return conversation == null ? List.of() : conversation.messages();
    }
    void append(String peer, ChatEntry entry) throws IOException {
        List<ChatEntry> messages = new ArrayList<>(messages(peer)); messages.add(entry);
        while (messages.size() > Protocol.CHAT_HISTORY_LIMIT) messages.remove(0);
        conversations.put(key(peer), new Conversation(peer, List.copyOf(messages)));
        persist();
    }

    void mergeServer(Map<String, List<ChatEntry>> history) throws IOException {
        for (var conversation : history.entrySet()) {
            String peer = conversation.getKey();
            List<ChatEntry> merged = new ArrayList<>(messages(peer));
            // Remove matching cached copies once, preserving intentionally repeated messages.
            for (ChatEntry entry : conversation.getValue()) merged.remove(entry);
            merged.addAll(conversation.getValue());
            merged.sort(Comparator.comparingLong(ChatEntry::timestamp));
            if (merged.size() > Protocol.CHAT_HISTORY_LIMIT)
                merged = new ArrayList<>(merged.subList(merged.size() - Protocol.CHAT_HISTORY_LIMIT, merged.size()));
            if (!merged.isEmpty()) conversations.put(key(peer), new Conversation(peer, List.copyOf(merged)));
        }
        persist();
    }

    private void persist() throws IOException {
        if (file == null) return;
        Properties data = new Properties();
        List<Conversation> all = conversations(); data.setProperty("count", String.valueOf(all.size()));
        for (int i = 0; i < all.size(); i++) {
            Conversation c = all.get(i); String prefix = "chat." + i + ".";
            data.setProperty(prefix + "peer", c.peer()); data.setProperty(prefix + "count", String.valueOf(c.messages().size()));
            for (int j = 0; j < c.messages().size(); j++) {
                ChatEntry message = c.messages().get(j); String entryKey = prefix + j + ".";
                data.setProperty(entryKey + "from", message.username()); data.setProperty(entryKey + "text", message.text());
                data.setProperty(entryKey + "time", String.valueOf(message.timestamp()));
            }
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "chat-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { data.store(writer, "Whisper history"); }
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
