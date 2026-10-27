package memorygame.test;

import memorygame.common.Protocol.*;
import memorygame.server.GameServer;
import java.nio.file.*;
import java.util.*;

/** Runs only through DatabaseFlowsTest, in an isolated MySQL database. */
public final class GameRecoveryTest {
    static GameServer server;
    static Thread thread;
    static Path dir;
    static String matchId;
    static void start() throws Exception { server = new GameServer(ProfileTest.PORT, dir); thread = ProfileTest.start(server); }
    static void stop() throws Exception { server.close(); thread.join(2000); }
    static void login(ProfileTest.Client c, String name) throws Exception {
        c.send(Message.of(Type.LOGIN).with("username", name).with("password", "secret123")); c.await(Type.LOGIN_OK);
    }
    static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    static Object field(Object target, String name) throws Exception {
        var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    static int[] secretBoard() throws Exception {
        var rooms = (Map<?, ?>) field(server, "rooms");
        return ((int[]) field(field(rooms.values().iterator().next(), "game"), "values")).clone();
    }
    static void resume(ProfileTest.Client a, ProfileTest.Client b) throws Exception {
        a.send(Message.of(Type.GAME_RESUME).with("matchId", matchId));
        check(a.await(Type.GAME_PAUSED).bool("paused"), "One vote resumed game");
        b.send(Message.of(Type.GAME_RESUME).with("matchId", matchId));
        check(!a.await(Type.GAME_START).bool("paused"), "Both votes did not resume");
        b.await(Type.GAME_START); a.await(Type.TURN_UPDATE); b.await(Type.TURN_UPDATE);
    }
    public static void main(String[] args) throws Exception {
        dir = Files.createTempDirectory("game-recovery-"); start();
        int[] values; String current;
        try (var a = new ProfileTest.Client(); var b = new ProfileTest.Client()) {
            ServerInterruptionTest.login(a, "recover_a"); ServerInterruptionTest.login(b, "recover_b");
            a.send(Message.of(Type.ROOM_CREATE).with("boardSize", 4).with("capacity", 2).with("cardTheme", "FRUIT"));
            b.send(Message.of(Type.ROOM_JOIN).with("code", a.await(Type.ROOM_UPDATE).string("code")));
            ServerInterruptionTest.room(a, 2, false);
            a.send(Message.of(Type.ROOM_READY).with("ready", true)); b.send(Message.of(Type.ROOM_READY).with("ready", true));
            ServerInterruptionTest.room(a, 2, true); a.send(Message.of(Type.ROOM_START));
            Message initial = a.await(Type.GAME_START); b.await(Type.GAME_START);
            matchId = initial.string("matchId"); current = initial.string("current");
            values = secretBoard();
            (current.equals("recover_a") ? a : b).send(Message.of(Type.CARD_PICK).with("index", 0));
            a.await(Type.CARD_REVEALED); b.await(Type.CARD_REVEALED);
            a.send(Message.of(Type.CHAT_SEND).with("text", "Giữ chat khi phục hồi")); a.await(Type.CHAT_MESSAGE);
            stop();
        } finally { stop(); }
        for (int phase = 0; phase < 3; phase++) {
            start();
            try (var a = new ProfileTest.Client(); var b = new ProfileTest.Client()) {
                login(a, "recover_a"); login(b, "recover_b");
                Message state = a.await(Type.GAME_START); b.await(Type.GAME_START);
                a.await(Type.TURN_UPDATE); b.await(Type.TURN_UPDATE);
                check(state.bool("paused") && state.string("matchId").equals(matchId), "Recovery identity/pause");
                check(Arrays.equals(values, secretBoard()), "Hidden board changed");
                int[] states = state.value("states"), visible = state.value("values");
                for (int i = 0; i < states.length; i++) if (states[i] == 0) check(visible[i] == -1, "Hidden card leaked");
                List<ChatEntry> chat = state.value("chat"); check(chat.size() == 1, "Chat lost");
                a.send(Message.of(Type.CARD_PICK).with("index", 1)); a.await(Type.ERROR);
                a.send(Message.of(Type.GAME_RESUME).with("matchId", "stale")); a.await(Type.ERROR);
                if (phase == 0) check(states[0] == 1 && state.string("current").equals(current), "First card/turn lost");
                if (phase == 1) check(state.bool("resolving"), "Wrong pair phase lost");
                if (phase == 2) check(state.integer("pairs") == 1, "Matched pair score lost");
                resume(a, b);
                if (phase == 0) {
                    int wrong = 1; while (values[wrong] == values[0]) wrong++;
                    (current.equals("recover_a") ? a : b).send(Message.of(Type.CARD_PICK).with("index", wrong));
                    a.await(Type.MATCH_RESULT); stop();
                } else if (phase == 1) {
                    a.await(Type.CARD_HIDDEN); current = a.await(Type.TURN_UPDATE).string("current");
                    int partner = 1; while (values[partner] != values[0]) partner++;
                    var actor = current.equals("recover_a") ? a : b;
                    actor.send(Message.of(Type.CARD_PICK).with("index", 0)); a.await(Type.CARD_REVEALED);
                    actor.send(Message.of(Type.CARD_PICK).with("index", partner)); a.await(Type.MATCH_RESULT); stop();
                } else {
                    a.await(Type.TURN_UPDATE);
                    b.send(Message.of(Type.ROOM_LEAVE)); a.await(Type.GAME_END);
                    try (var c = GamePersistenceProtocolTest.connection()) {
                        check(ServerInterruptionTest.number(c, "SELECT COUNT(*) FROM matches WHERE status='COMPLETED'") == 1, "Completion missing");
                        check(ServerInterruptionTest.number(c, "SELECT COUNT(*) FROM match_snapshots") == 0, "Snapshot not removed");
                        check(ServerInterruptionTest.number(c, "SELECT SUM(games) FROM users") == 2, "Duplicate rewards");
                    }
                }
            } finally { stop(); }
        }
        System.out.println("GAME_RECOVERY_OK identity hiddenBoard firstCard wrongPair matchedPair chat votes pausedActions completion rewards");
    }
}
