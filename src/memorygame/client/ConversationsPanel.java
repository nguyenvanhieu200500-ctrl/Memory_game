package memorygame.client;

import memorygame.common.AppTheme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** Recent private conversations; mouse and keyboard both open the selected peer. */
final class ConversationsPanel extends JPanel {
    private final DefaultListModel<WhisperHistory.Conversation> model = new DefaultListModel<>();
    private final JList<WhisperHistory.Conversation> list = new JList<>(model);
    private final JPanel content = new JPanel(new CardLayout());
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault());

    ConversationsPanel(Consumer<String> open) { this(open, peer -> 0); }
    ConversationsPanel(Consumer<String> open, ToIntFunction<String> unread) {
        super(new BorderLayout(0, 14)); setBackground(AppTheme.SURFACE); setBorder(new EmptyBorder(20, 20, 20, 20));
        JLabel title = new JLabel("Trò chuyện gần đây"); title.setForeground(AppTheme.PRIMARY);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 23)); add(title, BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); list.setFixedCellHeight(78);
        list.setBackground(AppTheme.SURFACE); list.setCellRenderer(new Renderer(unread));
        list.getAccessibleContext().setAccessibleName("Danh sách cuộc trò chuyện");
        list.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Runnable activate = () -> { if (list.getSelectedValue() != null) open.accept(list.getSelectedValue().peer()); };
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0 && list.getCellBounds(index, index).contains(e.getPoint())) { list.setSelectedIndex(index); activate.run(); }
            }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "open");
        list.getActionMap().put("open", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { activate.run(); }
        });
        JScrollPane scroll = new JScrollPane(list); scroll.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER));
        JLabel empty = new JLabel("Chưa có cuộc trò chuyện nào", SwingConstants.CENTER); empty.setForeground(AppTheme.MUTED);
        content.setOpaque(false); content.add(scroll, "list"); content.add(empty, "empty"); add(content);
        JLabel hint = new JLabel("Chọn một người để xem lịch sử và nhắn tiếp."); hint.setForeground(AppTheme.MUTED); add(hint, BorderLayout.SOUTH);
        update(List.of());
    }
    void update(List<WhisperHistory.Conversation> conversations) {
        String selected = list.getSelectedValue() == null ? "" : list.getSelectedValue().peer();
        model.clear();
        for (WhisperHistory.Conversation c : conversations) {
            model.addElement(c); if (c.peer().equalsIgnoreCase(selected)) list.setSelectedIndex(model.size()-1);
        }
        ((CardLayout) content.getLayout()).show(content, model.isEmpty() ? "empty" : "list");
    }
    private static final class Renderer implements ListCellRenderer<WhisperHistory.Conversation> {
        private final ToIntFunction<String> unread;
        Renderer(ToIntFunction<String> unread) { this.unread = unread; }
        public Component getListCellRendererComponent(JList<? extends WhisperHistory.Conversation> list,
                WhisperHistory.Conversation c, int index, boolean selected, boolean focus) {
            JPanel row = new JPanel(new BorderLayout(10, 7)); row.setBorder(new EmptyBorder(12, 14, 12, 14));
            row.setBackground(selected ? AppTheme.SELECTED : index % 2 == 0 ? AppTheme.INSET : AppTheme.SURFACE);
            JLabel name = text(c.peer() + (unread.applyAsInt(c.peer()) > 0 ? "  (" + unread.applyAsInt(c.peer()) + " mới)" : ""), AppTheme.TEXT); name.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            JLabel time = text(TIME.format(Instant.ofEpochMilli(c.latest().timestamp())), AppTheme.MUTED);
            JPanel heading = new JPanel(new BorderLayout()); heading.setOpaque(false); heading.add(name); heading.add(time, BorderLayout.EAST);
            row.add(heading, BorderLayout.NORTH);
            JLabel preview = text(c.latest().username() + ": " + c.latest().text(), AppTheme.MUTED);
            row.add(preview); row.setToolTipText(preview.getText()); return row;
        }
        private static JLabel text(String value, Color color) {
            JLabel label = new JLabel(); label.putClientProperty("html.disable", true); label.setText(value); label.setForeground(color); return label;
        }
    }
    static final class ChatIcon implements Icon {
        public int getIconWidth() { return 22; }
        public int getIconHeight() { return 22; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D p = (Graphics2D) g.create(); p.translate(x, y);
            p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            p.setColor(AppTheme.PRIMARY); p.setStroke(new BasicStroke(1.8f));
            p.drawRoundRect(2, 2, 18, 14, 6, 6); p.drawLine(6, 16, 5, 20); p.drawLine(5, 20, 11, 16);
            for (int i = 0; i < 3; i++) p.fillOval(6 + i * 4, 8, 2, 2); p.dispose();
        }
    }
}
