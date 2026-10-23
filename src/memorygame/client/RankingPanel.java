package memorygame.client;

import memorygame.common.AppTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Uses the order, ranks and statistics supplied by RANKING_UPDATE. */
final class RankingPanel extends JPanel {
    record Entry(int rank, String username, int rating, int wins, int games, double winRate) {
        static Entry from(Map<String, Object> row) {
            return new Entry(((Number) row.get("rank")).intValue(), String.valueOf(row.get("username")),
                    ((Number) row.get("rating")).intValue(), ((Number) row.get("wins")).intValue(),
                    ((Number) row.get("games")).intValue(), ((Number) row.get("winRate")).doubleValue());
        }
        String rateText() { return String.format(Locale.forLanguageTag("vi-VN"), "%.1f%%", winRate); }
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setPaint(new LinearGradientPaint(0, 0, 0, Math.max(1, h), new float[]{0, .2f, .65f, 1},
                new Color[]{new Color(0x8960F6), new Color(0xC7B5FA), new Color(0xF5E8FF), new Color(0xB8AAFF)}));
        g.fillRect(0, 0, w, h);
        for (int i = 0; i < 18; i++) {
            g.setColor(new Color(255, 236, 253, 65));
            g.fillOval((i * 179) % Math.max(1, w) - 90, (i * 103) % Math.max(1, h) - 40, 180, 100);
            LobbyStyle.star(g, (i * 137 + 30) % Math.max(1, w), (i * 71 + 18) % Math.max(1, h),
                    4, new Color(255, 241, 187, 185));
        }
        g.dispose();
    }

    RankingPanel(String username, List<Map<String, Object>> rows, Runnable close) {
        super(new BorderLayout(0, 12));
        setBackground(AppTheme.BACKGROUND);
        setBorder(new EmptyBorder(18, 20, 16, 20));
        List<Entry> entries = rows.stream().map(Entry::from).toList();
        JPanel top = clear(new BorderLayout(0, 12));
        JPanel heading = clear(new BorderLayout());
        JPanel titles = clear(new GridLayout(2, 1, 0, 5));
        titles.add(text("MEMORY GAME  |  Bảng xếp hạng", 23, Color.WHITE, true));
        titles.add(text("Thành tích của các người chơi", 13, new Color(0xF4E9FF), false));
        JLabel trophy = new JLabel(LobbyStyle.achievementIcon(false));
        trophy.setBorder(new EmptyBorder(0, 0, 0, 12));
        heading.add(trophy, BorderLayout.WEST);
        heading.add(titles);
        JButton exit = LobbyStyle.button("×", false);
        exit.setPreferredSize(new Dimension(42, 38));
        exit.setBorder(new EmptyBorder(0, 0, 0, 0));
        exit.setMargin(new Insets(0, 0, 0, 0));
        exit.setToolTipText("Đóng bảng xếp hạng");
        exit.getAccessibleContext().setAccessibleName("Đóng bảng xếp hạng");
        exit.addActionListener(e -> close.run());
        heading.add(exit, BorderLayout.EAST);
        top.add(heading, BorderLayout.NORTH);
        JPanel podium = clear(new GridLayout(1, 3, 12, 0));
        for (int rank : new int[]{2, 1, 3}) {
            Entry entry = entries.stream().filter(e -> e.rank() == rank).findFirst().orElse(null);
            JPanel slot = clear(new BorderLayout());
            slot.setBorder(new EmptyBorder(rank == 1 ? 0 : 18, 0, 0, 0));
            slot.add(new PodiumCard(rank, entry, username));
            podium.add(slot);
        }
        podium.setPreferredSize(new Dimension(0, 198));
        top.add(podium);
        add(top, BorderLayout.NORTH);

        JPanel board = LobbyStyle.surface(new BorderLayout(), 1);
        board.setBorder(new EmptyBorder(10, 10, 10, 10));
        DefaultTableModel model = new DefaultTableModel(new String[]{"Hạng", "Người chơi", "Điểm", "Thắng / Trận", "Tỷ lệ thắng"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        for (Entry entry : entries) model.addRow(new Object[]{entry.rank(),
                entry.username() + (entry.username().equalsIgnoreCase(username) ? "  [Bạn]" : ""),
                entry.rating(), entry.wins() + "/" + entry.games(), entry.rateText()});
        JTable table = new JTable(model);
        table.setRowHeight(42);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        table.setBackground(AppTheme.SURFACE);
        table.setForeground(LoginPanel.INK);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 38));
        DefaultTableCellRenderer header = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                setHorizontalAlignment(column == 1 ? LEFT : CENTER);
                setBackground(new Color(0xE5DAFC));
                setForeground(new Color(0x594395));
                setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                setBorder(new EmptyBorder(6, 10, 6, 10));
                return this;
            }
        };
        table.getTableHeader().setDefaultRenderer(header);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                Entry entry = entries.get(row);
                boolean self = entry.username().equalsIgnoreCase(username);
                setBackground(self ? AppTheme.SELECTED : selected ? AppTheme.SELECTED
                        : entry.rank() == 1 ? new Color(0xFFF5DF)
                        : row % 2 == 0 ? new Color(0xF6F2FF) : AppTheme.SURFACE);
                setForeground(self ? LoginPanel.CYAN : entry.rank() <= 3 ? medalColor(entry.rank()) : LoginPanel.INK);
                setFont(t.getFont().deriveFont(self || entry.rank() <= 3 ? Font.BOLD : Font.PLAIN));
                setHorizontalAlignment(column == 1 ? LEFT : CENTER);
                setIcon(column == 1 ? LobbyStyle.cat(30) : column == 0 && entry.rank() <= 3
                        ? LobbyStyle.achievementIcon(true) : null);
                setIconTextGap(10);
                setBorder(self ? BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 0, 1, 0, LoginPanel.CYAN), new EmptyBorder(0, 10, 0, 10))
                        : new EmptyBorder(1, 10, 1, 10));
                setToolTipText("Hạng " + entry.rank() + " · " + entry.username() + " · " + entry.rating()
                        + " điểm · " + entry.wins() + "/" + entry.games() + " thắng/trận · " + entry.rateText());
                return this;
            }
        });
        int[] widths = {70, 270, 110, 150, 150};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(AppTheme.SURFACE);
        if (entries.isEmpty()) {
            JLabel empty = text("Chưa có dữ liệu xếp hạng", 18, LoginPanel.MUTED, false);
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            board.add(empty);
        } else board.add(scroll);
        add(board);
        JPanel footer = LobbyStyle.surface(new BorderLayout(12, 0), 2);
        footer.setBorder(new EmptyBorder(10, 14, 10, 14));
        Entry self = entries.stream().filter(e -> e.username().equalsIgnoreCase(username)).findFirst().orElse(null);
        JLabel mine = text(self == null ? "Tài khoản của bạn chưa có trong bảng xếp hạng."
                : "Hạng của bạn: " + self.rank() + "   •   Điểm: " + self.rating(), 14, LoginPanel.CYAN, true);
        mine.setIcon(self == null ? null : LobbyStyle.cat(42));
        mine.setIconTextGap(12);
        footer.add(mine);
        JButton done = LobbyStyle.button("Đóng", true);
        done.setPreferredSize(new Dimension(130, 40));
        done.addActionListener(e -> close.run());
        footer.add(done, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }

    private static Color medalColor(int rank) {
        return rank == 1 ? new Color(0xB76A0B) : rank == 2 ? new Color(0x475CA7) : new Color(0xB44771);
    }
    private static final class PodiumCard extends JPanel {
        private final int rank;
        private final Entry entry;
        private final String self;
        PodiumCard(int rank, Entry entry, String self) {
            this.rank = rank; this.entry = entry; this.self = self; setOpaque(false);
            String description = entry == null ? "Chưa có người chơi hạng " + rank
                    : "Hạng " + entry.rank() + ": " + entry.username() + ", " + entry.rating() + " điểm, "
                    + entry.wins() + "/" + entry.games() + " thắng/trận, " + entry.rateText();
            setToolTipText(description); getAccessibleContext().setAccessibleName(description);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p = (Graphics2D) g.create();
            p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            p.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            Color color = medalColor(rank);
            Color edge = rank == 1 ? new Color(0xFFBF43) : rank == 2 ? new Color(0x8FA7FF) : new Color(0xEC95CF);
            for (int glow = 9; glow > 0; glow -= 2) {
                p.setStroke(new BasicStroke(glow));
                p.setColor(new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), 30));
                p.drawRoundRect(7, 29, w - 15, h - 36, 24, 24);
            }
            p.setPaint(new GradientPaint(0, 30, rank == 1 ? new Color(0xFFE39B)
                    : rank == 2 ? new Color(0xCBD8FF) : new Color(0xF8CCE7), w, h, new Color(0xFFFBF2)));
            p.fillRoundRect(7, 29, w - 15, h - 36, 24, 24);
            p.setColor(edge); p.setStroke(new BasicStroke(3f));
            p.drawRoundRect(7, 29, w - 15, h - 36, 24, 24);
            p.setColor(Color.WHITE); p.setStroke(new BasicStroke(1.5f));
            p.drawRoundRect(11, 33, w - 23, h - 44, 20, 20);
            int cx = w / 2;
            java.awt.geom.Path2D crown = new java.awt.geom.Path2D.Double();
            crown.moveTo(cx - 25, 39); crown.lineTo(cx - 32, 9); crown.lineTo(cx - 14, 20);
            crown.lineTo(cx, 1); crown.lineTo(cx + 14, 20); crown.lineTo(cx + 32, 9);
            crown.lineTo(cx + 25, 39); crown.closePath();
            p.setPaint(new GradientPaint(cx, 0, new Color(0xFFF6BF), cx, 40, edge)); p.fill(crown);
            p.setColor(color); p.setStroke(new BasicStroke(1.3f)); p.draw(crown);
            p.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 23)); center(p, String.valueOf(rank), cx, 33);
            for (int i = 0; i < 4; i++) {
                LobbyStyle.star(p, 21, 55 + i * 15, 4, edge);
                LobbyStyle.star(p, w - 22, 55 + i * 15, 4, edge);
            }
            if (entry != null) LobbyStyle.cat(42).paintIcon(this, p, cx - 21, 45);
            p.setColor(LoginPanel.INK); p.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            String name = entry == null ? "Chưa có người chơi" : entry.username() + (entry.username().equalsIgnoreCase(self) ? " [Bạn]" : "");
            while (p.getFontMetrics().stringWidth(name) > w - 32 && p.getFont().getSize() > 10)
                p.setFont(p.getFont().deriveFont((float) p.getFont().getSize() - 1));
            center(p, name, cx, 104);
            if (entry != null) {
                String[] values = {String.valueOf(entry.rating()), entry.wins() + "/" + entry.games(), entry.rateText()};
                String[] captions = {"Điểm", "Thắng / Trận", "Tỷ lệ thắng"};
                for (int i = 0; i < 3; i++) {
                    int x = (i * 2 + 1) * w / 6;
                    p.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16)); p.setColor(color);
                    center(p, values[i], x, h - 36);
                    p.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10)); p.setColor(LoginPanel.MUTED);
                    center(p, captions[i], x, h - 16);
                }
            }
            p.dispose();
        }
        private static void center(Graphics2D p, String value, int x, int y) {
            p.drawString(value, x - p.getFontMetrics().stringWidth(value) / 2, y);
        }
    }
    private static JPanel clear(LayoutManager layout) { JPanel result = new JPanel(layout); result.setOpaque(false); return result; }
    private static JLabel text(String value, int size, Color color, boolean bold) {
        JLabel label = new JLabel(value); label.setForeground(color);
        label.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size)); return label;
    }
}
