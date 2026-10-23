package memorygame.client;

import javax.swing.*;
import java.awt.*;

/** Decorative illustration only; no player or server data is drawn here. */
final class LobbyArtwork extends JPanel {
    private final boolean compact;
    private final boolean caption;
    private final boolean illustrated;
    LobbyArtwork(boolean compact) { this(compact, !compact); }
    LobbyArtwork(boolean compact, boolean caption) {
        this(compact, caption, false);
    }
    LobbyArtwork(boolean compact, boolean caption, boolean illustrated) {
        this.illustrated = illustrated;
        this.compact = compact;
        this.caption = caption;
        setOpaque(false);
        setPreferredSize(compact ? new Dimension(illustrated ? 220 : 135, illustrated ? 74 : 58)
                : new Dimension(0, illustrated ? 260 : 190));
    }
    @Override protected void paintComponent(Graphics graphics) {
        if (!illustrated) { paintOriginal(graphics); return; }
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        double scale = Math.min(getWidth() / 440.0, getHeight() / (compact ? 170.0 : 260.0));
        g.translate(getWidth() / 2.0, getHeight() / 2.0);
        g.scale(scale, scale);
        int cy = caption ? -25 : 0;
        g.setPaint(new RadialGradientPaint(0, cy, 210, new float[]{0, 1},
                new Color[]{new Color(211, 160, 255, 110), new Color(255, 255, 255, 0)}));
        g.fillOval(-210, cy - 120, 420, 240);
        g.setColor(new Color(139, 87, 217, 30)); g.fillOval(-110, cy + 77, 220, 20);
        LoginPanel.Artwork.card(g, -48, cy - 5, -.24, new Color(0xA147F2), false, .85);
        LoginPanel.Artwork.card(g, 55, cy + 12, .20, new Color(0x1FAAB2), true, .85);
        g.rotate(-.18);
        for (int stroke = 9; stroke >= 1; stroke -= 2) {
            g.setColor(new Color(255, 231, 172, stroke == 1 ? 240 : 22));
            g.setStroke(new BasicStroke(stroke));
            g.drawArc(-160, cy - 22, 320, 80, 180, 180);
        }
        g.rotate(.18);
        LobbyStyle.star(g, -156, cy - 62, 14, new Color(0xFFCC62));
        LobbyStyle.star(g, 142, cy - 83, 14, new Color(0xFFCC62));
        LobbyStyle.star(g, 167, cy + 66, 15, new Color(0xAF66ED));
        LobbyStyle.star(g, -181, cy + 70, 11, new Color(0xFFCC62));
        if (caption) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            g.setColor(LobbyStyle.PURPLE);
            String title = "Mời bạn bè cùng tham gia";
            g.drawString(title, -g.getFontMetrics().stringWidth(title) / 2, 112);
        }
        g.dispose();
    }
    private void paintOriginal(Graphics graphics) {
        Graphics2D p = (Graphics2D) graphics.create();
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        p.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (!compact && getHeight() < 190) {
            double scale = Math.max(.1, getHeight() / 190.0);
            p.translate(getWidth() * (1 - scale) / 2, 0); p.scale(scale, scale);
        }
        int cx = getWidth() / 2, y = compact ? 30 : caption ? 78 : Math.max(78, getHeight() / 2 - 5);
        int offset = compact ? 24 : 40;
        double scale = compact ? .34 : .66;
        LoginPanel.Artwork.card(p, cx - offset, y, -.22, LoginPanel.CYAN, false, scale);
        LoginPanel.Artwork.card(p, cx + offset, y + 9, .23, LoginPanel.PURPLE, true, scale);
        if (caption) {
            p.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16)); p.setColor(LoginPanel.INK);
            String title = "Mời bạn bè cùng tham gia";
            p.drawString(title, cx - p.getFontMetrics().stringWidth(title) / 2, 172);
        }
        p.dispose();
    }

}
