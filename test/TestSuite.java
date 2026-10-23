package memorygame.test;

import memorygame.server.GameServer;

import java.net.Socket;
import java.nio.file.Path;

public final class TestSuite {
    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 12347 : Integer.parseInt(args[0]);
        GameServer server = new GameServer(port, Path.of(".test-data"));
        Thread serverThread = new Thread(() -> {
            try { server.start(); }
            catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) e.printStackTrace();
            }
        }, "integration-test-server");
        serverThread.setDaemon(true);
        serverThread.start();
        waitForServer(port);

        try {
            memorygame.server.ServerMonitorViewTest.main(new String[0]);
            memorygame.client.HistoryPanelTest.main(new String[0]);
            memorygame.client.RankingPanelTest.main(new String[0]);
            memorygame.client.CardThemeTest.main(new String[0]);
            memorygame.client.CreateRoomPanelTest.main(new String[0]);
            memorygame.client.RoomInvitePanelTest.main(new String[0]);
            memorygame.client.InvitationPanelTest.main(new String[0]);
            RoomSettingsTest.run(server, port);
            ComboContrastTest.main(new String[0]);
            ShopEffectsTest.main(new String[0]);
            ChatPanelTest.main(new String[0]);
            memorygame.client.WhisperHistoryTest.main(new String[0]);
            memorygame.client.UnreadMessagesTest.main(new String[0]);
            memorygame.client.StreamingMusicTest.main(new String[0]);
            ChatTest.run(server, port);
            SocialTest.main(new String[0]);
            SmokeTest.run(server, port, 4);
            SmokeTest.run(server, port, 6);
            SmokeTest.run(server, port, 8);
            MatchmakingRulesTest.main(new String[]{String.valueOf(port)});
            FullGameReplayTest.main(new String[]{String.valueOf(port)});
            System.out.println("ALL_TESTS_PASSED");
        } finally {
            server.close();
            serverThread.join(2000);
        }
    }

    private static void waitForServer(int port) throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            try (Socket ignored = new Socket("127.0.0.1", port)) {
                return;
            } catch (Exception e) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("Server kiểm thử không khởi động.");
    }
}
