package memorygame.common;

import memorygame.common.Protocol.ChatEntry;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Room transcript shared by the client and the read-only server monitor. */
public final class ChatPanel extends JPanel {
    private final JTextArea transcript = new JTextArea();
    private final JTextField input = new JTextField();
    private final JButton send = new JButton("Gửi");
    private final JLabel status = new JLabel("Tối đa 300 ký tự / tin nhắn");
    private List<ChatEntry> entries = List.of();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public ChatPanel(Consumer<String> sender) {
        this(sender, sender == null ? "Chat phòng · Theo dõi" : "Chat phòng");
    }

    public ChatPanel(Consumer<String> sender, String heading) {
        super(new BorderLayout(6, 6));
        setOpaque(true);
        setBackground(AppTheme.SURFACE);
        setBorder(new EmptyBorder(6, 0, 0, 0));
        JLabel title = new JLabel(heading);
        title.setForeground(AppTheme.PRIMARY);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        add(title, BorderLayout.NORTH);
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        transcript.setBackground(AppTheme.SURFACE);
        transcript.setForeground(AppTheme.TEXT);
        transcript.setMargin(new Insets(8, 8, 8, 8));
        transcript.getAccessibleContext().setAccessibleName(heading);
        JScrollPane scroll = new JScrollPane(transcript);
        scroll.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER));
        add(scroll, BorderLayout.CENTER);
        if (sender != null) {
            JPanel compose = new JPanel(new BorderLayout(5, 5));
            compose.setOpaque(false);
            input.setBackground(AppTheme.SURFACE);
            input.setForeground(AppTheme.TEXT);
            input.setCaretColor(AppTheme.TEXT);
            send.setBackground(AppTheme.PRIMARY);
            send.setForeground(AppTheme.ON_ACCENT);
            send.setOpaque(true);
            send.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(AppTheme.PRIMARY), new EmptyBorder(5, 10, 5, 10)));
            input.getAccessibleContext().setAccessibleName("Nhập tin nhắn");
            input.setToolTipText("Nhập tin nhắn và nhấn Enter để gửi");
            compose.add(input);
            compose.add(send, BorderLayout.EAST);
            status.setForeground(AppTheme.MUTED);
            status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            compose.add(status, BorderLayout.SOUTH);
            Runnable submit = () -> {
                String text = input.getText().strip();
                if (!input.isEnabled() || text.isBlank()) return;
                if (text.length() > Protocol.CHAT_MAX_LENGTH) {
                    status.setText("Tin nhắn quá dài (tối đa 300 ký tự).");
                    return;
                }
                sender.accept(text);
                input.setText("");
                status.setText("Tối đa 300 ký tự / tin nhắn");
                input.requestFocusInWindow();
            };
            input.addActionListener(e -> submit.run());
            send.addActionListener(e -> submit.run());
            add(compose, BorderLayout.SOUTH);
        }
        setMinimumSize(new Dimension(180, 130));
    }

    public void setSendingEnabled(boolean enabled) {
        input.setEnabled(enabled);
        send.setEnabled(enabled);
        if (!enabled) input.setText("");
    }

    public void setMessages(List<ChatEntry> messages) {
        List<ChatEntry> next = messages == null ? List.of() : List.copyOf(messages);
        if (entries.equals(next)) return;
        entries = next;
        StringBuilder text = new StringBuilder();
        for (ChatEntry entry : entries) text.append('[').append(TIME.format(Instant.ofEpochMilli(entry.timestamp())))
                .append("] ").append(entry.username()).append(": ").append(entry.text()).append('\n');
        transcript.setText(text.toString());
        transcript.setCaretPosition(transcript.getDocument().getLength());
    }

    public void append(ChatEntry entry) {
        if (entry == null) return;
        List<ChatEntry> next = new ArrayList<>(entries);
        next.add(entry);
        while (next.size() > Protocol.CHAT_HISTORY_LIMIT) next.remove(0);
        setMessages(next);
    }
}
