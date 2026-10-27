package memorygame.server;

import java.nio.file.Path;
import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;

public final class ServerMain {
    private ServerMain() {}

    public static void main(String[] args) throws Exception {
        int port = 12345;
        boolean headless = GraphicsEnvironment.isHeadless();
        for (String arg : args) {
            if (arg.equals("--headless")) headless = true;
            else port = Integer.parseInt(arg);
        }
        String dataDirectory = System.getenv("MEMORY_GAME_DATA_DIR");
        GameServer server = new GameServer(port, Path.of(
                dataDirectory == null || dataDirectory.isBlank() ? "data" : dataDirectory));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { server.close(); } catch (Exception ignored) {}
        }));
        if (!headless) {
            SwingUtilities.invokeLater(() -> {
                memorygame.common.AppTheme.install();
                new ServerMonitorFrame(server).setVisible(true);
            });
        }
        try {
            server.start();
        } catch (Exception e) {
            System.err.println("Không thể khởi động server: " + e.getMessage());
            if (!headless) {
                SwingUtilities.invokeLater(() -> {
                    for (java.awt.Window window : java.awt.Window.getWindows()) window.dispose();
                    javax.swing.JOptionPane.showMessageDialog(null,
                            "Không thể khởi động server: " + e.getMessage(),
                            "Lỗi Server", javax.swing.JOptionPane.ERROR_MESSAGE);
                });
            }
            server.close();
            throw e;
        }
    }
}
