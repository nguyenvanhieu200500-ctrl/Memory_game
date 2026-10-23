package memorygame.test;

import memorygame.common.ChatPanel;
import memorygame.common.Protocol.ChatEntry;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class ChatPanelTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<String> sent = new ArrayList<>();
            ChatPanel panel = new ChatPanel(sent::add);
            JTextField input = find(panel, JTextField.class);
            JTextArea transcript = find(panel, JTextArea.class);
            JButton button = find(panel, JButton.class);
            input.setText("Xin chào"); input.postActionEvent();
            input.setText("Trả lời"); button.doClick();
            if (!sent.equals(List.of("Xin chào", "Trả lời"))) throw new AssertionError("Enter/button send failed");
            input.setText(" "); input.postActionEvent();
            input.setText("x".repeat(301)); button.doClick();
            if (sent.size() != 2) throw new AssertionError("Invalid message sent");
            panel.append(new ChatEntry("player_1", "<html>Xin chào 👋", 1000));
            if (!transcript.getText().contains("player_1: <html>Xin chào 👋") || transcript.isEditable())
                throw new AssertionError("Transcript must show plain text and sender");
            panel.setSendingEnabled(false);
            if (input.isEnabled() || button.isEnabled()) throw new AssertionError("Chat remains enabled after match");
            panel.setMessages(List.of());
            if (!transcript.getText().isEmpty()) throw new AssertionError("Old room transcript remains");
            if (find(new ChatPanel(null), JTextField.class) != null) throw new AssertionError("Server chat must be read-only");
        });
        System.out.println("CHAT_PANEL_OK enter button validation plainText disabled cleared readOnlyMonitor");
    }

    private static <T> T find(Container root, Class<T> type) {
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)
                    && (!(component instanceof JButton b) || "Gửi".equals(b.getText()))) return type.cast(component);
            if (component instanceof Container child) {
                T result = find(child, type);
                if (result != null) return result;
            }
        }
        return null;
    }
}
