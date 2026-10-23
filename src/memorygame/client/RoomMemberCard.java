package memorygame.client;

import memorygame.common.AppTheme;

import javax.swing.*;
import java.awt.*;

/** A selectable room member, or a noninteractive empty seat. */
final class RoomMemberCard extends JButton {
    private final String name, state, title;
    private final boolean host, self, ready;

    RoomMemberCard(String name, boolean host, boolean self, String state, boolean ready, String title) {
        this.name = name; this.host = host; this.self = self;
        this.state = state; this.ready = ready; this.title = title;
        setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(name.isEmpty() ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
        String description = name.isEmpty() ? state : name + (host ? " · Chủ phòng" : "")
                + (self ? " · Bạn" : "") + " · " + state + (title.isBlank() ? "" : " · " + title);
        getAccessibleContext().setAccessibleName(description);
        setToolTipText(description);
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D p = (Graphics2D) g.create();
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        p.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        p.setPaint(new GradientPaint(0, 0, AppTheme.INSET, w, h, AppTheme.SURFACE));
        p.fillRoundRect(2, 2, w - 5, h - 5, 18, 18);
        boolean highlighted = self || isSelected() || hasFocus();
        p.setColor(highlighted ? AppTheme.PRIMARY : AppTheme.BORDER);
        p.setStroke(new BasicStroke(isSelected() || hasFocus() ? 2.5f : 1.2f));
        p.drawRoundRect(2, 2, w - 5, h - 5, 18, 18);
        boolean empty = name.isEmpty();
        int radius = Math.max(18, Math.min(32, Math.min(w / 7, h / 4)));
        int cx = empty ? w / 2 : radius + 18;
        int cy = empty ? h / 2 - 16 : h / 2;
        p.setColor(empty ? LoginPanel.MUTED : LoginPanel.CYAN);
        p.setStroke(empty ? new BasicStroke(1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{4, 4}, 0)
                : new BasicStroke(2));
        p.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
        p.setPaint(new GradientPaint(cx, cy - radius, AppTheme.PRIMARY, cx, cy + radius, AppTheme.PRIMARY));
        if (empty) p.setColor(AppTheme.MUTED);
        p.fillOval(cx - radius / 4, cy - radius / 2, radius / 2, radius / 2);
        p.fillRoundRect(cx - radius / 2, cy + 2, radius, radius / 2, 10, 10);
        if (empty) {
            p.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); p.setColor(LoginPanel.MUTED);
            p.drawString(state, Math.max(8, (w - p.getFontMetrics().stringWidth(state)) / 2), cy + radius + 23);
        } else {
            int x = cx + radius + 14;
            int y = h / 2 - (title.isBlank() ? 25 : 34);
            p.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14)); p.setColor(LoginPanel.INK);
            drawText(p, name, x, y, w - x - 10);
            p.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            p.setColor(host ? AppTheme.GOLD_TEXT : LoginPanel.CYAN);
            drawText(p, (host ? "Chủ phòng" : "Thành viên") + (self ? "  ·  Bạn" : ""), x, y + 23, w - x - 10);
            p.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            p.setColor(ready ? AppTheme.SECONDARY : LoginPanel.MUTED);
            drawText(p, state, x, y + 46, w - x - 10);
            if (!title.isBlank()) {
                p.setColor(AppTheme.SECONDARY);
                drawText(p, title, x, y + 65, w - x - 10);
            }
        }
        p.dispose();
    }

    private static void drawText(Graphics2D p, String text, int x, int y, int width) {
        String value = text;
        while (!value.isEmpty() && p.getFontMetrics().stringWidth(value) > width) {
            value = value.substring(0, value.length() - 1);
        }
        if (!value.equals(text) && value.length() > 2) value = value.substring(0, value.length() - 2) + "…";
        p.drawString(value, x, y);
    }
}
