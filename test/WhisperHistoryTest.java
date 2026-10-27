package memorygame.client;

import memorygame.common.Protocol.ChatEntry;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class WhisperHistoryTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("whisper-history-test");
        WhisperHistory history = new WhisperHistory(directory, "localhost", 12345, "Alice");
        if (!history.conversations().isEmpty()) throw new AssertionError("New history not empty");
        history.append("Bob", new ChatEntry("Alice", "Xin chào <html> 👋", 1));
        history.append("Carol", new ChatEntry("Carol", "Chào bạn", 2));
        history.append("BOB", new ChatEntry("Bob", "Trả lời", 3));
        if (history.conversations().size() != 2 || !history.conversations().get(0).peer().equals("BOB"))
            throw new AssertionError("Case folding / recency ordering");
        WhisperHistory loaded = new WhisperHistory(directory, "LOCALHOST", 12345, "ALICE");
        if (!loaded.messages("bob").equals(history.messages("Bob"))) throw new AssertionError("Lost Unicode or messages on reload");
        if (!new WhisperHistory(directory, "localhost", 12345, "other").conversations().isEmpty()
                || !new WhisperHistory(directory, "elsewhere", 12345, "Alice").conversations().isEmpty()
                || !new WhisperHistory(directory, "localhost", 12346, "Alice").conversations().isEmpty())
            throw new AssertionError("History leaked between accounts or servers");
        for (int i = 0; i < 105; i++) loaded.append("Bob", new ChatEntry("Alice", "Tin " + i, i + 10));
        WhisperHistory bounded = new WhisperHistory(directory, "localhost", 12345, "Alice");
        if (bounded.messages("bob").size() != 100 || !bounded.messages("bob").get(0).text().equals("Tin 5"))
            throw new AssertionError("History retention");
        var snapshot = java.util.Map.of("Bob", List.of(new ChatEntry("Alice", "Xin chào <html> 👋", 1),
                new ChatEntry("Bob", "Trả lời", 3), new ChatEntry("Bob", "Từ máy khác", 4)));
        history.mergeServer(snapshot); history.mergeServer(snapshot);
        if (history.messages("Bob").size() != 3) throw new AssertionError("Sync duplicated cached messages");
        WhisperHistory fresh = new WhisperHistory(Files.createTempDirectory("fresh-chat"), "localhost", 12345, "Alice");
        fresh.mergeServer(snapshot);
        if (!fresh.messages("Bob").equals(history.messages("Bob"))) throw new AssertionError("Fresh device sync");
        // Restore peer casing used by the existing conversation UI checks.
        history.append("BOB", new ChatEntry("Bob", "More", 5));
        SwingUtilities.invokeAndWait(() -> {
            AtomicReference<String> opened = new AtomicReference<>();
            ConversationsPanel panel = new ConversationsPanel(opened::set);
            panel.update(history.conversations());
            JList<?> list = findList(panel);
            if (list == null || list.getModel().getSize() != 2) throw new AssertionError("Missing conversations");
            list.setSelectedIndex(0);
            list.getActionMap().get("open").actionPerformed(new ActionEvent(list, 0, "open"));
            if (!"BOB".equals(opened.get())) throw new AssertionError("Keyboard opened wrong peer");
            list.setSize(420, 300); opened.set(null);
            list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_CLICKED, 0, 0, 20, 100, 1, false, MouseEvent.BUTTON1));
            if (!"Carol".equals(opened.get())) throw new AssertionError("Click opened wrong peer");
            opened.set(null);
            list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_CLICKED, 0, 0, 20, 250, 1, false, MouseEvent.BUTTON1));
            if (opened.get() != null) throw new AssertionError("Blank space opened a chat");
            panel.update(List.of());
            if (list.getModel().getSize() != 0) throw new AssertionError("Empty history");
        });
        System.out.println("WHISPER_HISTORY_OK persistence unicode accountServerIsolation recency retention mouse keyboard empty");
    }
    private static JList<?> findList(Container parent) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JList<?> list) return list;
            if (c instanceof Container nested) { JList<?> found = findList(nested); if (found != null) return found; }
        }
        return null;
    }
}
