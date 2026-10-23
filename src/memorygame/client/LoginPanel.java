package memorygame.client;

import memorygame.common.AppTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** Presentation only; authentication callbacks are owned by GameFrame. */
final class LoginPanel extends JPanel {
    static final Color INK = AppTheme.TEXT;
    static final Color MUTED = AppTheme.MUTED;
    static final Color CYAN = AppTheme.PRIMARY;
    static final Color PURPLE = AppTheme.SECONDARY;
    final JTextField host = new JTextField("localhost");
    final JTextField port = new JTextField("12345");
    final JTextField username = new JTextField() {
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && !hasFocus()) {
                Graphics2D p = graphics(g);
                p.setFont(getFont()); p.setColor(MUTED);
                p.drawString("Nhập tên tài khoản", getInsets().left,
                        (getHeight() - p.getFontMetrics().getHeight()) / 2 + p.getFontMetrics().getAscent());
                p.dispose();
            }
        }
    };
    final JPasswordField password = new JPasswordField();
    final JButton login = new NeonButton("Đăng nhập   →", true);
    final JLabel status = new JLabel(" ");

    LoginPanel(Runnable signIn, Runnable register) {
        super(new BorderLayout());
        setBackground(AppTheme.BACKGROUND);
        setBorder(new EmptyBorder(32, 34, 32, 34));
        JPanel shell = new JPanel(new GridLayout(1, 2)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D p = graphics(g);
                p.setPaint(new GradientPaint(0, 0, AppTheme.SURFACE, getWidth(), getHeight(), AppTheme.SURFACE));
                p.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 32, 32);
                p.setPaint(new GradientPaint(0, 0, AppTheme.BORDER, getWidth(), getHeight(), AppTheme.BORDER));
                p.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 32, 32);
                p.setColor(AppTheme.BORDER);
                p.drawLine(getWidth() / 2, 2, getWidth() / 2, getHeight() - 3);
                p.dispose();
            }
        };
        shell.setOpaque(false);
        shell.add(new Artwork());
        JPanel right = transparent(new GridBagLayout());
        right.setBorder(new EmptyBorder(22, 36, 22, 36));
        JPanel form = transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        addRow(form, c, text("Chào mừng trở lại", 29, INK, true), 0, 9);
        addRow(form, c, text("Đăng nhập để bắt đầu trận đấu của bạn.", 14, MUTED, false), 1, 22);
        JLabel usernameLabel = text("Tài khoản", 13, INK, true);
        usernameLabel.setLabelFor(username);
        addRow(form, c, usernameLabel, 2, 7);
        styleInput(username); styleInput(password);
        username.getAccessibleContext().setAccessibleName("Tài khoản");
        password.getAccessibleContext().setAccessibleName("Mật khẩu");
        username.setToolTipText("Nhập tên tài khoản đã đăng ký");
        addRow(form, c, input(username, false), 3, 17);
        JLabel passwordLabel = text("Mật khẩu", 13, INK, true);
        passwordLabel.setLabelFor(password);
        addRow(form, c, passwordLabel, 4, 7);
        addRow(form, c, input(password, true), 5, 21);
        login.addActionListener(e -> signIn.run());
        username.addActionListener(e -> { if (login.isEnabled()) login.doClick(); });
        password.addActionListener(e -> { if (login.isEnabled()) login.doClick(); });
        addRow(form, c, login, 6, 3);
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        status.setForeground(MUTED);
        status.setPreferredSize(new Dimension(0, 30));
        status.addPropertyChangeListener("text", e -> status.setToolTipText(status.getText()));
        addRow(form, c, status, 7, 4);
        JPanel divider = transparent(new BorderLayout(10, 0));
        JLabel question = text("Chưa có tài khoản?", 12, MUTED, false);
        question.setHorizontalAlignment(SwingConstants.CENTER);
        divider.add(question);
        addRow(form, c, divider, 8, 12);
        JButton create = new NeonButton("Tạo tài khoản mới", false);
        create.addActionListener(e -> register.run());
        addRow(form, c, create, 9, 9);
        GridBagConstraints fill = new GridBagConstraints();
        fill.fill = GridBagConstraints.HORIZONTAL; fill.weightx = 1;
        right.add(form, fill);
        shell.add(right);
        add(shell);
    }

    static JPanel authSurface(LayoutManager layout) {
        JPanel surface = new JPanel(layout) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D p = graphics(g);
                p.setPaint(new GradientPaint(0, 0, AppTheme.SURFACE, getWidth(), getHeight(), AppTheme.SURFACE));
                p.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 28, 28);
                p.setColor(AppTheme.BORDER);
                p.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 28, 28);
                p.dispose();
            }
        };
        surface.setOpaque(false);
        return surface;
    }

    static void styleRegistrationInput(JTextField field) {
        styleInput(field);
        field.setOpaque(true);
        field.setBackground(AppTheme.INSET);
        Runnable updateBorder = () -> field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(field.hasFocus() ? CYAN : AppTheme.BORDER),
                new EmptyBorder(9, 11, 9, 11)));
        updateBorder.run();
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { updateBorder.run(); }
            @Override public void focusLost(java.awt.event.FocusEvent e) { updateBorder.run(); }
        });
    }

    private static void addRow(JPanel panel, GridBagConstraints c, Component component, int row, int after) {
        c.gridy = row; c.insets = new Insets(0, 0, after, 0); panel.add(component, c);
    }
    private static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout); panel.setOpaque(false); return panel;
    }
    private static JLabel text(String value, int size, Color color, boolean bold) {
        JLabel label = new JLabel(value);
        label.setForeground(color); label.setFont(new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, size));
        return label;
    }
    private static void styleInput(JTextField field) {
        field.setOpaque(false); field.setForeground(INK); field.setCaretColor(CYAN);
        field.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        field.setBorder(new EmptyBorder(0, 0, 0, 0));
        field.setSelectionColor(AppTheme.PRIMARY); field.setSelectedTextColor(Color.WHITE);
    }
    private JPanel input(JTextField field, boolean secret) {
        JPanel box = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D p = graphics(g);
                p.setPaint(new GradientPaint(0, 0, AppTheme.INSET, getWidth(), getHeight(), AppTheme.INSET));
                p.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 14, 14);
                p.setColor(field.hasFocus() ? CYAN : AppTheme.BORDER);
                p.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 14, 14);
                p.dispose();
            }
        };
        box.setOpaque(false); box.setBorder(new EmptyBorder(10, 14, 10, 14));
        box.setPreferredSize(new Dimension(0, 49));
        JLabel icon = new JLabel(new FieldIcon(secret ? 1 : 0));
        box.add(icon, BorderLayout.WEST); box.add(field);
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { box.repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent e) { box.repaint(); }
        });
        if (secret) {
            char echo = password.getEchoChar();
            JToggleButton reveal = new JToggleButton(new FieldIcon(2));
            reveal.setContentAreaFilled(false); reveal.setBorder(new EmptyBorder(0, 2, 0, 2));
            reveal.setToolTipText("Hiện mật khẩu");
            reveal.getAccessibleContext().setAccessibleName("Hiện mật khẩu");
            reveal.addActionListener(e -> {
                password.setEchoChar(reveal.isSelected() ? (char) 0 : echo);
                String title = reveal.isSelected() ? "Ẩn mật khẩu" : "Hiện mật khẩu";
                reveal.setToolTipText(title); reveal.getAccessibleContext().setAccessibleName(title);
            });
            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()) {
                    reveal.setSelected(false); password.setEchoChar(echo);
                    reveal.setToolTipText("Hiện mật khẩu"); reveal.getAccessibleContext().setAccessibleName("Hiện mật khẩu");
                }
            });
            box.add(reveal, BorderLayout.EAST);
        }
        return box;
    }
    private static Graphics2D graphics(Graphics g) {
        Graphics2D p = (Graphics2D) g.create();
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        p.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return p;
    }
    private static final class FieldIcon implements Icon {
        private final int type;
        FieldIcon(int type) { this.type = type; }
        public int getIconWidth() { return 20; }
        public int getIconHeight() { return 22; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D p = graphics(g); p.translate(x, y); p.setColor(AppTheme.MUTED); p.setStroke(new BasicStroke(2));
            if (type == 0) { p.fillOval(6, 2, 9, 9); p.fillRoundRect(3, 13, 15, 8, 5, 5); }
            else if (type == 1) { p.drawRoundRect(6, 2, 9, 12, 8, 8); p.fillRoundRect(3, 10, 15, 11, 3, 3); }
            else { p.drawOval(1, 6, 18, 11); p.fillOval(7, 9, 6, 6); }
            p.dispose();
        }
    }
    static final class NeonButton extends JButton {
        private final boolean filled;
        NeonButton(String title, boolean filled) {
            super(title); this.filled = filled;
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
            setForeground(filled ? AppTheme.ON_ACCENT : INK); setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            setPreferredSize(new Dimension(0, 49)); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p = graphics(g);
            if (!isEnabled()) p.setComposite(AlphaComposite.SrcOver.derive(0.45f));
            p.setColor(CYAN);
            if (filled) p.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 14, 14);
            else p.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 14, 14);
            if (getModel().isRollover() || getModel().isPressed()) {
                p.setColor(new Color(255, 255, 255, getModel().isPressed() ? 45 : 20));
                p.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 14, 14);
            }
            if (hasFocus()) { p.setColor(filled ? AppTheme.ON_ACCENT : INK); p.drawRoundRect(4, 4, getWidth() - 9, getHeight() - 9, 10, 10); }
            p.dispose(); super.paintComponent(g);
        }
    }
    static final class Artwork extends JPanel {
        Artwork() { setOpaque(false); getAccessibleContext().setAccessibleName("Memory Game — Lật thẻ. Kết nối. Chinh phục. 2–4 người chơi"); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p = graphics(g);
            p.clip(new RoundRectangle2D.Double(2, 2, getWidth() - 3, getHeight() - 4, 30, 30));
            p.scale(getWidth() / 450.0, getHeight() / 540.0);
            p.setColor(AppTheme.INSET);
            p.fillRect(0, 0, 450, 540);
            p.setPaint(new GradientPaint(190, 40, CYAN, 260, 96, PURPLE));
            p.setStroke(new BasicStroke(3)); p.drawRoundRect(188, 39, 74, 62, 14, 14);
            p.setColor(INK); p.fillRoundRect(201, 58, 48, 29, 14, 14);
            p.setColor(AppTheme.INSET); p.fillRect(207, 69, 16, 4); p.fillRect(213, 63, 4, 16);
            p.fillOval(233, 65, 5, 5); p.fillOval(239, 73, 5, 5);
            p.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34)); p.setColor(INK);
            center(p, "MEMORY GAME", 225, 149);
            p.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17)); p.setColor(MUTED);
            center(p, "Lật thẻ. Kết nối. Chinh phục.", 225, 181);
            p.setColor(AppTheme.SELECTED); p.fillRoundRect(143, 202, 164, 34, 30, 30);
            p.setColor(CYAN); p.setStroke(new BasicStroke(1)); p.drawRoundRect(143, 202, 164, 34, 30, 30);
            p.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13)); center(p, "2–4 người chơi", 225, 224);
            card(p, 30, 350, -0.28, AppTheme.GOLD_TEXT, false, 0.85);
            card(p, 413, 378, 0.2, AppTheme.MUTED, true, 0.88);
            card(p, 170, 375, -0.16, CYAN, false, 1.0);
            card(p, 297, 411, 0.23, PURPLE, true, 1.0);
            p.dispose();
        }
        private static void center(Graphics2D p, String s, int x, int y) { p.drawString(s, x - p.getFontMetrics().stringWidth(s) / 2, y); }
        static void card(Graphics2D parent, int x, int y, double angle, Color color, boolean bolt, double scale) {
            Graphics2D p = (Graphics2D) parent.create(); p.translate(x, y); p.rotate(angle); p.scale(scale, scale);
            p.setColor(new Color(109, 40, 217, 24));
            p.fillRoundRect(-52, -72, 112, 156, 12, 12);
            p.setColor(AppTheme.SURFACE);
            p.fillRoundRect(-56, -78, 112, 156, 16, 16);
            p.setStroke(new BasicStroke(2)); p.setColor(color); p.drawRoundRect(-56, -78, 112, 156, 16, 16);
            p.setStroke(new BasicStroke(1)); p.setColor(AppTheme.BORDER); p.drawRoundRect(-50, -72, 100, 144, 12, 12);
            Path2D shape = new Path2D.Double();
            if (bolt) { shape.moveTo(10, -36); shape.lineTo(-26, 8); shape.lineTo(-4, 8); shape.lineTo(-10, 37); shape.lineTo(30, -7); shape.lineTo(6, -7); }
            else for (int i = 0; i < 10; i++) { double a = -Math.PI / 2 + i * Math.PI / 5; double r = i % 2 == 0 ? 33 : 16; if (i == 0) shape.moveTo(Math.cos(a)*r, Math.sin(a)*r); else shape.lineTo(Math.cos(a)*r, Math.sin(a)*r); }
            shape.closePath(); p.setColor(bolt ? PURPLE : AppTheme.GOLD);
            p.fill(shape); p.setColor(new Color(255, 255, 255, 160)); p.draw(shape); p.dispose();
        }
    }
}
