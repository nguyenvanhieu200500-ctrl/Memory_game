package memorygame.common;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;

/** Small, bounded PNG payload shared by account storage and clients. */
public final class AvatarImages {
    private AvatarImages() {}
    public static BufferedImage decode(String encoded) throws IOException {
        if (encoded == null || encoded.isEmpty()) return null;
        if (encoded.length() > 100_000) throw new IOException("Ảnh đại diện quá lớn.");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException e) { throw new IOException("Ảnh không hợp lệ."); }
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Không đọc được ảnh.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                if (!reader.getFormatName().equalsIgnoreCase("png") || reader.getWidth(0) > 128 || reader.getHeight(0) > 128)
                    throw new IOException("Ảnh phải là PNG tối đa 128 × 128.");
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }
    public static String fromFile(File file) throws IOException {
        if (file.length() > 10_000_000) throw new IOException("Vui lòng chọn ảnh dưới 10 MB.");
        BufferedImage image;
        try (var input = ImageIO.createImageInputStream(file)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Vui lòng chọn ảnh PNG hoặc JPG.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String type = reader.getFormatName();
                if (!type.equalsIgnoreCase("png") && !type.equalsIgnoreCase("jpeg")) throw new IOException("Chỉ hỗ trợ PNG và JPG.");
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 25_000_000) throw new IOException("Ảnh có kích thước quá lớn.");
                image = reader.read(0);
            } finally { reader.dispose(); }
        }
        BufferedImage avatar = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = avatar.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int edge = Math.min(image.getWidth(), image.getHeight());
        int x = (image.getWidth() - edge) / 2, y = (image.getHeight() - edge) / 2;
        g.drawImage(image, 0, 0, 128, 128, x, y, x + edge, y + edge, null); g.dispose();
        var output = new ByteArrayOutputStream(); ImageIO.write(avatar, "png", output);
        String encoded = Base64.getEncoder().encodeToString(output.toByteArray());
        decode(encoded); return encoded;
    }
}
