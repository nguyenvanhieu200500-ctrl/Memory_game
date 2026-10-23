package memorygame.client;

import javax.swing.*;

public final class ClientMain {
    private ClientMain() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            memorygame.common.AppTheme.install();
            new GameFrame().setVisible(true);
        });
    }
}
