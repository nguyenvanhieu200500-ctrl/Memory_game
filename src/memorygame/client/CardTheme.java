package memorygame.client;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Face themes map the server's pair IDs to distinct image-only card faces. */
public enum CardTheme {
    FRUIT("Trái cây",
            "Táo|Lê|Cam|Quýt|Chanh|Bưởi|Chuối|Dưa hấu|Nho|Dâu tây|Đào|Anh đào|Dứa|Xoài|Dừa|Kiwi|Bơ|Việt quất|Dưa lưới|Mận|Vải|Nhãn|Chôm chôm|Sầu riêng|Măng cụt|Đu đủ|Ổi|Khế|Thanh long|Chanh dây|Hồng|Lựu"),
    ANIMALS("Động vật",
            "Chó|Mèo|Chuột|Hamster|Thỏ|Cáo|Gấu|Gấu trúc|Koala|Hổ|Sư tử|Bò|Heo|Ếch|Khỉ|Gà|Chim cánh cụt|Chim|Vịt|Đại bàng|Cú|Dơi|Sói|Ngựa|Ong|Bướm|Ốc sên|Rùa|Rắn|Cá heo|Cá voi|Bạch tuộc"),
    FOOD("Đồ ăn",
            "Bánh mì|Bánh sừng bò|Bánh vòng|Bánh kếp|Phô mai|Trứng|Thịt xông khói|Bít tết|Đùi gà|Hamburger|Khoai tây chiên|Pizza|Hot dog|Sandwich|Taco|Burrito|Salad|Mì Ý|Mì ramen|Sushi|Cơm nắm|Cơm cà ri|Há cảo|Tôm chiên|Kem|Bánh kem|Cupcake|Donut|Bánh quy|Sô cô la|Kẹo|Bắp rang"),
    VEHICLES("Phương tiện",
            "Ô tô|Taxi|Xe buýt|Xe điện buýt|Xe đua|Xe cảnh sát|Xe cứu thương|Xe cứu hỏa|Xe tải|Xe đầu kéo|Máy kéo|Xe máy|Xe tay ga|Xe đạp|Xe trượt|Xích lô|Tàu hỏa|Tàu cao tốc|Tàu điện|Tàu điện ngầm|Máy bay|Trực thăng|Tên lửa|Đĩa bay|Thuyền buồm|Ca nô|Tàu thủy|Phà|Xuồng|Cáp treo|Xe lăn|Xe bán tải");

    private final String title;
    private final String[] names;
    private BufferedImage atlas;
    private BufferedImage[] sprites;

    CardTheme(String title, String names) {
        this.title = title;
        this.names = names.split("\\|");
        if (this.names.length != 32) {
            throw new IllegalArgumentException("A theme must cover all 32 pairs");
        }
    }

    public String face(int value) {
        if (value < 1 || value > 32) throw new IllegalArgumentException("Invalid pair ID: " + value);
        return "@" + name() + ":" + value;
    }

    BufferedImage image(int value) {
        if (value < 1 || value > 32) throw new IllegalArgumentException("Invalid pair ID: " + value);
        if (atlas == null) {
            String resource = "images/" + name().toLowerCase(Locale.ROOT) + ".png";
            try (var stream = CardTheme.class.getResourceAsStream(resource)) {
                if (stream == null) throw new IllegalStateException("Missing card artwork: " + resource);
                atlas = ImageIO.read(stream);
                if (atlas == null) throw new IllegalStateException("Invalid card artwork: " + resource);
                extractSprites();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot load card artwork: " + resource, e);
            }
        }
        return sprites[value - 1];
    }

    // Locate the white gutters rather than cutting illustrations at nominal grid lines.
    private void extractSprites() {
        sprites = new BufferedImage[32];
        int[] rows = new int[5];
        rows[4] = atlas.getHeight();
        for (int row = 1; row < 4; row++) rows[row] = gutter(true, row * atlas.getHeight() / 4,
                atlas.getHeight() / 10, 0, atlas.getWidth());
        for (int row = 0; row < 4; row++) {
            int[] columns = new int[9];
            columns[8] = atlas.getWidth();
            for (int column = 1; column < 8; column++) columns[column] = gutter(false,
                    column * atlas.getWidth() / 8, atlas.getWidth() / 32, rows[row], rows[row + 1]);
            for (int column = 0; column < 8; column++) {
                int left = columns[column], right = columns[column + 1];
                int top = rows[row], bottom = rows[row + 1];
                int minX = right, minY = bottom, maxX = left, maxY = top;
                for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
                    if (isInk(x, y)) {
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    }
                }
                minX = Math.max(left, minX - 4); minY = Math.max(top, minY - 4);
                maxX = Math.min(right - 1, maxX + 4); maxY = Math.min(bottom - 1, maxY + 4);
                sprites[row * 8 + column] = atlas.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
            }
        }
    }

    private int gutter(boolean horizontal, int expected, int radius, int start, int end) {
        int best = expected, score = Integer.MAX_VALUE;
        for (int position = expected - radius; position <= expected + radius; position++) {
            int count = 0;
            for (int p = start; p < end; p++) if (isInk(horizontal ? p : position, horizontal ? position : p)) count++;
            if (count < score || count == score && Math.abs(position - expected) < Math.abs(best - expected)) {
                best = position; score = count;
            }
        }
        return best;
    }

    private boolean isInk(int x, int y) {
        int rgb = atlas.getRGB(x, y);
        return Math.min((rgb >> 16) & 255, Math.min((rgb >> 8) & 255, rgb & 255)) < 210;
    }

    String description(int value) { return names[value - 1]; }

    @Override public String toString() { return title; }

    public static void paintFace(Graphics2D g, String text, Font base, int x, int y, int width, int height) {
        if (!text.startsWith("@")) {
            drawCentered(g, text, base, x, y, width, height);
            return;
        }
        String[] parts = text.substring(1).split(":", 2);
        BufferedImage image = valueOf(parts[0]).image(Integer.parseInt(parts[1]));
        double scale = Math.min(width / (double) image.getWidth(), height / (double) image.getHeight());
        int w = Math.max(1, (int) (image.getWidth() * scale));
        int h = Math.max(1, (int) (image.getHeight() * scale));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(image, x + (width - w) / 2, y + (height - h) / 2, w, h, null);
    }

    private static void drawCentered(Graphics2D g, String text, Font font, int x, int y, int width, int height) {
        while (font.getSize() > 8 && (g.getFontMetrics(font).stringWidth(text) > width
                || g.getFontMetrics(font).getHeight() > height)) font = font.deriveFont((float) font.getSize() - 1);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, x + (width - metrics.stringWidth(text)) / 2,
                y + (height - metrics.getHeight()) / 2 + metrics.getAscent());
    }
}
