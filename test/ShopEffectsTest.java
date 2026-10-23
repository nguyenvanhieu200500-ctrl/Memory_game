package memorygame.test;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShopEffectsTest {
    private record Preview(String text, Color background) { }

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                Class<?> cardType = Class.forName("memorygame.client.GameFrame$CardButton");
                Constructor<?> constructor = cardType.getDeclaredConstructor(String.class);
                constructor.setAccessible(true);
                Method setThemeEffect = cardType.getDeclaredMethod("setThemeEffect", String.class, double.class);
                setThemeEffect.setAccessible(true);

                JButton preview = (JButton) constructor.newInstance("?");
                preview.setSize(150, 190);
                preview.setFont(preview.getFont().deriveFont(44f));

                Map<String, Preview> products = new LinkedHashMap<>();
                products.put("theme_classic", new Preview("?", new Color(45, 58, 76)));
                products.put("theme_ocean", new Preview("?", new Color(14, 116, 144)));
                products.put("theme_violet", new Preview("?", new Color(109, 40, 217)));
                products.put("theme_gold", new Preview("?", new Color(161, 98, 7)));
                products.put("title_memory", new Preview("◆", new Color(12, 62, 82)));
                products.put("title_champion", new Preview("★", new Color(92, 55, 8)));

                for (Map.Entry<String, Preview> entry : products.entrySet()) {
                    preview.setText(entry.getValue().text());
                    preview.setBackground(entry.getValue().background());
                    setThemeEffect.invoke(preview, entry.getKey(), 0.12);
                    BufferedImage first = render(preview);
                    setThemeEffect.invoke(preview, entry.getKey(), 0.62);
                    BufferedImage second = render(preview);
                    int changedPixels = changedPixels(first, second);
                    if (changedPixels < 40) {
                        throw new AssertionError(entry.getKey()
                                + " chưa có chuyển động rõ ràng: changedPixels=" + changedPixels);
                    }
                }
                System.out.println("SHOP_EFFECTS_OK classic ocean violet gold memoryTitle championTitle");
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static BufferedImage render(JButton button) {
        BufferedImage image = new BufferedImage(150, 190, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        button.printAll(graphics);
        graphics.dispose();
        return image;
    }

    private static int changedPixels(BufferedImage first, BufferedImage second) {
        int changed = 0;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) changed++;
            }
        }
        return changed;
    }
}
