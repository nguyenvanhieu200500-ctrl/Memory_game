package memorygame.client;

import memorygame.common.AppTheme;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

/** Presentation-only components for the illustrated lobby. */
final class LobbyStyle {
    private LobbyStyle() {}
    static final Color PURPLE = new Color(0x4D0CD5);

    static JPanel surface(LayoutManager layout, int variant) {
        return new JPanel(layout) {
            { setOpaque(false); }
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                if (variant == 0) {
                    g.setPaint(new LinearGradientPaint(0, 0, 0, Math.max(1, h),
                            new float[]{0f, .12f, .28f, .87f, 1f},
                            new Color[]{new Color(0x8275F5), new Color(0xB4A2F3), new Color(0xFCF3E9),
                                    new Color(0xFFF7EC), new Color(0xD7B5FC)}));
                    g.fillRect(0, 0, w, h);
                    g.setPaint(new RadialGradientPaint(w * .56f, 0, Math.max(1, w * .4f),
                            new float[]{0f, 1f}, new Color[]{new Color(234, 220, 255, 165), new Color(234, 220, 255, 0)}));
                    g.fillRect(0, 0, w, h / 3);
                    for (int i = 0; i < 10; i++) {
                        int cloudX = i * w / 9 - 60;
                        g.setColor(new Color(245, 220, 255, 65));
                        g.fillOval(cloudX, h / 8 + (i % 3) * 9, 140, 65);
                        g.setColor(new Color(190, 155, 255, 60));
                        g.fillOval(cloudX, h - 45 - (i % 3) * 10, 130, 90);
                    }
                    ghostCard(g, (int) (w * .47), 53, -.34, 36);
                    ghostCard(g, (int) (w * .59), 32, .34, 42);
                } else {
                    for (int blur = 8; blur > 0; blur -= 2) {
                        g.setColor(new Color(125, 64, 174, 7));
                        g.fillRoundRect(2 - blur / 2, 4, w - 4 + blur, h - 7 + blur / 2, 28, 28);
                    }
                    Color from = variant == 3 ? new Color(0x6743D4) : variant == 5 ? new Color(0x7D57EC)
                            : variant == 4 ? new Color(0xE7DBFC) : new Color(0xFFFBF7);
                    Color to = variant == 3 ? new Color(0xDFC4F0) : variant == 5 ? new Color(0xDCA2F6)
                            : variant == 2 ? new Color(0xB88AF7) : variant == 4 ? new Color(0xE4DAF9) : new Color(0xFCF5F9);
                    g.setPaint(new GradientPaint(0, 0, from, w, h * .3f, to));
                    g.fillRoundRect(1, 1, w - 3, h - 7, 24, 24);
                    if (variant == 2) {
                        g.setPaint(new RadialGradientPaint(w * .65f, h * .8f, Math.max(1, w * .42f),
                                new float[]{0, 1}, new Color[]{new Color(245, 224, 255, 180), new Color(245, 224, 255, 0)}));
                        g.fillRoundRect(1, 1, w - 3, h - 7, 24, 24);
                    }
                    g.setStroke(new BasicStroke(1.5f));
                    g.setColor(new Color(0xD4B9F9));
                    g.drawRoundRect(1, 1, w - 3, h - 7, 24, 24);
                    g.setColor(new Color(255, 255, 255, 160));
                    g.drawRoundRect(3, 3, w - 7, h - 11, 22, 22);
                    if (variant == 3 || variant == 5) {
                        ghostCard(g, w * 3 / 5, h / 2, -.4, 23);
                        ghostCard(g, w * 9 / 10, h / 2, .4, 27);
                    }
                }
                if (variant == 0 || variant == 2 || variant == 3) {
                    for (int i = 0; i < 12; i++) {
                        int x = (i * 173 + 43) % Math.max(1, w), y = (i * 73 + 19) % Math.max(1, h);
                        star(g, x, y, 3 + i % 3, new Color(255, 255, 255, 100));
                    }
                }
                g.dispose();
                super.paintComponent(graphics);
            }
        };
    }

    static JButton button(String text, boolean primary) {
        JButton button = new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean gold = Boolean.TRUE.equals(getClientProperty("gold"));
                Color a = gold ? new Color(0xFFE88F) : primary ? new Color(0xC575F7) : Color.WHITE;
                Color b = gold ? new Color(0xFFBB2F) : primary ? new Color(0x6934F0) : new Color(0xF1DCFC);
                if (!isEnabled()) { a = new Color(0xEAE4F2); b = new Color(0xDED6EA); }
                if (getModel().isRollover() && isEnabled()) a = a.brighter();
                int y = getModel().isPressed() ? 2 : 0;
                g.setColor(new Color(112, 44, 190, 35));
                g.fillRoundRect(1, y + 4, getWidth() - 3, getHeight() - 5, 16, 16);
                g.setPaint(new GradientPaint(0, 0, a, getWidth(), getHeight(), b));
                g.fillRoundRect(1, y + 1, getWidth() - 3, getHeight() - 6, 15, 15);
                g.setColor(gold ? new Color(0xFFD670) : new Color(0xCCAEF8));
                g.setStroke(new BasicStroke(isFocusOwner() ? 3f : 1.5f));
                g.drawRoundRect(1, y + 1, getWidth() - 3, getHeight() - 6, 15, 15);
                g.setColor(new Color(255, 255, 255, 165));
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(3, y + 3, getWidth() - 7, getHeight() - 10, 13, 13);
                g.dispose(); super.paintComponent(graphics);
            }
        };
        button.setOpaque(false); button.setContentAreaFilled(false); button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setForeground(primary ? Color.WHITE : PURPLE);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        button.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        button.setPreferredSize(new Dimension(120, 46));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    static Icon achievementIcon(boolean crown) {
        return new Icon() {
            public int getIconWidth() { return 24; }
            public int getIconHeight() { return 24; }
            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.translate(x, y);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(233, 158, 41, 35));
                g.fillOval(3, 20, 18, 3);
                if (crown) {
                    Path2D shape = new Path2D.Double();
                    shape.moveTo(4, 17); shape.lineTo(2, 6); shape.lineTo(8, 11);
                    shape.lineTo(12, 3); shape.lineTo(16, 11); shape.lineTo(22, 6);
                    shape.lineTo(20, 17); shape.closePath();
                    g.setPaint(new GradientPaint(0, 4, new Color(0xFFE88F), 0, 20, new Color(0xFFB524)));
                    g.fill(shape); g.setColor(new Color(0xE79023)); g.draw(shape);
                    g.setColor(new Color(0xFFD05B)); g.fillRoundRect(4, 18, 16, 3, 3, 3);
                    g.setColor(new Color(0xFFF0AC));
                    g.fillOval(0, 4, 4, 4); g.fillOval(10, 0, 4, 4); g.fillOval(20, 4, 4, 4);
                    g.setColor(new Color(0xD980DD)); g.fillOval(10, 12, 4, 4);
                } else {
                    g.setColor(new Color(0xECA029));
                    g.drawRoundRect(2, 5, 7, 8, 5, 5); g.drawRoundRect(15, 5, 7, 8, 5, 5);
                    g.setPaint(new GradientPaint(5, 3, new Color(0xFFE889), 18, 15, new Color(0xFFB627)));
                    g.fillRoundRect(6, 3, 12, 13, 5, 5);
                    g.setColor(new Color(0xE79023)); g.drawRoundRect(6, 3, 12, 13, 5, 5);
                    g.fillRect(11, 16, 2, 4); g.fillRoundRect(7, 20, 10, 3, 3, 3);
                    star(g, 12, 9, 4, new Color(0xFFF6D2));
                }
                g.dispose();
            }
        };
    }

    static JLabel logo() {
        JLabel label = new JLabel(new Icon() {
            public int getIconWidth() { return 210; }
            public int getIconHeight() { return 58; }
            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create(); g.translate(x, y);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setPaint(new GradientPaint(0, 0, new Color(0x6629DB), 210, 58, new Color(0xAE7CFF)));
                g.fillRoundRect(0, 3, 208, 52, 16, 16);
                LoginPanel.Artwork.card(g, 23, 29, -.22, PURPLE, false, .27);
                LoginPanel.Artwork.card(g, 48, 34, .20, AppTheme.SECONDARY, true, .25);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
                g.setColor(new Color(0x45207B)); g.drawString("MEMORY", 66, 30);
                g.setColor(new Color(0xFFE080)); g.drawString("MEMORY", 65, 28);
                g.setFont(g.getFont().deriveFont(23f));
                g.setColor(Color.WHITE); g.drawString("GAME", 95, 50); g.dispose();
            }
        });
        label.getAccessibleContext().setAccessibleName("Memory Game");
        return label;
    }

    static Icon cat(int size) {
        return new Icon() {
            public int getIconWidth() { return size; }
            public int getIconHeight() { return size; }
            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create(); g.translate(x, y); g.scale(size / 100.0, size / 100.0);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(156, 103, 220, 28)); g.fillOval(18, 88, 68, 9);
                // A curled tail, tiny paws, and a large soft head give the mascot a chibi silhouette.
                g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(0xAB7CEA));
                g.drawArc(69, 65, 23, 24, -90, 240);
                g.setPaint(new GradientPaint(30, 58, new Color(0xDFC7FF), 65, 94, new Color(0xA87BE5)));
                g.fillOval(29, 56, 44, 38);
                g.setColor(new Color(0xFFFAFF)); g.fillOval(37, 63, 28, 29);
                g.fillOval(25, 81, 22, 14); g.fillOval(57, 81, 22, 14);
                Path2D head = new Path2D.Double();
                head.moveTo(17, 36);
                head.curveTo(10, 20, 13, 5, 20, 7);
                head.curveTo(27, 8, 34, 19, 35, 19);
                head.curveTo(44, 15, 58, 15, 66, 20);
                head.curveTo(73, 9, 83, 4, 87, 9);
                head.curveTo(91, 15, 87, 29, 85, 36);
                head.curveTo(103, 67, 80, 80, 51, 80);
                head.curveTo(20, 80, 1, 66, 17, 36);
                head.closePath();
                g.setPaint(new GradientPaint(20, 12, new Color(0xD6B5FF), 75, 77, new Color(0xA16EE4)));
                g.fill(head); g.setColor(new Color(0x8C58CB)); g.setStroke(new BasicStroke(1.7f)); g.draw(head);
                Path2D ear = new Path2D.Double();
                ear.moveTo(19, 29); ear.curveTo(15, 11, 18, 10, 30, 25); ear.closePath();
                g.setColor(new Color(0xF3B6DD)); g.fill(ear);
                Path2D earRight = new Path2D.Double();
                earRight.moveTo(72, 25); earRight.curveTo(85, 9, 87, 11, 81, 30); earRight.closePath(); g.fill(earRight);
                Path2D muzzle = new Path2D.Double();
                muzzle.moveTo(15, 55); muzzle.curveTo(22, 47, 34, 49, 43, 38);
                muzzle.quadTo(51, 24, 58, 39); muzzle.curveTo(67, 50, 79, 46, 88, 56);
                muzzle.curveTo(91, 83, 12, 85, 15, 55); muzzle.closePath();
                g.setColor(new Color(0xFFFAFF)); g.fill(muzzle);
                g.setColor(new Color(0xD9B9FA));
                g.fillRoundRect(40, 20, 5, 10, 5, 5); g.fillRoundRect(48, 18, 5, 12, 5, 5); g.fillRoundRect(56, 20, 5, 10, 5, 5);
                for (int eyeX : new int[]{26, 63}) {
                    g.setPaint(new GradientPaint(eyeX, 42, new Color(0x321C56), eyeX, 61, new Color(0x7945AB)));
                    g.fillOval(eyeX, 41, 14, 19);
                    g.setColor(new Color(0xC58FF2)); g.fillOval(eyeX + 3, 53, 8, 5);
                    g.setColor(Color.WHITE); g.fillOval(eyeX + 2, 43, 6, 7); g.fillOval(eyeX + 9, 51, 3, 3);
                }
                g.setColor(new Color(0xF5BAD9)); g.fillOval(18, 59, 15, 7); g.fillOval(71, 59, 15, 7);
                g.setColor(new Color(0xCB79A6));
                g.fillOval(47, 57, 8, 5);
                g.setColor(new Color(0x704274)); g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawArc(43, 59, 8, 8, 180, 180); g.drawArc(51, 59, 8, 8, 180, 180);
                g.setColor(new Color(0xB28AC6));
                g.drawLine(13, 56, 21, 58); g.drawLine(12, 62, 20, 62);
                g.drawLine(82, 58, 91, 56); g.drawLine(83, 62, 92, 62);
                g.setColor(new Color(0x9762D4)); g.fillRoundRect(39, 77, 25, 5, 4, 4);
                star(g, 51, 83, 6, new Color(0xFFD361));
                star(g, 6, 37, 4, new Color(0xCDA0F5));
                star(g, 94, 25, 4, new Color(0xFFD361));
                g.dispose();
            }
        };
    }

    private static void ghostCard(Graphics2D parent, int x, int y, double angle, int size) {
        Graphics2D g = (Graphics2D) parent.create();
        g.translate(x, y); g.rotate(angle);
        g.setColor(new Color(255, 255, 255, 24));
        g.fillRoundRect(-size / 2, -size * 2 / 3, size, size * 4 / 3, 7, 7);
        g.setColor(new Color(255, 255, 255, 75)); g.setStroke(new BasicStroke(2.5f));
        g.drawRoundRect(-size / 2, -size * 2 / 3, size, size * 4 / 3, 7, 7);
        star(g, 0, 0, size / 5, new Color(255, 255, 255, 100)); g.dispose();
    }

    static void star(Graphics2D g, int x, int y, int radius, Color color) {
        Path2D path = new Path2D.Double();
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4, r = i % 2 == 0 ? radius : radius * .3;
            double px = x + Math.cos(angle) * r, py = y + Math.sin(angle) * r;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.closePath(); g.setColor(color); g.fill(path);
    }
}
