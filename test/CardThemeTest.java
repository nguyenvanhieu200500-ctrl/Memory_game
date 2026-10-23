package memorygame.client;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

public final class CardThemeTest {
    public static void main(String[] args) throws Exception {
        BufferedImage preview = new BufferedImage(960, 560, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = preview.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, preview.getWidth(), preview.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (CardTheme theme : CardTheme.values()) {
            Set<String> faces = new HashSet<>();
            Set<Integer> pictures = new HashSet<>();
            for (int value = 1; value <= 32; value++) {
                String face = theme.face(value);
                BufferedImage artwork = theme.image(value);
                int hash = java.util.Arrays.hashCode(artwork.getRGB(0, 0, artwork.getWidth(), artwork.getHeight(), null, 0, artwork.getWidth()));
                if (!faces.add(face) || !pictures.add(hash)) throw new AssertionError("Duplicate artwork in " + theme);
                if (artwork.getWidth() < 64 || artwork.getHeight() < 64) throw new AssertionError("Low resolution artwork");
                for (int size : new int[]{4, 6, 8}) {
                    BufferedImage small = new BufferedImage(80, 64, BufferedImage.TYPE_INT_RGB);
                    Graphics2D sg = small.createGraphics();
                    sg.setColor(Color.WHITE); sg.fillRect(0, 0, 80, 64);
                    sg.setColor(Color.BLACK);
                    CardTheme.paintFace(sg, face, new Font(Font.SANS_SERIF, Font.BOLD, 36 - size * 2), 8, 5, 64, 54);
                    sg.dispose();
                    int ink = 0;
                    for (int y = 0; y < 64; y++) for (int x = 0; x < 80; x++) if (small.getRGB(x, y) != Color.WHITE.getRGB()) ink++;
                    if (ink == 0) throw new AssertionError("Blank face " + face);
                }
                int x = (value - 1) % 8 * 120;
                int y = (value - 1) / 8 * 140;
                if (theme == (args.length > 1 ? CardTheme.valueOf(args[1]) : CardTheme.FOOD)) {
                    g.setColor(new Color(0xF5F0FF)); g.fillRoundRect(x + 3, y + 3, 114, 134, 12, 12);
                    g.setColor(new Color(0x24324B));
                    CardTheme.paintFace(g, face, new Font(Font.SANS_SERIF, Font.BOLD, 20), x + 10, y + 10, 100, 120);
                }
            }
        }
        g.dispose();
        if (args.length > 0) javax.imageio.ImageIO.write(preview, "png", new java.io.File(args[0]));
        System.out.println("CARD_THEMES_OK fourImageThemes uniquePictures renderedFaces allBoardSizes");
    }
}
