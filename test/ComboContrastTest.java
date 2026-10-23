package memorygame.test;

import memorygame.client.GameFrame;
import memorygame.common.AppTheme;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;

public final class ComboContrastTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                AppTheme.install();
                Method factory = GameFrame.class.getDeclaredMethod("styledCombo", String[].class);
                factory.setAccessible(true);
                JComboBox<?> combo = (JComboBox<?>) factory.invoke(null,
                        (Object) new String[]{"2 người", "3 người", "4 người"});
                combo.setSize(250, 40);
                combo.doLayout();

                BufferedImage image = new BufferedImage(250, 40, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                combo.printAll(graphics);
                graphics.dispose();

                int surfacePixels = 0, inkPixels = 0, accentPixels = 0;
                for (int y = 1; y < image.getHeight() - 1; y++) {
                    for (int x = 1; x < image.getWidth() - 1; x++) {
                        int rgb = image.getRGB(x, y);
                        if (rgb == AppTheme.SURFACE.getRGB()) surfacePixels++;
                        if (rgb == AppTheme.TEXT.getRGB()) inkPixels++;
                        if (rgb == AppTheme.PRIMARY.getRGB()) accentPixels++;
                    }
                }
                if (surfacePixels < 4000 || inkPixels < 10 || accentPixels < 400) {
                    throw new AssertionError("ComboBox missing readable field or arrow: surface=" + surfacePixels
                            + ", ink=" + inkPixels + ", accent=" + accentPixels);
                }
                Color[][] pairs = {
                        {AppTheme.TEXT, AppTheme.SURFACE},
                        {AppTheme.MUTED, AppTheme.INSET},
                        {AppTheme.MUTED, AppTheme.SELECTED},
                        {AppTheme.ON_ACCENT, AppTheme.PRIMARY},
                        {AppTheme.ON_ACCENT, AppTheme.SECONDARY},
                        {AppTheme.SECONDARY, AppTheme.SUCCESS_BG},
                        {AppTheme.GOLD_TEXT, AppTheme.WARNING_BG},
                        {AppTheme.ORANGE_TEXT, AppTheme.SURFACE},
                        {AppTheme.DANGER, AppTheme.DANGER_BG},
                        {AppTheme.TEXT, AppTheme.GOLD},
                        {AppTheme.TEXT, AppTheme.ORANGE}
                };
                for (Color[] pair : pairs) {
                    if (contrast(pair[0], pair[1]) < 4.5) {
                        throw new AssertionError("Theme contrast below 4.5:1: "
                                + AppTheme.hex(pair[0]) + " on " + AppTheme.hex(pair[1]));
                    }
                }
                for (Color player : memorygame.common.GameColors.forPlayers(
                        java.util.List.of("a", "b", "c", "d")).values()) {
                    if (contrast(memorygame.common.GameColors.textOn(player), player) < 4.5) {
                        throw new AssertionError("Player card text is unreadable");
                    }
                }
                System.out.println("COMBO_CONTRAST_OK candyGarden purpleArrow semanticColors playerColors");
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static double contrast(Color a, Color b) {
        double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
    }
    private static double luminance(Color c) {
        double[] rgb = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
        for (int i = 0; i < 3; i++) rgb[i] = rgb[i] <= .04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4);
        return .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2];
    }
}
