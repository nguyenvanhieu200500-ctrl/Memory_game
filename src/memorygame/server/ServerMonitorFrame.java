package memorygame.server;

import memorygame.server.GameServer.*;
import memorygame.common.GameColors;
import memorygame.client.CardTheme;
import memorygame.common.AppTheme;
import memorygame.common.ChatPanel;
import java.util.Map;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Read-only, local view of the authoritative server state. */
public final class ServerMonitorFrame extends JFrame {
    private final ScheduledExecutorService refresh = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "server-monitor");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;

    public ServerMonitorFrame(GameServer server) {
        super("Memory Game — Giám sát Server");
        Dashboard dashboard = new Dashboard();
        setContentPane(dashboard);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1280, 850);
        setMinimumSize(new Dimension(1080, 760));
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                closed = true;
                refresh.shutdownNow();
            }
        });
        // Never hold the game lock on Swing's event dispatch thread.
        refresh.scheduleWithFixedDelay(() -> {
            MonitorSnapshot snapshot = server.monitorSnapshot();
            SwingUtilities.invokeLater(() -> { if (!closed) dashboard.render(snapshot); });
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    private static final Color INK = AppTheme.TEXT;
    private static final Color MUTED = AppTheme.MUTED;
    private static final Color BACKGROUND = AppTheme.BACKGROUND;
    private static final Color LINE = new Color(0xD4B9F9);
    private static final Color BLUE = new Color(0x4D0CD5);
    private static final Color GREEN = AppTheme.SECONDARY;
    private static final Color AMBER = AppTheme.GOLD_TEXT;

    private static JLabel label(String text, int size, Color color, boolean bold) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(color);
        return label;
    }

    private static JPanel panel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static JPanel surface(LayoutManager layout) {
        return surface(layout, LINE, false);
    }

    private static JPanel surface(LayoutManager layout, Color accent, boolean glow) {
        JPanel panel = new JPanel(layout) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D graphics = (Graphics2D) g.create();
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setColor(new Color(100, 52, 160, 40));
                graphics.fillRoundRect(3, 6, getWidth() - 7, getHeight() - 7, 22, 22);
                graphics.setPaint(new GradientPaint(0, 0, glow ? new Color(0xE7DBFC) : new Color(0xFFFBF7),
                        getWidth(), getHeight(), new Color(0xFCF5F9)));
                graphics.fillRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 22, 22);
                graphics.setStroke(new BasicStroke(glow ? 2f : 1.5f));
                graphics.setColor(accent);
                graphics.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 22, 22);
                graphics.setColor(new Color(255, 255, 255, 190));
                graphics.setStroke(new BasicStroke(1f));
                graphics.drawRoundRect(5, 5, getWidth() - 11, getHeight() - 11, 20, 20);
                graphics.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));
        return panel;
    }

    /** Small vector icons stay crisp without depending on platform emoji fonts. */
    private static final class MonitorIcon implements Icon {
        private final int kind;
        private final int size;
        private final Color color;
        MonitorIcon(int kind, int size, Color color) { this.kind = kind; this.size = size; this.color = color; }
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D p = (Graphics2D) g.create();
            p.translate(x, y);
            p.scale(size / 48.0, size / 48.0);
            p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            p.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 28));
            p.fillRoundRect(1, 1, 46, 46, 12, 12);
            p.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 80));
            p.drawRoundRect(1, 1, 46, 46, 12, 12);
            p.setColor(color);
            if (kind == 0 || kind == 1) {
                p.fillOval(18, 10, 12, 12);
                p.fillRoundRect(13, 25, 22, 14, 9, 9);
                if (kind == 1) {
                    p.fillOval(7, 17, 8, 8); p.fillRoundRect(5, 28, 9, 10, 5, 5);
                    p.fillOval(33, 17, 8, 8); p.fillRoundRect(34, 28, 9, 10, 5, 5);
                }
            } else if (kind == 2) {
                p.setStroke(new BasicStroke(2));
                p.drawRect(13, 10, 21, 29);
                p.fillPolygon(new int[]{13, 27, 27, 13}, new int[]{10, 7, 40, 36}, 4);
                p.setColor(BACKGROUND); p.fillOval(22, 23, 3, 3);
            } else {
                p.fillRoundRect(8, 15, 32, 22, 12, 12);
                p.setColor(BACKGROUND); p.fillRect(14, 21, 11, 3); p.fillRect(18, 17, 3, 11);
                p.fillOval(29, 21, 4, 4); p.fillOval(34, 26, 4, 4);
            }
            p.dispose();
        }
    }

    private static Map<String, Color> playerColors(RoomSnapshot room) {
        return GameColors.forPlayers(room.players().stream().map(PlayerSnapshot::name).toList());
    }

    private static String playerCode(RoomSnapshot room, String owner) {
        for (int i = 0; i < room.players().size(); i++) {
            if (room.players().get(i).name().equals(owner)) return "P" + (i + 1);
        }
        return "✓";
    }

    static final class Dashboard extends JPanel {
        private final JLabel serverStatus = label("●  Đang khởi động…", 13, MUTED, true);
        private final JLabel[] metrics = new JLabel[4];
        private final JLabel roomCount = label("0 phòng", 12, MUTED, false);
        private final JLabel roomSubtitle = label("", 12, MUTED, false);
        private final JLabel live = label("", 11, MUTED, true);
        private final JLabel turn = label("Chưa có trận đấu", 14, BLUE, true);
        private final JLabel clock = label("—", 21, BLUE, true);
        private final JLabel progressText = label("0 / 0 cặp", 24, INK, true);
        private final JProgressBar progress = new JProgressBar();
        private final JPanel players = panel(new GridLayout(0, 1, 0, 6));
        private final Board board = new Board();
        private final JPanel side = panel(new BorderLayout(0, 8));
        private final JLabel[] cardCounts = new JLabel[3];
        private final ChatPanel chat = new ChatPanel(null);
        private final DefaultListModel<RoomSnapshot> roomsModel = new DefaultListModel<>();
        private final JList<RoomSnapshot> roomsList = new JList<>(roomsModel);
        private final CardLayout roomListLayout = new CardLayout();
        private final JPanel roomList = panel(roomListLayout);
        private List<RoomSnapshot> rooms = List.of();
        private boolean updating;
        private RoomSnapshot displayed;

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            MonitorArtwork.background(g, getWidth(), getHeight());
            g.dispose();
        }

        Dashboard() {
            super(new BorderLayout(0, 12));
            setBackground(BACKGROUND);
            setBorder(new EmptyBorder(18, 20, 14, 20));
            JPanel top = panel(new BorderLayout(0, 12));
            JPanel heading = panel(new BorderLayout(16, 0));
            JPanel title = panel(new BorderLayout(16, 0));
            title.add(new JLabel(new MonitorIcon(3, 60, BLUE)), BorderLayout.WEST);
            JPanel text = panel(new GridLayout(2, 1, 0, 0));
            text.add(label("Giám sát trận đấu", 30, new Color(0x4C1D95), true));
            text.add(label("MEMORY GAME   /   Trung tâm điều hành server", 13, MUTED, false));
            title.add(text);
            title.add(MonitorArtwork.illustration(170, 68), BorderLayout.EAST);
            heading.add(title, BorderLayout.CENTER);
            JPanel status = panel(new GridBagLayout());
            JPanel badge = surface(new BorderLayout(), GREEN, false);
            badge.setBorder(new EmptyBorder(10, 15, 10, 15));
            badge.add(serverStatus);
            status.add(badge);
            heading.add(status, BorderLayout.EAST);
            top.add(heading, BorderLayout.NORTH);
            JPanel stats = panel(new GridLayout(1, 4, 8, 0));
            String[] names = {"NGƯỜI ONLINE", "ĐANG GHÉP TRẬN", "PHÒNG HIỆN TẠI", "TRẬN ĐANG CHƠI"};
            Color[] accents = {BLUE, AMBER, AppTheme.BLUE, GREEN};
            for (int i = 0; i < names.length; i++) {
                JPanel card = surface(new BorderLayout(14, 0), new Color(accents[i].getRed(), accents[i].getGreen(), accents[i].getBlue(), 145), false);
                card.setBorder(new EmptyBorder(12, 16, 12, 16));
                card.add(new JLabel(new MonitorIcon(i, 46, accents[i])), BorderLayout.WEST);
                JPanel info = panel(new BorderLayout(0, 2));
                info.add(label(names[i], 11, MUTED, true), BorderLayout.NORTH);
                metrics[i] = label("0", 28, accents[i], true);
                info.add(metrics[i]);
                card.add(info);
                stats.add(card);
            }
            top.add(stats, BorderLayout.CENTER);
            add(top, BorderLayout.NORTH);

            JPanel roomPanel = surface(new BorderLayout(0, 12));
            roomPanel.setPreferredSize(new Dimension(290, 0));
            JPanel roomHeading = new JPanel(new BorderLayout(0, 8)) {
                @Override protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setPaint(new GradientPaint(0, 0, new Color(0x6743D4), getWidth(), getHeight(), new Color(0xA87BEE)));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18); g.dispose();
                }
            };
            roomHeading.setOpaque(false);
            roomHeading.setBorder(new EmptyBorder(14, 12, 14, 12));
            roomCount.setForeground(Color.WHITE);
            JPanel headingRow = panel(new BorderLayout(6, 0));
            headingRow.add(label("Danh sách phòng", 18, Color.WHITE, true), BorderLayout.CENTER);
            headingRow.add(roomCount, BorderLayout.EAST);
            roomHeading.add(headingRow, BorderLayout.NORTH);
            roomHeading.add(label("Chọn phòng để xem chi tiết", 12, Color.WHITE, false), BorderLayout.SOUTH);
            roomPanel.add(roomHeading, BorderLayout.NORTH);
            roomsList.setOpaque(false);
            roomsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            roomsList.setFixedCellHeight(92);
            roomsList.setCellRenderer((list, room, index, selected, focus) -> roomCard(room, selected, focus));
            roomsList.getAccessibleContext().setAccessibleName("Danh sách phòng đang hoạt động");
            roomsList.addListSelectionListener(e -> {
                if (!updating && !e.getValueIsAdjusting()) showRoom();
            });
            JScrollPane scroll = new JScrollPane(roomsList);
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.getVerticalScrollBar().setUnitIncrement(24);
            scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
            roomList.add(scroll, "list");
            JPanel empty = panel(new GridBagLayout());
            JPanel emptyContent = panel(new BorderLayout(0, 12));
            emptyContent.add(MonitorArtwork.illustration(240, 165), BorderLayout.NORTH);
            empty.add(emptyContent);
            roomList.add(empty, "empty");
            roomListLayout.show(roomList, "empty");
            roomPanel.add(roomList);

            JPanel inspection = surface(new BorderLayout(0, 12));
            JPanel detailHeading = panel(new BorderLayout(12, 0));
            JLabel detailTitle = label("Chi tiết trận đấu", 21, INK, true);
            detailTitle.setIcon(new MonitorIcon(3, 30, AppTheme.SECONDARY));
            detailTitle.setIconTextGap(10);
            detailHeading.add(detailTitle, BorderLayout.WEST);
            JPanel detailStatus = panel(new GridLayout(2, 1, 0, 3));
            live.setHorizontalAlignment(SwingConstants.RIGHT);
            roomSubtitle.setHorizontalAlignment(SwingConstants.RIGHT);
            detailStatus.add(live);
            detailStatus.add(roomSubtitle);
            detailHeading.add(detailStatus);
            inspection.add(detailHeading, BorderLayout.NORTH);
            JPanel match = panel(new BorderLayout(12, 0));

            side.setPreferredSize(new Dimension(215, 0));
            side.add(players);
            JPanel completion = surface(new BorderLayout(0, 6));
            completion.setBorder(new EmptyBorder(13, 16, 15, 16));
            completion.add(label("Tiến độ ghép cặp", 13, MUTED, false), BorderLayout.NORTH);
            completion.add(progressText);
            progress.setUI(new javax.swing.plaf.basic.BasicProgressBarUI());
            progress.setBorderPainted(false);
            progress.setForeground(GREEN);
            progress.setBackground(LINE);
            progress.setPreferredSize(new Dimension(0, 10));
            completion.add(progress, BorderLayout.SOUTH);
            JPanel sideBottom = panel(new BorderLayout(0, 8));
            sideBottom.add(completion, BorderLayout.NORTH);
            chat.setPreferredSize(new Dimension(0, 160));

            side.add(sideBottom, BorderLayout.SOUTH);
            match.add(side, BorderLayout.WEST);
            JPanel game = panel(new BorderLayout(0, 6));
            JPanel turnBar = surface(new BorderLayout(8, 0));
            turnBar.setBorder(new EmptyBorder(10, 14, 10, 14));
            turnBar.add(turn);
            turnBar.add(clock, BorderLayout.EAST);
            game.add(turnBar, BorderLayout.NORTH);
            game.add(board);
            match.add(game);
            inspection.add(match);
            JPanel legend = panel(new GridLayout(1, 3, 8, 0));
            legend.setBorder(new EmptyBorder(10, 12, 10, 12));
            legend.add(legend("?", "Đang úp", "Chưa mở thẻ", BLUE, 0));
            legend.add(legend("◉", "Đang lật", "Ảnh theo chủ đề", AMBER, 1));
            legend.add(legend("✓", "Đã ghép", "Màu của người ghép", GREEN, 2));
            JPanel lower = panel(new BorderLayout(0, 6));
            lower.add(legend, BorderLayout.NORTH);
            chat.setPreferredSize(new Dimension(0, 100));
            lower.add(chat, BorderLayout.CENTER);

            inspection.add(lower, BorderLayout.SOUTH);
            JPanel main = panel(new BorderLayout(8, 0));
            main.add(roomPanel, BorderLayout.WEST);
            main.add(inspection);
            add(main);
            JLabel footer = label("↻   Tự cập nhật mỗi 0,5 giây   •   Chỉ quan sát   •   Đóng cửa sổ này vẫn giữ server chạy", 12, MUTED, false);
            footer.setBorder(new EmptyBorder(4, 8, 2, 8));
            add(footer, BorderLayout.SOUTH);
        }

        private JPanel legend(String symbol, String title, String description, Color color, int index) {
            JPanel result = surface(new BorderLayout(8, 0));
            result.setBorder(new EmptyBorder(9, 10, 10, 10));
            cardCounts[index] = label("0", 23, color, true);
            result.add(cardCounts[index], BorderLayout.EAST);
            JLabel icon = label(symbol, 22, color, true);
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            icon.setPreferredSize(new Dimension(30, 34));
            result.add(icon, BorderLayout.WEST);
            JPanel text = panel(new GridLayout(2, 1, 0, 3));
            text.add(label(title, 12, INK, true));
            text.add(label(description, 11, MUTED, false));
            result.add(text);
            return result;
        }

        private JPanel roomCard(RoomSnapshot room, boolean selected, boolean focus) {
            JPanel card = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D p = (Graphics2D) g.create();
                    p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    p.setPaint(new GradientPaint(0, 0, selected ? AppTheme.SELECTED : AppTheme.SURFACE,
                            getWidth(), getHeight(), AppTheme.SURFACE));
                    p.fillRoundRect(4, 4, getWidth() - 9, getHeight() - 9, 14, 14);
                    p.setColor(selected ? BLUE : focus ? MUTED : LINE);
                    p.setStroke(new BasicStroke(selected ? 1.6f : 1f));
                    p.drawRoundRect(4, 4, getWidth() - 9, getHeight() - 9, 14, 14);
                    new MonitorIcon(1, 42, selected ? BLUE : MUTED).paintIcon(this, p, 14, 24);
                    p.setColor(INK);
                    p.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
                    p.drawString(room.code(), 68, 28);
                    p.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                    p.setColor(MUTED);
                    p.drawString(room.boardSize() + " × " + room.boardSize() + "  ·  " + room.players().size() + " người", 68, 48);
                    p.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                    p.setColor(!room.current().isEmpty() ? GREEN : room.cards().isEmpty() ? AMBER : MUTED);
                    p.drawString("● " + room.status(), 68, 68);
                    p.dispose();
                }
            };
            card.setOpaque(false);
            card.setToolTipText(room.code() + " · " + (room.matchmaking() ? "Ghép tự động" : "Phòng riêng") + " · " + room.status());
            return card;
        }

        void render(MonitorSnapshot snapshot) {
            RoomSnapshot selected = roomsList.getSelectedValue();
            String code = selected == null ? "" : selected.code();
            serverStatus.setText((snapshot.running() ? "●  Server đang chạy" : "●  Server chưa chạy") + "  ·  Cổng " + snapshot.port());
            serverStatus.setForeground(snapshot.running() ? GREEN : AMBER);
            long playing = snapshot.rooms().stream().filter(r -> !r.current().isEmpty()).count();
            long[] values = {snapshot.online(), snapshot.queued(), snapshot.rooms().size(), playing};
            for (int i = 0; i < values.length; i++) metrics[i].setText(String.valueOf(values[i]));
            roomCount.setText(snapshot.rooms().size() + " phòng");
            if (!rooms.equals(snapshot.rooms())) {
                updating = true;
                rooms = snapshot.rooms();
                int next = -1;
                for (int i = 0; i < rooms.size(); i++) {
                    RoomSnapshot room = rooms.get(i);
                    if (i >= roomsModel.size()) roomsModel.addElement(room);
                    else if (!room.equals(roomsModel.get(i))) roomsModel.set(i, room);
                    if (room.code().equals(code)) next = i;
                }
                while (roomsModel.size() > rooms.size()) roomsModel.remove(roomsModel.size() - 1);
                if (next < 0 && !rooms.isEmpty()) next = 0;
                roomsList.setSelectedIndex(next);
                updating = false;
            }
            roomListLayout.show(roomList, rooms.isEmpty() ? "empty" : "list");
            showRoom();
        }

        private void showRoom() {
            RoomSnapshot room = roomsList.getSelectedValue();
            chat.setMessages(room == null ? List.of() : room.chat());
            boolean showPlayers = room != null;
            if (side.isVisible() != showPlayers) { side.setVisible(showPlayers); side.getParent().revalidate(); }
            int[] counts = new int[3];
            if (room != null) for (CardSnapshot card : room.cards()) {
                if (card.state() >= 0 && card.state() < counts.length) counts[card.state()]++;
            }
            for (int i = 0; i < counts.length; i++) cardCounts[i].setText(String.valueOf(counts[i]));
            if (room == null) {
                roomSubtitle.setText("");
                live.setText("");
                live.setForeground(MUTED);
                turn.setText("Chưa có trận đấu");
                clock.setText("—");
                progressText.setText("0 / 0 cặp");
                progress.setValue(0);
            } else {
                roomSubtitle.setText("Phòng: " + room.code() + "   •   Bàn: " + room.boardSize() + " × " + room.boardSize());
                live.setText(room.current().isEmpty() ? room.status().toUpperCase(java.util.Locale.ROOT) : "●  TRỰC TIẾP");
                live.setForeground(room.current().isEmpty() ? MUTED : AppTheme.SECONDARY);
                long seconds = Math.max(0, (room.deadline() - System.currentTimeMillis() + 999) / 1000);
                clock.setText(room.resolving() ? "Đang xử lý" : room.deadline() > 0 ? seconds + " giây" : "—");
                clock.setForeground(!room.current().isEmpty() && !room.resolving() && seconds <= 5 ? AMBER : BLUE);
                turn.setText(room.current().isEmpty() ? "Chờ trận đấu" : "Lượt: " + room.current());
                turn.setToolTipText(turn.getText());
                int total = room.boardSize() * room.boardSize() / 2;
                progressText.setText(room.pairs() + " / " + total + " cặp");
                progress.setMaximum(total);
                progress.setValue(room.pairs());
            }
            if (!java.util.Objects.equals(displayed, room)) {
                // A turn/card update does not need to recreate unchanged player widgets.
                if (room == null || displayed == null || !room.code().equals(displayed.code())
                        || !room.players().equals(displayed.players()) || !room.current().equals(displayed.current())
                        || room.cards().isEmpty() != displayed.cards().isEmpty()) {
                    players.removeAll();
                    if (room != null) for (PlayerSnapshot player : room.players()) players.add(playerCard(player, room));
                    players.revalidate();
                    players.repaint();
                }
                displayed = room;
                board.room = room;
                board.repaint();
            }
        }

        private JPanel playerCard(PlayerSnapshot player, RoomSnapshot room) {
            boolean active = player.name().equals(room.current());
            boolean compact = room.players().size() >= 3;
            Color ownerColor = playerColors(room).getOrDefault(player.name(), GameColors.UNASSIGNED);
            JPanel card = surface(new BorderLayout(9, 0), ownerColor, active);
            card.setBorder(new EmptyBorder(compact ? 7 : 9, 12, compact ? 7 : 9, 12));
            card.add(new JLabel(new MonitorIcon(0, compact ? 28 : 40, ownerColor)), BorderLayout.WEST);
            JPanel info = panel(new GridBagLayout());
            JPanel stack = panel(new BorderLayout(0, 5));
            JLabel name = label(playerCode(room, player.name()) + " · " + player.name(), 12, INK, true);
            name.setToolTipText(player.name());
            String state = player.forfeited() ? "Bỏ cuộc" : active ? "Đang tới lượt"
                    : room.cards().isEmpty() ? (player.ready() ? "Sẵn sàng" : "Chưa sẵn sàng") : "Online";
            if (!player.connected()) state = "Mất kết nối" + (player.forfeited() ? " · Bỏ cuộc" : "");
            JLabel status = label(state, 10, player.connected() ? MUTED : AMBER, false);
            status.setToolTipText(state);
            if (compact) {
                JPanel row = panel(new BorderLayout(5, 0));
                row.add(name);
                row.add(label(String.valueOf(player.score()), 22, INK, true), BorderLayout.EAST);
                stack.add(row, BorderLayout.CENTER);
            } else {
                stack.add(name, BorderLayout.NORTH);
                stack.add(label(player.score() + " điểm", 26, INK, true));
            }
            stack.add(status, BorderLayout.SOUTH);
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.weightx = 1;
            info.add(stack, constraints);
            card.add(info);
            card.setToolTipText(player.name() + " · " + player.score() + " điểm");
            return card;
        }
    }

    /** Draw in place instead of recreating all tiles on each refresh. */
    private static final class Board extends JPanel {
        private RoomSnapshot room;

        Board() {
            setOpaque(false);
            setToolTipText("");
            setPreferredSize(new Dimension(480, 480));
            getAccessibleContext().setAccessibleName("Bàn thẻ theo dõi trận đấu");
        }

        private Rectangle boardBounds() {
            int size = Math.max(0, Math.min(getWidth() - 16, getHeight() - 16));
            return new Rectangle((getWidth() - size) / 2, (getHeight() - size) / 2, size, size);
        }

        private Rectangle cardBounds(int index) {
            Rectangle area = boardBounds();
            int n = room.boardSize();
            double stepX = area.width / (double) n, stepY = area.height / (double) n;
            int gap = Math.min(n == 4 ? 18 : n == 6 ? 14 : 10,
                    Math.max(4, (int) (Math.min(stepX, stepY) * .18)));
            return new Rectangle(area.x + (int) ((index % n) * stepX) + gap / 2,
                    area.y + (int) ((index / n) * stepY) + gap / 2,
                    Math.max(1, (int) stepX - gap), Math.max(1, (int) stepY - gap));
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D graphics = (Graphics2D) g.create();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (room == null || room.cards().isEmpty()) {
                graphics.setPaint(new GradientPaint(0, 0, new Color(0xF8F2FF), getWidth(), getHeight(), new Color(0xE8D9FC)));
                graphics.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                graphics.setColor(LINE);
                graphics.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                double scale = Math.min(1.0, Math.min(getWidth() / 360.0, Math.max(.1, (getHeight() - 70) / 190.0)));
                int centerY = Math.max(35, (getHeight() - 65) / 2);
                MonitorArtwork.cards(graphics, getWidth() / 2, centerY, scale);
                int titleY = Math.min(getHeight() - 35, centerY + (int) (100 * scale) + 18);
                graphics.setColor(INK);
                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
                center(graphics, room == null ? "Chưa có trận đấu" : "Chờ người chơi sẵn sàng", getWidth() / 2, titleY);
                graphics.setColor(MUTED);
                graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                center(graphics, "Bàn thẻ hiển thị khi bắt đầu trận", getWidth() / 2, titleY + 24);
            } else {
                Rectangle area = boardBounds();
                graphics.setPaint(new GradientPaint(area.x, area.y, new Color(0xEDE3FF),
                        area.x + area.width, area.y + area.height, new Color(0xCDBAF3)));
                graphics.fillRoundRect(area.x - 4, area.y - 4, area.width + 8, area.height + 8, 18, 18);
                Map<String, Color> colors = playerColors(room);
                for (int i = 0; i < room.cards().size(); i++) {
                    CardSnapshot card = room.cards().get(i);
                    Rectangle tile = cardBounds(i);
                    int x = tile.x, y = tile.y, width = tile.width, height = tile.height;
                    int size = Math.min(width, height);
                    Color face = card.state() == 0 ? AppTheme.PRIMARY
                            : card.state() == 1 ? GameColors.forValue(card.value())
                            : colors.getOrDefault(card.owner(), GameColors.UNASSIGNED);
                    graphics.setColor(new Color(57, 28, 96, 65));
                    graphics.fillRoundRect(x + 2, y + 4, width, height, 14, 14);
                    if (card.state() == 0) {
                        Shape oldClip = graphics.getClip();
                        graphics.clip(new java.awt.geom.RoundRectangle2D.Double(x, y, width, height, 14, 14));
                        memorygame.common.CardArtwork.paintBack(graphics, x, y, width, height);
                        graphics.setClip(oldClip);
                        for (int glow = 3; glow >= 1; glow--) {
                            graphics.setStroke(new BasicStroke(glow * 2f));
                            graphics.setColor(new Color(241, 180, 255, 35));
                            graphics.drawRoundRect(x, y, width, height, 14, 14);
                        }
                        graphics.setStroke(new BasicStroke(1.5f));
                        graphics.setColor(new Color(255, 231, 175));
                        graphics.drawRoundRect(x + 1, y + 1, width - 2, height - 2, 12, 12);
                        graphics.setColor(new Color(248, 215, 255, 180));
                        graphics.drawRoundRect(x + 4, y + 4, Math.max(1, width - 8), Math.max(1, height - 8), 10, 10);
                    } else {
                        graphics.setColor(face);
                        graphics.fillRoundRect(x, y, width, height, 12, 12);
                        graphics.setStroke(new BasicStroke(1.5f));
                        graphics.setColor(face.darker());
                        graphics.drawRoundRect(x, y, width - 1, height - 1, 12, 12);
                    }
                    if (card.state() == 2) {
                        paintMatchedCard(graphics, card, tile, face);
                    } else if (card.state() == 1) {
                        paintCardImage(graphics, card.value(), x + 6, y + 6, width - 12, height - 12);
                    } else {
                        graphics.setColor(AppTheme.ON_ACCENT);
                        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, Math.min(36, size / 3))));
                        center(graphics, "?", x + width / 2, y + height / 2 + graphics.getFontMetrics().getAscent() / 3);
                    }
                }
            }
            graphics.dispose();
        }

        private void paintMatchedCard(Graphics2D g, CardSnapshot card, Rectangle tile, Color ownerColor) {
            Graphics2D p = (Graphics2D) g.create();
            p.clip(tile);
            int x = tile.x, y = tile.y, w = tile.width, h = tile.height;
            String code = playerCode(room, card.owner());
            p.setStroke(new BasicStroke(6f));
            p.setColor(Color.WHITE);
            p.drawRoundRect(x + 3, y + 3, Math.max(1, w - 6), Math.max(1, h - 6), 10, 10);
            p.setStroke(new BasicStroke(4f));
            p.setColor(ownerColor);
            p.drawRoundRect(x + 3, y + 3, Math.max(1, w - 6), Math.max(1, h - 6), 10, 10);
            p.setColor(GameColors.textOn(ownerColor));
            {
                int strip = Math.min(22, Math.max(14, h / 4));
                p.setColor(AppTheme.SURFACE);
                p.fillRoundRect(x + 7, y + 7, Math.max(1, w - 14), Math.max(1, h - strip - 10), 6, 6);
                paintCardImage(p, card.value(), x + 7, y + 7, w - 14, h - strip - 10);
                p.setColor(GameColors.textOn(ownerColor));
                p.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                String label = code;
                center(p, label, x + w / 2, y + h - strip + (strip - p.getFontMetrics().getHeight()) / 2
                        + p.getFontMetrics().getAscent() - 2);
            }
            p.dispose();
        }

        private void paintCardImage(Graphics2D g, int value, int x, int y, int width, int height) {
            if (value < 1 || value > 32 || width <= 0 || height <= 0) return;
            CardTheme theme;
            try { theme = CardTheme.valueOf(room.cardTheme()); }
            catch (IllegalArgumentException | NullPointerException e) { theme = CardTheme.FRUIT; }
            CardTheme.paintFace(g, theme.face(value), getFont(), x, y, width, height);
        }

        private static void center(Graphics2D g, String text, int x, int baseline) {
            g.drawString(text, x - g.getFontMetrics().stringWidth(text) / 2, baseline);
        }

        @Override public String getToolTipText(MouseEvent event) {
            if (room == null || room.cards().isEmpty()) return null;
            Rectangle area = boardBounds();
            if (!area.contains(event.getPoint())) return null;
            int column = (int) ((event.getX() - area.x) * (double) room.boardSize() / area.width);
            int row = (int) ((event.getY() - area.y) * (double) room.boardSize() / area.height);
            int index = row * room.boardSize() + column;
            if (!cardBounds(index).contains(event.getPoint())) return null;
            CardSnapshot card = room.cards().get(index);
            return "Ô " + (index + 1) + (card.state() == 0 ? " · Đang úp" : " · Giá trị " + card.value())
                    + (card.owner() == null ? "" : " · Ghép bởi " + playerCode(room, card.owner()) + " · " + card.owner());
        }
    }
}
