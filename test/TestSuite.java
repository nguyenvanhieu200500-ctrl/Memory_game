package memorygame.test;

import memorygame.server.GameServer;

import java.net.Socket;
import java.nio.file.Path;

public final class TestSuite {
    private static final java.util.List<String> failures=new java.util.ArrayList<>();
    @FunctionalInterface interface Checked { void run() throws Exception; }
    private static void check(String name,Checked test) {
        try {test.run();}catch(Throwable failure){failures.add(name);System.err.println("CHECK_FAILED "+name+": "+failure);}
    }
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
            check("memorygame.server.ServerMonitorViewTest.main", () -> memorygame.server.ServerMonitorViewTest.main(new String[0]));
            check("memorygame.client.HistoryPanelTest.main", () -> memorygame.client.HistoryPanelTest.main(new String[0]));
            check("memorygame.client.RankingPanelTest.main", () -> memorygame.client.RankingPanelTest.main(new String[0]));
            check("memorygame.client.CardThemeTest.main", () -> memorygame.client.CardThemeTest.main(new String[0]));
            check("memorygame.client.CreateRoomPanelTest.main", () -> memorygame.client.CreateRoomPanelTest.main(new String[0]));
            check("memorygame.client.RoomInvitePanelTest.main", () -> memorygame.client.RoomInvitePanelTest.main(new String[0]));
            check("memorygame.client.InvitationPanelTest.main", () -> memorygame.client.InvitationPanelTest.main(new String[0]));
            check("RoomSettingsTest.run", () -> RoomSettingsTest.run(server, port));
            check("ComboContrastTest.main", () -> ComboContrastTest.main(new String[0]));
            check("ShopEffectsTest.main", () -> ShopEffectsTest.main(new String[0]));
            check("ChatPanelTest.main", () -> ChatPanelTest.main(new String[0]));
            check("memorygame.client.WhisperHistoryTest.main", () -> memorygame.client.WhisperHistoryTest.main(new String[0]));
            check("memorygame.client.UnreadMessagesTest.main", () -> memorygame.client.UnreadMessagesTest.main(new String[0]));
            check("memorygame.client.StreamingMusicTest.main", () -> memorygame.client.StreamingMusicTest.main(new String[0]));
            check("ChatTest.run", () -> ChatTest.run(server, port));
            check("SocialTest.main", () -> SocialTest.main(new String[0]));
            check("SmokeTest.run", () -> SmokeTest.run(server, port, 4));
            check("SmokeTest.run", () -> SmokeTest.run(server, port, 6));
            check("SmokeTest.run", () -> SmokeTest.run(server, port, 8));
            check("MatchmakingRulesTest.main", () -> MatchmakingRulesTest.main(new String[]{String.valueOf(port)}));
            check("FullGameReplayTest.main", () -> FullGameReplayTest.main(new String[]{String.valueOf(port)}));
            if(!failures.isEmpty())throw new AssertionError("FAILED_CHECKS: "+failures);
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
