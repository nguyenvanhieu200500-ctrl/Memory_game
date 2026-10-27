package memorygame.test;

import memorygame.common.Protocol.*;
import memorygame.server.GameServer;
import java.nio.file.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class GameCrashRecoveryTest {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            new GameServer(ProfileTest.PORT, Path.of(args[0])).start(); return;
        }
        Path dir = Files.createTempDirectory("game-crash-");
        Process child = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp",
                System.getProperty("java.class.path"), GameCrashRecoveryTest.class.getName(), dir.toString()).inheritIO().start();
        String id; int value;
        try {
            boolean ready = false;
            for (int i = 0; i < 100; i++) {
                try (var socket = new Socket("127.0.0.1", ProfileTest.PORT)) { ready = true; break; }
                catch (Exception e) { Thread.sleep(50); }
            }
            GameRecoveryTest.check(ready, "Child server not ready");
            try (var a = new ProfileTest.Client(); var b = new ProfileTest.Client()) {
                ServerInterruptionTest.login(a, "crash_a"); ServerInterruptionTest.login(b, "crash_b");
                a.send(Message.of(Type.QUEUE_JOIN).with("size", 2).with("boardSize", 4).with("cardTheme", "FRUIT"));
                b.send(Message.of(Type.QUEUE_JOIN).with("size", 2).with("boardSize", 4).with("cardTheme", "FRUIT"));
                ServerInterruptionTest.room(a, 2, false);
                a.send(Message.of(Type.ROOM_READY).with("ready", true)); b.send(Message.of(Type.ROOM_READY).with("ready", true));
                Message start = a.await(Type.GAME_START); id = start.string("matchId");
                (start.string("current").equals("crash_a") ? a : b).send(Message.of(Type.CARD_PICK).with("index", 0));
                value = a.await(Type.CARD_REVEALED).integer("value");
                child.destroyForcibly(); GameRecoveryTest.check(child.waitFor(5, TimeUnit.SECONDS), "Child not terminated");
            }
        } finally { if (child.isAlive()) { child.destroyForcibly(); child.waitFor(); } }
        GameServer server = new GameServer(ProfileTest.PORT, dir); Thread thread = ProfileTest.start(server);
        try (var a = new ProfileTest.Client()) {
            GameRecoveryTest.login(a, "crash_a"); Message state = a.await(Type.GAME_START);
            int[] states = state.value("states"), values = state.value("values");
            GameRecoveryTest.check(state.bool("paused") && id.equals(state.string("matchId")), "Crash identity lost");
            GameRecoveryTest.check(states[0] == 1 && values[0] == value, "Acknowledged card lost after hard kill");
            try (var duplicate = new GameServer(ProfileTest.PORT + 1, dir)) {
                boolean rejected = false;
                try { duplicate.start(); } catch (IllegalStateException expected) { rejected = true; }
                GameRecoveryTest.check(rejected, "Second server acquired database");
            }
        } finally { server.close(); thread.join(2000); }
        try (var c = GamePersistenceProtocolTest.connection(); var sql = c.createStatement()) {
            sql.executeUpdate("UPDATE match_snapshots SET checksum=REPEAT('0',64)");
        }
        server = new GameServer(ProfileTest.PORT, dir); thread = ProfileTest.start(server);
        try (var a = new ProfileTest.Client(); var c = GamePersistenceProtocolTest.connection()) {
            GameRecoveryTest.login(a, "crash_a");
            GameRecoveryTest.check(ServerInterruptionTest.number(c, "SELECT COUNT(*) FROM matches WHERE status='INTERRUPTED'") == 1, "Corruption not rejected");
            GameRecoveryTest.check(ServerInterruptionTest.number(c, "SELECT SUM(games+coins) FROM users") == 0, "Corruption awarded rewards");
        } finally { server.close(); thread.join(2000); }
        System.out.println("GAME_CRASH_RECOVERY_OK hardKill acknowledgedCard databaseLock corruptSnapshot noRewards");
    }
}
