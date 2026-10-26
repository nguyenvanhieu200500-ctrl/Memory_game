package memorygame.client;

import memorygame.common.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

final class ProfilePanel extends JPanel {
    private final JLabel preview = new JLabel(), avatar = new JLabel(), stats = new JLabel(), status = new JLabel(" ");
    private final JPasswordField current = new JPasswordField(), next = new JPasswordField(), confirm = new JPasswordField();
    private final JButton saveAvatar = LobbyStyle.button("Lưu ảnh", true), change = LobbyStyle.button("Đổi mật khẩu", true);
    private String selectedAvatar = "", savedAvatar = "";
    private final CardLayout views = new CardLayout();
    private final JPanel pages = new JPanel(views);
    private final JButton back = LobbyStyle.button("← Quay lại", false);

    private void showPage(String page) {
        if (!page.equals("password")) { current.setText(""); next.setText(""); confirm.setText(""); }
        if (page.equals("avatar")) { selectedAvatar = savedAvatar; preview.setIcon(icon(savedAvatar, 128)); }
        status.setText(" ");
        views.show(pages, page);
        back.setVisible(!page.equals("overview"));
    }

    ProfilePanel(Protocol.Message profile, Consumer<String> updateAvatar,
                 java.util.function.BiConsumer<String, String> password, Runnable close) {
        super(new BorderLayout(12, 14));
        setBackground(AppTheme.INSET); setBorder(new EmptyBorder(20, 24, 20, 24));
        JLabel title = new JLabel("Thông tin cá nhân · " + profile.string("username"));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22)); title.setForeground(AppTheme.PRIMARY); add(title, BorderLayout.NORTH);
        pages.setOpaque(false);
        JPanel overview = new JPanel(new BorderLayout(0, 24)); overview.setOpaque(false);
        JPanel info = LobbyStyle.surface(new BorderLayout(24, 0), 1);
        info.setBorder(new EmptyBorder(24, 20, 24, 20));
        avatar.setPreferredSize(new Dimension(110, 110)); info.add(avatar, BorderLayout.WEST); info.add(stats);
        overview.add(info);
        JPanel options = new JPanel(new GridLayout(1, 2, 14, 0)); options.setOpaque(false);
        JButton editAvatar = LobbyStyle.button("Đổi ảnh đại diện", true);
        JButton editPassword = LobbyStyle.button("Đổi mật khẩu", true);
        editAvatar.addActionListener(e -> showPage("avatar"));
        editPassword.addActionListener(e -> showPage("password"));
        options.add(editAvatar); options.add(editPassword); overview.add(options, BorderLayout.SOUTH);
        pages.add(overview, "overview");

        JPanel avatarPage = new JPanel(new BorderLayout(0, 18)); avatarPage.setOpaque(false);
        JLabel avatarTitle = new JLabel("Đổi ảnh đại diện", SwingConstants.CENTER);
        avatarTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20)); avatarTitle.setForeground(AppTheme.PRIMARY);
        avatarPage.add(avatarTitle, BorderLayout.NORTH);
        preview.setHorizontalAlignment(SwingConstants.CENTER); avatarPage.add(preview);
        JPanel avatarControls = new JPanel(new BorderLayout(0, 8)); avatarControls.setOpaque(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER)); actions.setOpaque(false);
        JButton choose = LobbyStyle.button("Chọn ảnh…", false), reset = LobbyStyle.button("Mèo mặc định", false);
        actions.add(choose); actions.add(reset); actions.add(saveAvatar); avatarControls.add(actions);
        JLabel hint = new JLabel("PNG/JPG dưới 10 MB · Ảnh được cắt vuông ở giữa.", SwingConstants.CENTER);
        hint.setForeground(AppTheme.MUTED); avatarControls.add(hint, BorderLayout.SOUTH);
        avatarPage.add(avatarControls, BorderLayout.SOUTH); pages.add(avatarPage, "avatar");
        choose.addActionListener(e -> {
            JFileChooser picker = new JFileChooser(); picker.setAcceptAllFileFilterUsed(false);
            picker.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Ảnh PNG/JPG", "png", "jpg", "jpeg"));
            if (picker.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            try { selectedAvatar = AvatarImages.fromFile(picker.getSelectedFile()); preview.setIcon(icon(selectedAvatar, 128)); status.setText("Ảnh xem trước — bấm Lưu ảnh để cập nhật."); }
            catch (Exception ex) { status.setText(ex.getMessage()); }
        });
        reset.addActionListener(e -> { selectedAvatar = ""; preview.setIcon(icon("", 128)); status.setText("Bấm Lưu ảnh để dùng mèo mặc định."); });
        saveAvatar.addActionListener(e -> { saveAvatar.setEnabled(false); updateAvatar.accept(selectedAvatar); });
        JPanel passwordPage = new JPanel(new GridBagLayout()); passwordPage.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0; constraints.gridy = 0; constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL; constraints.insets = new Insets(0, 0, 20, 0);
        JLabel passwordTitle = new JLabel("Đổi mật khẩu", SwingConstants.CENTER);
        passwordTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20)); passwordTitle.setForeground(AppTheme.PRIMARY);
        passwordPage.add(passwordTitle, constraints);

        JPanel form = new JPanel(new GridLayout(3, 2, 10, 10)); form.setOpaque(false);
        for (Object[] row : new Object[][]{{"Mật khẩu hiện tại", current}, {"Mật khẩu mới (6–128 ký tự)", next}, {"Nhập lại mật khẩu mới", confirm}}) {
            JLabel label = new JLabel((String) row[0]); label.setLabelFor((JComponent) row[1]); form.add(label);
            JPasswordField field = (JPasswordField) row[1];
            field.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AppTheme.BORDER), new EmptyBorder(6, 8, 6, 8)));
            form.add(field);
        }
        form.setMaximumSize(new Dimension(Integer.MAX_VALUE, 128));
        constraints.gridy++; passwordPage.add(form, constraints);
        constraints.gridy++; constraints.fill = GridBagConstraints.NONE; passwordPage.add(change, constraints);
        pages.add(passwordPage, "password");
        change.addActionListener(e -> {
            char[] oldChars = current.getPassword(), newChars = next.getPassword(), confirmation = confirm.getPassword();
            try {
                if (oldChars.length == 0) { status.setText("Nhập mật khẩu hiện tại."); return; }
                if (newChars.length < 6 || newChars.length > 128) { status.setText("Mật khẩu mới cần từ 6 đến 128 ký tự."); return; }
                if (!java.util.Arrays.equals(newChars, confirmation)) { status.setText("Hai mật khẩu mới không khớp."); return; }
                change.setEnabled(false); password.accept(new String(oldChars), new String(newChars));
            } finally { java.util.Arrays.fill(oldChars, '\0'); java.util.Arrays.fill(newChars, '\0'); java.util.Arrays.fill(confirmation, '\0'); }
        });
        add(pages);
        JPanel footer = new JPanel(new BorderLayout(0, 8)); footer.setOpaque(false);
        status.setForeground(AppTheme.PRIMARY); footer.add(status, BorderLayout.NORTH);
        JPanel navigation = new JPanel(new BorderLayout()); navigation.setOpaque(false);
        back.addActionListener(e -> showPage("overview")); navigation.add(back, BorderLayout.WEST);
        JButton done = LobbyStyle.button("Đóng", false); done.addActionListener(e -> close.run());
        navigation.add(done, BorderLayout.EAST); footer.add(navigation); add(footer, BorderLayout.SOUTH);
        update(profile);
        showPage("overview");
    }
    void update(Protocol.Message profile) {
        savedAvatar = profile.string("avatar"); selectedAvatar = savedAvatar;
        avatar.setIcon(icon(savedAvatar, 104)); preview.setIcon(icon(savedAvatar, 128));
        int games = profile.integer("games"), wins = profile.integer("wins");
        String rate = String.format(java.util.Locale.forLanguageTag("vi-VN"), "%.1f%%", games == 0 ? 0.0 : wins * 100.0 / games);
        stats.setText("<html><div style='font-size:14px'>Điểm: <b>" + profile.integer("rating")
                + "</b><br><br>Thắng / Tổng trận: <b>" + wins + "/" + games
                + "</b><br><br>Tỷ lệ thắng: <b>" + rate + "</b><br><br>Số xu: <b>" + profile.integer("coins") + "</b></div></html>");
    }
    void result(Protocol.Message message) {
        status.setText(message.string("message"));
        if (message.string("action").equals("password")) {
            change.setEnabled(true);
            if (message.bool("success")) { current.setText(""); next.setText(""); confirm.setText(""); }
        } else saveAvatar.setEnabled(true);
    }
    static Icon icon(String encoded, int size) {
        int resolution = size * 2;
        var canvas = new java.awt.image.BufferedImage(resolution, resolution,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(new Color(239, 225, 255));
        g.fillRect(0, 0, resolution, resolution);
        boolean loaded = false;
        try {
            var image = AvatarImages.decode(encoded);
            if (image != null) {
                int edge = Math.min(image.getWidth(), image.getHeight());
                int x = (image.getWidth() - edge) / 2, y = (image.getHeight() - edge) / 2;
                g.drawImage(image, 0, 0, resolution, resolution, x, y, x + edge, y + edge, null);
                loaded = true;
            }
        } catch (Exception ignored) { }
        if (!loaded) LobbyStyle.cat(resolution).paintIcon(null, g, 0, 0);
        // Apply an antialiased alpha mask so the corners remain truly transparent.
        var mask = new java.awt.image.BufferedImage(resolution, resolution,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D m = mask.createGraphics();
        m.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        m.setColor(Color.WHITE);
        m.fillOval(1, 1, resolution - 2, resolution - 2);
        m.dispose();
        g.setComposite(AlphaComposite.DstIn);
        g.drawImage(mask, 0, 0, null);
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(190, 147, 246));
        g.setStroke(new BasicStroke(2f));
        g.drawOval(2, 2, resolution - 4, resolution - 4);
        g.dispose();
        return new ImageIcon(canvas.getScaledInstance(size, size, Image.SCALE_SMOOTH));
    }
}
