package memorygame.client;

import memorygame.common.Protocol;
import memorygame.common.AppTheme;
import memorygame.common.Protocol.Message;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

/** Room options sent to the server only after confirmation. */
final class CreateRoomPanel extends JPanel {
    private static final Color INK = AppTheme.TEXT, MUTED = AppTheme.MUTED;
    private static final Color FIELD = AppTheme.SURFACE, LINE = AppTheme.BORDER;
    private static final Color ACCENT = AppTheme.PRIMARY;
    private final JTextField name;
    private final JComboBox<CardTheme> theme;
    private int capacity = 2, boardSize = 4;

    CreateRoomPanel(String username, CardTheme initialTheme, int initialSize, Runnable cancel, Consumer<Message> submit) {
        this(username, initialTheme, initialSize, "", cancel, submit);
    }

    CreateRoomPanel(String username, CardTheme initialTheme, int initialSize, String invitee, Runnable cancel, Consumer<Message> submit) {
        super(new BorderLayout(0, 18));
        setOpaque(false);
        setBorder(new EmptyBorder(24, 28, 24, 28));
        setPreferredSize(new Dimension(740, 730));
        JPanel heading = clear(new BorderLayout(16, 0));
        LobbyArtwork icon = new LobbyArtwork(true, false);
        icon.setPreferredSize(new Dimension(112, 84));
        heading.add(icon, BorderLayout.WEST);
        JPanel titles = clear(new GridLayout(2, 1, 0, 4));
        titles.add(text(invitee.isEmpty() ? "TẠO PHÒNG" : "THIẾT LẬP TRẬN ĐẤU", 32, ACCENT, true));
        titles.add(text(invitee.isEmpty() ? "Tùy chỉnh phòng chơi của bạn." : "Tạo phòng và mời " + invitee + " cùng chơi.", 15, MUTED, false));
        heading.add(titles);
        JButton close = button("×", false);
        close.setPreferredSize(new Dimension(42, 42));
        close.getAccessibleContext().setAccessibleName("Đóng tạo phòng");
        close.addActionListener(e -> cancel.run());
        JPanel closeArea = clear(new BorderLayout());
        closeArea.add(close, BorderLayout.NORTH);
        heading.add(closeArea, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);

        JPanel form = clear(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        String defaultName = "Phòng của " + username;
        name = new JTextField(defaultName.substring(0, Math.min(40, defaultName.length())));
        name.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        name.setBackground(FIELD); name.setForeground(INK); name.setCaretColor(ACCENT);
        name.setSelectionColor(AppTheme.SELECTED); name.setSelectedTextColor(INK);
        name.setBorder(new EmptyBorder(12, 16, 12, 16));
        name.setOpaque(false);
        field(form, c, "Tên phòng", rounded(name));
        JPanel counts = clear(new GridLayout(1, 3, 8, 0));
        ButtonGroup countGroup = new ButtonGroup();
        for (int count = 2; count <= 4; count++) {
            final int selected = count;
            JToggleButton choice = choice(count + " người", count == capacity);
            choice.putClientProperty("symbol", "people");
            choice.addActionListener(e -> capacity = selected);
            countGroup.add(choice); counts.add(choice);
        }
        field(form, c, "Số người chơi tối đa", counts);
        theme = new JComboBox<>(CardTheme.values());
        theme.setSelectedItem(initialTheme);
        theme.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                g.setColor(FIELD); g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            @Override protected JButton createArrowButton() {
                JButton arrow = button("⌄", false);
                arrow.setPreferredSize(new Dimension(42, 42));
                return arrow;
            }
        });
        theme.setBorder(new EmptyBorder(2, 5, 2, 5));
        theme.setOpaque(false);
        theme.setBackground(FIELD); theme.setForeground(INK);
        theme.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        theme.setPreferredSize(new Dimension(0, 48));
        theme.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                label.setBackground(selected ? AppTheme.SELECTED : FIELD); label.setForeground(INK);
                label.setOpaque(index >= 0);
                label.setBorder(new EmptyBorder(7, 12, 7, 12)); label.setIconTextGap(12);
                if (value instanceof CardTheme item) label.setIcon(new ImageIcon(item.image(1).getScaledInstance(30, 30, Image.SCALE_SMOOTH)));
                return label;
            }
        });
        field(form, c, "Chủ đề thẻ", rounded(theme));
        JPanel difficulties = clear(new GridLayout(1, 3, 8, 0));
        ButtonGroup difficultyGroup = new ButtonGroup();
        int[] sizes = {4, 6, 8}; String[] labels = {"Dễ · 4×4", "Trung bình · 6×6", "Khó · 8×8"};
        boardSize = initialSize;
        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            JToggleButton choice = choice(labels[i], size == boardSize);
            choice.putClientProperty("symbol", new String[]{"leaf", "bars", "fire"}[i]);
            choice.putClientProperty("description", new String[]{"Dành cho người mới", "Thử thách vừa phải", "Dành cho cao thủ"}[i]);
            choice.setPreferredSize(new Dimension(0, 76));
            choice.addActionListener(e -> boardSize = size);
            difficultyGroup.add(choice); difficulties.add(choice);
        }
        field(form, c, "Độ khó", difficulties);
        JLabel validation = text("Bắt đầu khi có ít nhất 2 người và tất cả đã sẵn sàng.", 12, MUTED, false);
        validation.setIcon(new FormIcon("info", ACCENT, 22)); validation.setIconTextGap(10);
        validation.setOpaque(true); validation.setBackground(AppTheme.SELECTED);
        validation.setBorder(new EmptyBorder(12, 12, 12, 12));
        c.gridy++; c.insets = new Insets(4, 0, 0, 0); form.add(validation, c);
        add(form);
        JPanel actions = clear(new BorderLayout(12, 0));
        actions.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE), new EmptyBorder(20, 0, 0, 0)));
        JButton back = button("Hủy", false), confirm = button(invitee.isEmpty() ? "Xác nhận tạo phòng" : "Tạo phòng và gửi lời mời", true);
        back.setPreferredSize(new Dimension(220, 56));
        back.addActionListener(e -> cancel.run());
        Runnable create = () -> {
            if (!confirm.isEnabled()) return;
            String roomName = name.getText().trim();
            if (roomName.isEmpty() || roomName.length() > 40 || roomName.chars().anyMatch(Character::isISOControl)) {
                validation.setText("Tên phòng phải có từ 1 đến 40 ký tự hợp lệ.");
                validation.setForeground(AppTheme.DANGER); name.requestFocusInWindow(); return;
            }
            confirm.setEnabled(false);
            Message request = Message.of(Protocol.Type.ROOM_CREATE).with("roomName", roomName)
                    .with("capacity", capacity).with("boardSize", boardSize)
                    .with("cardTheme", ((CardTheme) theme.getSelectedItem()).name());
            if (!invitee.isEmpty()) request.with("inviteUsername", invitee);
            submit.accept(request);
        };
        confirm.addActionListener(e -> create.run()); name.addActionListener(e -> create.run());
        actions.add(back, BorderLayout.WEST); actions.add(confirm); add(actions, BorderLayout.SOUTH);
    }

    private static void field(JPanel form, GridBagConstraints c, String caption, JComponent input) {
        c.insets = new Insets(4, 0, 7, 0);
        JLabel label = text(caption, 16, INK, true); label.setLabelFor(input);
        String symbol = caption.equals("Tên phòng") ? "pen" : caption.startsWith("Số") ? "people" : caption.startsWith("Chủ") ? "palette" : "bars";
        label.setIcon(new FormIcon(symbol, ACCENT, 24)); label.setIconTextGap(12);
        form.add(label, c); c.gridy++;
        c.insets = new Insets(0, 0, 12, 0); form.add(input, c); c.gridy++;
    }
    private static JToggleButton choice(String title, boolean selected) {
        JToggleButton button = new JToggleButton(title, selected);
        style(button); button.setPreferredSize(new Dimension(0, 60));
        Runnable update = () -> { button.setBackground(button.isSelected() ? ACCENT : FIELD); button.setForeground(button.isSelected() ? AppTheme.ON_ACCENT : INK); };
        button.addItemListener(e -> update.run()); update.run(); return button;
    }
    private static JButton button(String title, boolean primary) {
        JButton button = new JButton(title); style(button);
        button.setBackground(primary ? ACCENT : FIELD); button.setForeground(primary ? AppTheme.ON_ACCENT : INK); return button;
    }
    private static void style(AbstractButton button) {
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override public void paint(Graphics graphics, JComponent component) {
                AbstractButton b = (AbstractButton) component;
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = b.getWidth(), h = b.getHeight();
                boolean active = b.isSelected() || ACCENT.equals(b.getBackground());
                g.setColor(new Color(109, 40, 217, active ? 28 : 10)); g.fillRoundRect(2, 5, w - 4, h - 6, 18, 18);
                g.setPaint(active ? new GradientPaint(0, 0, new Color(0x9745FA), w, h, ACCENT) : FIELD);
                g.fillRoundRect(1, 1, w - 3, h - 6, 16, 16);
                g.setColor(active ? ACCENT : LINE); g.setStroke(new BasicStroke(1.2f));
                g.drawRoundRect(1, 1, w - 3, h - 6, 16, 16);
                if (b.hasFocus()) { g.setColor(ACCENT); g.drawRoundRect(4, 4, w - 9, h - 12, 12, 12); }
                String symbol = (String) b.getClientProperty("symbol");
                String description = (String) b.getClientProperty("description");
                Color ink = active ? AppTheme.ON_ACCENT : INK;
                g.setFont(b.getFont());
                if (symbol != null) {
                    new FormIcon(symbol, active ? Color.WHITE : symbol.equals("leaf") ? new Color(0x49B85A)
                            : symbol.equals("bars") ? AppTheme.GOLD : symbol.equals("fire") ? AppTheme.ORANGE : ACCENT, 27)
                            .paintIcon(b, g, 12, (h - 30) / 2 - 2);
                    g.setColor(ink);
                    g.drawString(b.getText(), 48, description == null ? (h - 6 - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent() : 28);
                    if (description != null) {
                        g.setFont(b.getFont().deriveFont(Font.PLAIN, 11f));
                        g.setColor(active ? new Color(0xEEE3FF) : MUTED); g.drawString(description, 48, 49);
                    }
                    if (b.isSelected()) {
                        g.setColor(Color.WHITE); g.fillOval(w - 24, 10, 15, 15);
                        g.setColor(ACCENT); g.setStroke(new BasicStroke(2));
                        g.drawLine(w - 20, 17, w - 17, 20); g.drawLine(w - 17, 20, w - 12, 14);
                    }
                } else {
                    g.setColor(ink); FontMetrics fm = g.getFontMetrics();
                    g.drawString(b.getText(), (w - fm.stringWidth(b.getText())) / 2, (h - 6 - fm.getHeight()) / 2 + fm.getAscent());
                }
                g.dispose();
            }
        });
        button.setOpaque(false); button.setContentAreaFilled(false);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14)); button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(10, 8, 10, 8));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    private static JPanel rounded(JComponent input) {
        JPanel panel = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(FIELD); g.fillRoundRect(1, 1, getWidth()-3, getHeight()-3, 16, 16);
                g.setColor(LINE); g.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, 16, 16); g.dispose();
                super.paintComponent(graphics);
            }
        };
        panel.setOpaque(false); panel.setBorder(new EmptyBorder(2, 4, 2, 4)); panel.add(input); return panel;
    }
    private record FormIcon(String kind, Color color, int size) implements Icon {
        public int getIconWidth() { return size; }
        public int getIconHeight() { return size; }
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create(); g.translate(x, y); g.scale(size / 24.0, size / 24.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color); g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (kind) {
                case "people" -> { for(int i=0;i<3;i++) { int xx=i*7; int yy=i==1?1:4; g.fillOval(xx+1,yy,6,6); g.fillRoundRect(xx,yy+8,8,11,4,4); } }
                case "bars" -> { for(int i=0;i<3;i++) g.fillRoundRect(2+i*7,15-i*6,5,7+i*6,3,3); }
                case "leaf" -> { g.rotate(.5,12,12); g.fillOval(5,1,14,20); g.setColor(new Color(0x268C40)); g.drawLine(12,9,12,23); }
                case "fire" -> { java.awt.geom.Path2D path=new java.awt.geom.Path2D.Double(); path.moveTo(13,1); path.curveTo(14,9,25,13,20,20); path.curveTo(13,28,1,20,4,13); path.lineTo(8,6); path.lineTo(9,13); path.closePath(); g.fill(path); }
                case "pen" -> { g.drawPolygon(new int[]{3,5,18,22,9},new int[]{22,15,2,6,20},5); g.drawLine(15,5,19,9); }
                case "palette" -> { g.drawOval(2,2,20,19); for(int i=0;i<3;i++)g.fillOval(5+i*5,5+(i==1?0:3),3,3); g.fillOval(13,14,6,7); }
                default -> { g.drawOval(2,2,20,20); g.fillOval(11,6,2,2); g.drawLine(12,11,12,18); }
            }
            g.dispose();
        }
    }
    private static JPanel clear(LayoutManager layout) { JPanel panel = new JPanel(layout); panel.setOpaque(false); return panel; }
    private static JLabel text(String text, int size, Color color, boolean bold) {
        JLabel label = new JLabel(text); label.setForeground(color); label.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size)); return label;
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, AppTheme.SURFACE, getWidth(), getHeight(), AppTheme.INSET));
        g.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 30, 30);
        g.setComposite(AlphaComposite.SrcOver.derive(0.06f));
        LoginPanel.Artwork.card(g, getWidth() - 150, 95, -0.4, ACCENT, false, 0.7);
        LoginPanel.Artwork.card(g, getWidth() - 65, 135, 0.4, ACCENT, true, 0.7);
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(LINE); g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 30, 30); g.dispose();
        super.paintComponent(graphics);
    }
}
