package memorygame.client;

import memorygame.common.AppTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renders the persisted history received in HISTORY_UPDATE, including older records. */
final class HistoryPanel extends JPanel {
    record Entry(String time, String result, String reason, Integer pairs, Integer rating, Integer coins, String raw) {
        static Entry parse(String raw) {
            String[] fields = raw.split("\\s*\\|\\s*", -1);
            return new Entry(fields.length > 1 ? fields[0] : "—", fields.length > 3 ? fields[3] : "Bản ghi cũ",
                    fields.length > 1 ? fields[1] : raw,
                    fields.length > 2 ? number(fields[2], "(\\d+)\\s+cặp") : null,
                    fields.length > 4 ? number(fields[4], "Xếp hạng\\s+([+-]?\\d+)") : null,
                    fields.length > 5 ? number(fields[5], "\\+?(\\d+)\\s+xu") : null, raw);
        }
        private static Integer number(String text, String pattern) {
            Matcher match = Pattern.compile(pattern).matcher(text.trim());
            if (!match.matches()) return null;
            try { return Integer.valueOf(match.group(1)); }
            catch (NumberFormatException ignored) { return null; }
        }
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setPaint(new GradientPaint(0, 0, new Color(0xFFF9F1), w, h, new Color(0xC3A2FF)));
        g.fillRect(0, 0, w, h);
        for (int i = 0; i < 14; i++) {
            g.setColor(new Color(246, 216, 255, 65));
            g.fillOval((i * 179) % Math.max(1, w) - 80, (i * 113) % Math.max(1, h) - 50, 180, 120);
            LobbyStyle.star(g, (i * 197 + 20) % Math.max(1, w), (i * 137 + 19) % Math.max(1, h), 5,
                    new Color(255, 235, 155, 190));
        }
        g.dispose();
    }

    HistoryPanel(String username, int wallet, List<String> history, Runnable close) {
        super(new BorderLayout(12, 12));
        setBackground(AppTheme.BACKGROUND);
        setBorder(new EmptyBorder(18, 20, 18, 20));
        List<Entry> entries = history.stream().map(Entry::parse).toList();
        JPanel top = clear(new BorderLayout(0, 12));
        JPanel toolbar = clear(new BorderLayout());
        toolbar.add(text("MEMORY GAME  |  Lịch sử trận đấu", 22, LoginPanel.CYAN, true));
        JPanel walletCard = LobbyStyle.surface(new BorderLayout(), 1);
        walletCard.setBorder(new EmptyBorder(7, 12, 9, 12));
        JLabel walletText = text(wallet + " xu", 16, AppTheme.GOLD_TEXT, true);
        walletText.setIcon(new HistoryIcon(2, 28, AppTheme.GOLD_TEXT)); walletText.setIconTextGap(8);
        walletCard.add(walletText);
        toolbar.add(walletCard, BorderLayout.EAST);
        top.add(toolbar, BorderLayout.NORTH);
        JPanel overview = clear(new BorderLayout(0, 10));
        JPanel intro = LobbyStyle.surface(new BorderLayout(12, 0), 2);
        intro.setBorder(new EmptyBorder(12, 18, 12, 18));
        JPanel titles = clear(new GridLayout(1, 1));
        titles.add(text("Lịch sử trận đấu  •  " + username, 23, LoginPanel.INK, true));
        JLabel historyIcon = new JLabel(new HistoryIcon(0, 44, LobbyStyle.PURPLE));
        intro.add(historyIcon, BorderLayout.WEST);
        intro.add(titles);
        intro.add(new LobbyArtwork(true, false, true), BorderLayout.EAST);
        overview.add(intro, BorderLayout.NORTH);
        JPanel stats = clear(new GridLayout(1, 3, 10, 0));
        stats.add(metric("Trận được lưu", String.valueOf(entries.size()), LoginPanel.CYAN, 0));
        stats.add(metric("Cặp đã ghép", total(entries, false), AppTheme.SECONDARY, 1));
        stats.add(metric("Xu nhận được", total(entries, true), AppTheme.GOLD_TEXT, 2));
        overview.add(stats);
        top.add(overview);
        add(top, BorderLayout.NORTH);

        JPanel body = LobbyStyle.surface(new BorderLayout(0, 10), 1);
        body.setBorder(new EmptyBorder(14, 14, 14, 14));
        body.add(text("Thống kê trong lịch sử được lưu (tối đa 50 trận gần nhất)", 12, LoginPanel.MUTED, false), BorderLayout.NORTH);
        DefaultTableModel model = new DefaultTableModel(new String[]{"Thời gian", "Kết quả", "Cặp đã ghép", "Điểm xếp hạng", "Xu nhận"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        for (Entry entry : entries) model.addRow(new Object[]{entry.time(), entry.result(),
                entry.pairs() == null ? "—" : entry.pairs() + " cặp", signed(entry.rating()),
                entry.coins() == null ? "—" : "+" + entry.coins() + " xu"});
        JTable table = new JTable(model);
        table.setRowHeight(64);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 5));
        table.setBackground(AppTheme.SURFACE);
        table.setForeground(LoginPanel.INK);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 34));
        DefaultTableCellRenderer header = new DefaultTableCellRenderer();
        header.setBackground(new Color(0xEDE3FC));
        header.setForeground(new Color(0x614494));
        header.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        header.setBorder(new EmptyBorder(8, 10, 8, 10));
        table.getTableHeader().setDefaultRenderer(header);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                Entry entry = entries.get(row);
                setBackground(selected ? AppTheme.SELECTED : row == 0 ? new Color(0xFFF5E6)
                        : entry.result().contains("Bỏ cuộc") ? new Color(0xFFF0F7)
                        : row % 2 == 0 ? new Color(0xF7F2FF) : new Color(0xFBF8FF));
                setIcon(null);
                setFont(t.getFont().deriveFont(column >= 3 ? Font.BOLD : Font.PLAIN));
                setForeground(LoginPanel.INK);
                setBorder(new EmptyBorder(8, 10, 8, 10));
                setHorizontalAlignment(column >= 2 ? CENTER : LEFT);
                setToolTipText(entry.raw());
                if (column == 1) {
                    setIcon(entry.result().contains("Bỏ cuộc") ? new HistoryIcon(3, 26, AppTheme.DANGER)
                            : entry.result().startsWith("Hạng") ? LobbyStyle.achievementIcon(false)
                            : new HistoryIcon(0, 26, AppTheme.BLUE));
                    setIconTextGap(9);
                    String color = entry.result().contains("Bỏ cuộc") ? AppTheme.hex(AppTheme.DANGER)
                            : entry.result().equals("Hạng 1") ? AppTheme.hex(AppTheme.SECONDARY) : AppTheme.hex(AppTheme.DANGER);
                    setText("<html><b><font color='" + color + "'>" + escape(entry.result())
                            + "</font></b><br><font color='" + AppTheme.hex(AppTheme.MUTED) + "'>" + escape(entry.reason()) + "</font></html>");
                } else if (column == 3 && entry.rating() != null) {
                    setForeground(entry.rating() < 0 ? AppTheme.DANGER : AppTheme.PRIMARY);
                } else if (column == 4) setForeground(AppTheme.GOLD_TEXT);
                return this;
            }
        });
        int[] widths = {180, 310, 110, 140, 110};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        String[] rowNumbers = new String[entries.size()];
        for (int i = 0; i < rowNumbers.length; i++) rowNumbers[i] = String.valueOf(i + 1);
        JList<String> numbers = new JList<>(rowNumbers);
        numbers.setFixedCellHeight(table.getRowHeight()); numbers.setFixedCellWidth(46);
        numbers.setFocusable(false);
        numbers.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, false, false);
                setHorizontalAlignment(CENTER); setForeground(LobbyStyle.PURPLE);
                setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
                setBackground(index == 0 ? new Color(0xFFF5E6) : new Color(0xF7F2FF));
                setBorder(BorderFactory.createMatteBorder(0, 0, 5, 0, Color.WHITE));
                return this;
            }
        });
        scroll.setRowHeaderView(numbers);
        JLabel numberHeader = text("#", 13, LobbyStyle.PURPLE, true);
        numberHeader.setHorizontalAlignment(SwingConstants.CENTER); numberHeader.setOpaque(true);
        numberHeader.setBackground(new Color(0xEDE3FC));
        scroll.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, numberHeader);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(AppTheme.SURFACE);
        if (entries.isEmpty()) {
            JPanel empty = clear(new GridBagLayout());
            JPanel message = clear(new GridLayout(2, 1, 0, 8));
            message.add(text("Chưa có trận đấu", 21, LoginPanel.INK, true));
            message.add(text("Kết quả sẽ xuất hiện sau khi bạn hoàn thành trận.", 13, LoginPanel.MUTED, false));
            empty.add(message);
            body.add(empty);
        } else body.add(scroll);
        JPanel footer = clear(new BorderLayout(10, 0));
        boolean missing = entries.stream().anyMatch(e -> e.pairs() == null || e.coins() == null || e.rating() == null);
        if (missing) footer.add(text("— : dữ liệu chưa có trong bản ghi cũ.", 11, LoginPanel.MUTED, false));
        JButton done = LobbyStyle.button("Đóng", true);
        done.setPreferredSize(new Dimension(120, 42));
        done.addActionListener(e -> close.run());
        footer.add(done, BorderLayout.EAST);
        body.add(footer, BorderLayout.SOUTH);
        add(body);
    }

    private static String signed(Integer value) { return value == null ? "—" : (value >= 0 ? "+" : "") + value; }
    private static String total(List<Entry> entries, boolean coins) {
        if (entries.stream().anyMatch(e -> (coins ? e.coins() : e.pairs()) == null)) return "—";
        long sum = entries.stream().mapToLong(e -> coins ? e.coins() : e.pairs()).sum();
        return (coins ? "+" : "") + sum;
    }
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
    private static JPanel clear(LayoutManager layout) { JPanel panel = new JPanel(layout); panel.setOpaque(false); return panel; }
    private static JLabel text(String value, int size, Color color, boolean bold) {
        JLabel label = new JLabel(value); label.setForeground(color);
        label.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size)); return label;
    }
    private static JPanel metric(String title, String value, Color color, int kind) {
        JPanel panel = new JPanel(new BorderLayout(12, 0)) {
            { setOpaque(false); }
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color tint = kind == 0 ? new Color(0xEEE1FF) : kind == 1 ? new Color(0xD8F8F2) : new Color(0xFFF0CB);
                g.setPaint(new GradientPaint(0, 0, Color.WHITE, getWidth(), getHeight(), tint));
                g.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 20, 20);
                g.setColor(tint.darker()); g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 20, 20);
                g.dispose();
            }
        };
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));
        panel.add(new JLabel(new HistoryIcon(kind, 44, color)), BorderLayout.WEST);
        JPanel labels = clear(new BorderLayout(0, 4));
        labels.add(text(title, 12, LoginPanel.MUTED, false), BorderLayout.NORTH);
        labels.add(text(value, 27, color, true)); panel.add(labels); return panel;
    }

    private static final class HistoryIcon implements Icon {
        private final int kind, size;
        private final Color color;
        HistoryIcon(int kind, int size, Color color) { this.kind = kind; this.size = size; this.color = color; }
        public int getIconWidth() { return size; }
        public int getIconHeight() { return size; }
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create(); g.translate(x, y); g.scale(size / 40.0, size / 40.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 25)); g.fillOval(0, 0, 40, 40);
            g.setColor(color); g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (kind == 0) { g.drawOval(9, 9, 22, 22); g.drawLine(20, 13, 20, 21); g.drawLine(20, 21, 26, 21); }
            else if (kind == 1) {
                g.rotate(-.15, 20, 20); g.fillRoundRect(13, 7, 18, 24, 4, 4);
                g.setColor(Color.WHITE); g.drawRoundRect(8, 11, 18, 24, 4, 4);
                g.setColor(color); g.fillRoundRect(8, 11, 18, 24, 4, 4);
                LobbyStyle.star(g, 17, 23, 6, Color.WHITE);
            } else if (kind == 2) {
                for (int cy : new int[]{25, 20, 15}) {
                    g.setColor(new Color(0xF6AB27)); g.fillRoundRect(9, cy - 4, 23, 9, 6, 6);
                    g.setColor(new Color(0xFFE38A)); g.fillOval(9, cy - 5, 23, 8);
                    g.setColor(new Color(0xC88717)); g.setStroke(new BasicStroke(1f)); g.drawOval(9, cy - 5, 23, 8);
                }
            } else { g.drawLine(13, 13, 27, 27); g.drawLine(27, 13, 13, 27); }
            g.dispose();
        }
    }
}
