package memorygame.client;

import memorygame.common.Protocol.Message;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** One-shot invitation with a local monotonic deadline; expiry is silent. */
final class InvitationPanel extends JPanel {
    private static final Color PURPLE = new Color(124, 24, 235);
    private static final Color INK = new Color(27, 38, 76);
    private static final Color MUTED = new Color(117, 115, 154);
    private final JLabel countdown = new JLabel();
    private final JButton accept = new InviteButton("Chấp nhận", true);
    private final JButton decline = new InviteButton("Từ chối", false);
    private final Timer timer;
    private final long deadline;
    private final LongSupplier clock;
    private final Runnable close;
    private final Consumer<Boolean> reply;
    private boolean finished;

    InvitationPanel(Message message, Consumer<Boolean> reply, Runnable close) {
        this(message, reply, close, System::nanoTime);
    }
    InvitationPanel(Message message, Consumer<Boolean> reply, Runnable close, LongSupplier clock) {
        super(new BorderLayout(0, 16));
        this.clock = clock; this.reply = reply; this.close = close;
        long seconds = Math.min(10, message.integer("expiresIn") > 0 ? message.integer("expiresIn") : 10);
        deadline = clock.getAsLong() + seconds * 1_000_000_000L;
        setOpaque(false);
        setPreferredSize(new Dimension(760, 590));
        setBorder(new EmptyBorder(28, 34, 28, 34));
        JPanel top = new JPanel(new BorderLayout(12, 0)); top.setOpaque(false);
        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 4)); heading.setOpaque(false);
        heading.setBorder(new EmptyBorder(8, 8, 26, 0));
        heading.add(label("LỜI MỜI VÀO PHÒNG", 32, PURPLE));
        JLabel subtitle = label(message.string("from") + " mời bạn cùng chơi", 21, MUTED);
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN)); heading.add(subtitle);
        top.add(heading); top.add(new Cards(), BorderLayout.EAST); add(top, BorderLayout.NORTH);
        JPanel details = new JPanel(new GridLayout(0, 1)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D p = graphics(g);
                p.setColor(new Color(255, 255, 255, 210));
                p.fillRoundRect(0, 0, getWidth()-1, getHeight()-1, 30, 30);
                p.setColor(new Color(237, 228, 255));
                p.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 30, 30); p.dispose();
            }
        };
        details.setOpaque(false); details.setBorder(new EmptyBorder(7, 18, 7, 18));
        row(details, "Tên phòng", message.string("roomName"), 0);
        if (!message.string("code").isEmpty()) row(details, "Mã phòng", message.string("code"), 1);
        row(details, "Người chơi", message.integer("playerCount") + " / " + message.integer("capacity"), 2);
        String theme;
        try { theme = CardTheme.valueOf(message.string("cardTheme")).toString(); }
        catch (IllegalArgumentException e) { theme = "Trái cây"; }
        row(details, "Chủ đề", theme, 3);
        int size = message.integer("boardSize");
        row(details, "Độ khó", (size == 4 ? "Dễ" : size == 6 ? "Trung bình" : "Khó") + " · " + size + "×" + size, 4);
        add(details);
        JPanel bottom = new JPanel(new BorderLayout(0, 18)); bottom.setOpaque(false);
        countdown.setForeground(MUTED); countdown.setHorizontalAlignment(SwingConstants.CENTER);
        countdown.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        countdown.setIcon(new DetailIcon(5)); countdown.setIconTextGap(10);
        bottom.add(countdown, BorderLayout.NORTH);
        JPanel actions = new JPanel(new GridLayout(1, 2, 16, 0)); actions.setOpaque(false);
        actions.add(decline); actions.add(accept); actions.setPreferredSize(new Dimension(0, 62)); bottom.add(actions);
        add(bottom, BorderLayout.SOUTH);
        accept.addActionListener(e -> respond(true)); decline.addActionListener(e -> respond(false));
        timer = new Timer(100, e -> tick()); tick();
    }
    private static JLabel label(String value, int size, Color color) {
        JLabel label = new JLabel(); label.putClientProperty("html.disable", true); label.setText(value);
        label.setToolTipText(value); label.setForeground(color); label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size)); return label;
    }
    private static void row(JPanel panel, String key, String value, int icon) {
        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0)); row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(icon == 0 ? 0 : 1, 0, 0, 0, new Color(237, 228, 255)),
                new EmptyBorder(5, 0, 5, 0)));
        JLabel name = label(key, 20, MUTED); name.setFont(name.getFont().deriveFont(Font.PLAIN));
        name.setIcon(new DetailIcon(icon)); name.setIconTextGap(20);
        row.add(name); row.add(label(value, 20, INK)); panel.add(row);
    }
    private static Graphics2D graphics(Graphics g) {
        Graphics2D p = (Graphics2D) g.create();
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return p;
    }
    @Override protected void paintComponent(Graphics g) {
        Graphics2D p = graphics(g);
        p.setPaint(new GradientPaint(0, 0, new Color(253, 251, 255), getWidth(), getHeight(), new Color(243, 235, 255)));
        p.fillRect(0, 0, getWidth(), getHeight());
        p.setColor(new Color(225, 203, 255, 65));
        p.fillOval(getWidth()-260, 35, 330, 170); p.dispose();
    }
    private static final class InviteButton extends JButton {
        private final boolean primary;
        InviteButton(String text, boolean primary) {
            super(text); this.primary = primary; setOpaque(false); setContentAreaFilled(false);
            setBorderPainted(false); setFocusPainted(false);
            setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            setForeground(primary ? Color.WHITE : INK); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p = graphics(g);
            if (primary) p.setPaint(new GradientPaint(0, 0, new Color(154, 37, 246), getWidth(), getHeight(), new Color(110, 17, 220)));
            else p.setColor(new Color(255, 255, 255, 180));
            p.fillRoundRect(2, 2, getWidth()-5, getHeight()-5, 24, 24);
            if (getModel().isRollover() || getModel().isPressed()) {
                p.setColor(new Color(124, 24, 235, getModel().isPressed() ? 45 : 18));
                p.fillRoundRect(2, 2, getWidth()-5, getHeight()-5, 24, 24);
            }
            p.setStroke(new BasicStroke(1.6f)); p.setColor(PURPLE);
            p.drawRoundRect(2, 2, getWidth()-5, getHeight()-5, 24, 24);
            if (hasFocus()) { p.setColor(primary ? Color.WHITE : PURPLE); p.drawRoundRect(6, 6, getWidth()-13, getHeight()-13, 19, 19); }
            p.dispose(); super.paintComponent(g);
        }
    }
    private static final class Cards extends JComponent {
        Cards() { setPreferredSize(new Dimension(174, 110)); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p = graphics(g);
            card(p, 58, 55, -.27, false); card(p, 121, 54, .23, true); p.dispose();
        }
        private static void card(Graphics2D parent, int x, int y, double angle, boolean bolt) {
            Graphics2D p = (Graphics2D) parent.create(); p.translate(x, y); p.rotate(angle);
            p.setColor(new Color(157, 98, 230, 22)); p.fillRoundRect(-32, -44, 72, 99, 10, 10);
            p.setColor(Color.WHITE); p.fillRoundRect(-35, -48, 70, 96, 9, 9);
            p.setStroke(new BasicStroke(2.5f)); p.setColor(bolt ? new Color(38, 137, 151) : PURPLE);
            p.drawRoundRect(-35, -48, 70, 96, 9, 9);
            Path2D shape = new Path2D.Double();
            if (bolt) { shape.moveTo(5,-24); shape.lineTo(-18,5); shape.lineTo(-2,5); shape.lineTo(-5,25); shape.lineTo(20,-6); shape.lineTo(5,-6); }
            else for (int i=0; i<10; i++) { double a=-Math.PI/2+i*Math.PI/5, r=i%2==0?22:11; if(i==0)shape.moveTo(Math.cos(a)*r,Math.sin(a)*r); else shape.lineTo(Math.cos(a)*r,Math.sin(a)*r); }
            shape.closePath(); p.setColor(bolt ? new Color(65, 185, 184) : new Color(255, 207, 0)); p.fill(shape); p.dispose();
        }
    }
    private static final class DetailIcon implements Icon {
        private final int kind;
        DetailIcon(int kind) { this.kind = kind; }
        public int getIconWidth() { return kind == 5 ? 26 : 54; }
        public int getIconHeight() { return kind == 5 ? 26 : 42; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D p = graphics(g); p.translate(x, y);
            if (kind == 5) {
                p.setColor(MUTED); p.setStroke(new BasicStroke(2)); p.drawOval(3,3,20,20);
                p.drawLine(13,6,13,13); p.drawLine(13,13,19,13);
            } else {
                p.setColor(new Color(240, 232, 255)); p.fillRoundRect(0,0,54,42,13,13);
                p.translate(14,8); p.setColor(PURPLE); p.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                switch (kind) {
                    case 0 -> { p.fillPolygon(new int[]{0,13,26},new int[]{12,0,12},3); p.fillRoundRect(4,10,18,16,3,3); p.setColor(new Color(240,232,255)); p.fillRoundRect(11,18,5,9,2,2); }
                    case 1 -> { p.fillOval(10,0,15,15); p.drawLine(13,12,3,24); p.drawLine(4,22,8,23); p.setColor(new Color(240,232,255)); p.fillOval(15,4,6,6); }
                    case 2 -> { p.fillOval(9,2,10,10); p.fillOval(0,4,8,8); p.fillOval(21,4,8,8); p.fillRoundRect(0,14,9,10,6,6); p.fillRoundRect(21,14,9,10,6,6); p.fillRoundRect(7,14,15,13,8,8); }
                    case 3 -> { p.fillRoundRect(0,5,28,20,9,9); p.setColor(Color.WHITE); p.drawLine(5,13,12,13); p.drawLine(8,10,8,16); p.fillOval(20,10,3,3); p.fillOval(23,15,3,3); }
                    case 4 -> { p.fillRoundRect(2,15,6,12,3,3); p.fillRoundRect(11,8,6,19,3,3); p.fillRoundRect(20,0,6,27,3,3); }
                }
            }
            p.dispose();
        }
    }
    void tick() {
        if (finished) return;
        long remaining = deadline - clock.getAsLong();
        if (remaining <= 0) { finish(); return; }
        countdown.setText("Tự đóng sau " + ((remaining + 999_999_999L) / 1_000_000_000L) + " giây");
    }
    void respond(boolean accepted) {
        if (finished) return;
        if (clock.getAsLong() >= deadline) { finish(); return; }
        finish(); reply.accept(accepted);
    }
    private void finish() { finished = true; timer.stop(); accept.setEnabled(false); decline.setEnabled(false); close.run(); }
    @Override public void addNotify() { super.addNotify(); if (!finished) timer.start(); }
    @Override public void removeNotify() { timer.stop(); super.removeNotify(); }
}
