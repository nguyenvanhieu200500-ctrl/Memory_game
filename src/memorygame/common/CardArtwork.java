package memorygame.common;

import java.awt.*;

/** Shared card-back artwork for the game and the server observer. */
public final class CardArtwork {
    private CardArtwork() {}

    public static void paintBack(Graphics2D g, int x, int y, int width, int height) {
            g.setPaint(new GradientPaint(x, y, new Color(0x7146B8), x + width, y + height, new Color(0x35216D)));
            g.fillRect(x, y, width, height);
            g.setColor(new Color(207, 162, 255, 18));
            int tile = Math.max(12, height / 5);
            for (int row = 0; row < height / tile + 1; row++) {
                for (int col = 0; col < width / tile + 1; col++) {
                    int px = x + col * tile, py = y + row * tile;
                    g.fillPolygon(new int[]{px, px + tile / 2, px + tile, px + tile / 2},
                            new int[]{py + tile / 2, py, py + tile / 2, py + tile}, 4);
                }
            }
            int cx = x + width / 2, cy = y + height / 2;
            int rx = Math.max(5, (int) (width * .27));
            int ry = Math.max(7, (int) (height * .34));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1f, new float[]{2f, 3f}, 0f));
            g.setColor(new Color(255, 218, 171, 130));
            g.drawOval(cx - rx, cy - ry, rx * 2, ry * 2);
            Polygon diamond = new Polygon(new int[]{cx, cx + rx, cx, cx - rx},
                    new int[]{cy - ry, cy, cy + ry, cy}, 4);
            g.setColor(new Color(255, 143, 239, 65));
            g.setStroke(new BasicStroke(6f));
            g.draw(diamond);
            g.setColor(new Color(255, 225, 166));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(diamond);
            int star = Math.max(3, Math.min(width, height) / 22);
            paintStar(g, cx, cy - ry, star);
            paintStar(g, cx + rx, cy, star);
            paintStar(g, cx, cy + ry, star);
            paintStar(g, cx - rx, cy, star);
            paintStar(g, x + 10, y + 10, star);
            paintStar(g, x + width - 10, y + 10, star);
            paintStar(g, x + 10, y + height - 10, star);
            paintStar(g, x + width - 10, y + height - 10, star);
            g.setStroke(new BasicStroke(1f));
        }

    private static void paintStar(Graphics2D g, int x, int y, int radius) {
            g.setColor(new Color(255, 236, 178));
            g.fillPolygon(new int[]{x, x + radius / 2, x + radius, x + radius / 2,
                            x, x - radius / 2, x - radius, x - radius / 2},
                    new int[]{y - radius * 2, y - radius / 2, y, y + radius / 2,
                            y + radius * 2, y + radius / 2, y, y - radius / 2}, 8);
        }

}
