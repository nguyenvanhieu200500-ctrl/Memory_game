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
import java.util.function.Function;

/** Room transcript shared by the client and the read-only server monitor. */
public final class ChatPanel extends JPanel {
    private final JTextArea transcript = new JTextArea();
    private final JTextField input = new JTextField();
    private final JButton send = new JButton("Gửi");
    private final JLabel status = new JLabel("Tối đa 300 ký tự / tin nhắn");
    private JScrollPane scroll;
    private JPanel bubbles;
    private JTextPane avatarTranscript;
    private Function<String, Icon> transcriptAvatars;
    private String ownUsername;
    private Function<String, Icon> avatarResolver;
    private int bubbleWidth;
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
        scroll = new JScrollPane(transcript);
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
        refreshTranscriptAvatars();
        if (avatarTranscript != null) avatarTranscript.setCaretPosition(avatarTranscript.getDocument().getLength());
        refreshBubbles();
        if (bubbles != null) SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum()));
    }

    /** Keep room chat's chronological text layout, adding only sender icons. */
    public void useTranscriptAvatars(Function<String, Icon> avatars) {
        transcriptAvatars = avatars;
        avatarTranscript = new JTextPane();
        avatarTranscript.setEditable(false);
        avatarTranscript.setFont(transcript.getFont());
        avatarTranscript.setBackground(transcript.getBackground());
        avatarTranscript.setForeground(transcript.getForeground());
        avatarTranscript.setMargin(transcript.getMargin());
        avatarTranscript.getAccessibleContext().setAccessibleName(
                transcript.getAccessibleContext().getAccessibleName());
        scroll.setViewportView(avatarTranscript);
        refreshTranscriptAvatars();
    }

    public void refreshTranscriptAvatars() {
        if (avatarTranscript == null) return;
        int caret = avatarTranscript.getCaretPosition();
        javax.swing.text.StyledDocument document = avatarTranscript.getStyledDocument();
        try {
            document.remove(0, document.getLength());
            for (ChatEntry entry : entries) {
                Icon avatar = transcriptAvatars.apply(entry.username());
                if (avatar != null) {
                    javax.swing.text.SimpleAttributeSet attributes = new javax.swing.text.SimpleAttributeSet();
                    javax.swing.text.StyleConstants.setIcon(attributes, avatar);
                    document.insertString(document.getLength(), " ", attributes);
                }
                document.insertString(document.getLength(), " [" + TIME.format(Instant.ofEpochMilli(entry.timestamp()))
                        + "] " + entry.username() + ": " + entry.text() + "\n", null);
            }
            avatarTranscript.setCaretPosition(Math.min(caret, document.getLength()));
        } catch (javax.swing.text.BadLocationException e) {
            throw new IllegalStateException("Cannot render chat transcript", e);
        }
    }

    /** Opt-in presentation for private conversations; room chat remains a transcript. */
    public void useMessageBubbles(String username, Function<String, Icon> avatars) {
        ownUsername = username;
        avatarResolver = avatars;
        bubbles = new JPanel();
        bubbles.setLayout(new BoxLayout(bubbles, BoxLayout.Y_AXIS));
        bubbles.setBackground(new Color(247, 243, 255));
        scroll.setViewportView(bubbles);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                int width = scroll.getViewport().getWidth();
                if (width != bubbleWidth) { bubbleWidth = width; refreshBubbles(); }
            }
        });
        refreshBubbles();
    }

    public void refreshBubbles() {
        if (bubbles == null) return;
        bubbles.removeAll();
        int width = Math.max(180, scroll.getViewport().getWidth());
        int textWidth = Math.max(90, Math.min(320, (int) (width * .68) - 28));
        for (ChatEntry entry : entries) {
            boolean own = entry.username().equalsIgnoreCase(ownUsername);
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(7, 10, 7, 10));
            JPanel group = new JPanel(new BorderLayout(7, 0));
            group.setOpaque(false);
            Icon avatar = avatarResolver == null ? null : avatarResolver.apply(entry.username());
            JLabel face = new JLabel(avatar);
            face.setVerticalAlignment(SwingConstants.TOP);
            face.getAccessibleContext().setAccessibleName("Ảnh đại diện " + entry.username());
            face.setToolTipText(entry.username());
            JPanel bubble = new JPanel(new BorderLayout(0, 4)) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getBackground());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            bubble.setOpaque(false);
            bubble.setBackground(own ? new Color(119, 61, 232) : Color.WHITE);
            bubble.setBorder(new EmptyBorder(9, 12, 9, 12));
            // JTextArea keeps user text literal, including strings beginning with HTML tags.
            JTextArea body = new JTextArea(entry.text());
            body.setEditable(false);
            body.setOpaque(false);
            body.setLineWrap(true);
            body.setWrapStyleWord(true);
            body.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            body.setForeground(own ? Color.WHITE : AppTheme.TEXT);
            body.setSize(textWidth, Short.MAX_VALUE);
            body.setPreferredSize(new Dimension(textWidth, body.getPreferredSize().height));
            JLabel author = new JLabel();
            author.putClientProperty("html.disable", true);
            author.setText(entry.username() + " · " + TIME.format(Instant.ofEpochMilli(entry.timestamp())));
            author.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            author.setForeground(own ? new Color(235, 222, 255) : AppTheme.MUTED);
            author.setPreferredSize(new Dimension(textWidth, 15));
            bubble.add(author, BorderLayout.NORTH);
            bubble.add(body, BorderLayout.CENTER);
            group.add(bubble, BorderLayout.CENTER);
            group.add(face, own ? BorderLayout.EAST : BorderLayout.WEST);
            row.add(group, own ? BorderLayout.EAST : BorderLayout.WEST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubbles.add(row);
        }
        bubbles.revalidate();
        bubbles.repaint();
    }

    public void append(ChatEntry entry) {
        if (entry == null) return;
        List<ChatEntry> next = new ArrayList<>(entries);
        next.add(entry);
        while (next.size() > Protocol.CHAT_HISTORY_LIMIT) next.remove(0);
        setMessages(next);
    }
}
