package memorygame.client;
import memorygame.common.Protocol.*;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public final class ProfilePanelTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            int[] requests = {0};
            ProfilePanel panel = new ProfilePanel(Message.of(Type.PROFILE_UPDATE).with("username", "player")
                    .with("rating", 16).with("wins", 7).with("games", 12).with("coins", 210),
                    avatar -> requests[0]++, (oldPassword, newPassword) -> requests[0]++, () -> {});
            if (visible(panel, JPasswordField.class).size() != 0 || button(panel, "Lưu ảnh") != null)
                throw new AssertionError("Editors visible on overview");
            if (!visible(panel, JLabel.class).stream().anyMatch(c -> ((JLabel)c).getText() != null
                    && ((JLabel)c).getText().contains("58,3%"))) throw new AssertionError("Win rate missing");
            button(panel, "Đổi mật khẩu").doClick();
            if (visible(panel, JPasswordField.class).size() != 3 || button(panel, "Lưu ảnh") != null)
                throw new AssertionError("Password page");
            button(panel, "Đổi mật khẩu").doClick();
            if (requests[0] != 0) throw new AssertionError("Invalid password submitted");
            button(panel, "← Quay lại").doClick();
            button(panel, "Đổi ảnh đại diện").doClick();
            if (button(panel, "Lưu ảnh") == null || visible(panel, JPasswordField.class).size() != 0)
                throw new AssertionError("Avatar page");
            button(panel, "← Quay lại").doClick();
            if (requests[0] != 0) throw new AssertionError("Navigation submitted changes");
            panel.update(Message.of(Type.PROFILE_UPDATE).with("games", 0).with("wins", 0));
            if (!visible(panel, JLabel.class).stream().anyMatch(c -> ((JLabel)c).getText() != null
                    && ((JLabel)c).getText().contains("0,0%"))) throw new AssertionError("Zero-game rate");
        });
        System.out.println("PROFILE_PANEL_OK overview separateEditors navigation winRate zeroGames");
    }
    private static java.util.List<Component> visible(Container parent, Class<?> type) {
        var result = new ArrayList<Component>();
        for (Component c : parent.getComponents()) if (c.isVisible()) {
            if (type.isInstance(c)) result.add(c);
            if (c instanceof Container child) result.addAll(visible(child, type));
        }
        return result;
    }
    private static JButton button(Container panel, String title) {
        return visible(panel, JButton.class).stream().map(c -> (JButton)c)
                .filter(b -> title.equals(b.getText())).findFirst().orElse(null);
    }
}
