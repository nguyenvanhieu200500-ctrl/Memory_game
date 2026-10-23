package memorygame.test;

import memorygame.common.Protocol.Message;
import memorygame.common.Protocol.Type;
import memorygame.server.GameServer;
import memorygame.common.GameColors;
import java.util.List;
import java.util.Map;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class SmokeTest {
    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 12346 : Integer.parseInt(args[0]);
        int boardSize = args.length < 2 ? 4 : Integer.parseInt(args[1]);
        run(null, port, boardSize);
    }

    static void run(GameServer server, int port, int boardSize) throws Exception {
        int totalPairs = boardSize * boardSize / 2;
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        try (TestClient one = new TestClient(port); TestClient two = new TestClient(port)) {
            String oneName = "testA" + boardSize + "_" + suffix;
            String twoName = "testB" + boardSize + "_" + suffix;
            one.send(Message.of(Type.REGISTER).with("username", oneName).with("password", "test123"));
            two.send(Message.of(Type.REGISTER).with("username", twoName).with("password", "test123"));
            one.until(Type.REGISTER_OK);
            two.until(Type.REGISTER_OK);
            one.send(Message.of(Type.LOGIN).with("username", oneName).with("password", "test123"));
            two.send(Message.of(Type.LOGIN).with("username", twoName).with("password", "test123"));
            one.until(Type.LOGIN_OK);
            two.until(Type.LOGIN_OK);

            one.send(Message.of(Type.ROOM_CREATE).with("boardSize", boardSize));
            Message room = one.until(Type.ROOM_UPDATE);
            String code = room.string("code");
            two.send(Message.of(Type.ROOM_JOIN).with("code", code));
            two.until(Type.ROOM_UPDATE);
            one.until(Type.ROOM_UPDATE);

            one.send(Message.of(Type.ROOM_READY).with("ready", true));
            two.send(Message.of(Type.ROOM_READY).with("ready", true));
            one.until(Type.ROOM_UPDATE);
            Message ready = one.until(Type.ROOM_UPDATE);
            long startRequestedAt = System.nanoTime();
            one.send(Message.of(Type.ROOM_START));
            Message countdown = one.until(Type.ROOM_UPDATE);
            if (countdown.longValue("countdownDeadline") <= System.currentTimeMillis()) {
                throw new AssertionError("Phòng riêng không phát thời điểm đếm ngược bắt đầu");
            }

            Message gameOne = one.until(Type.GAME_START);
            Message gameTwo = two.until(Type.GAME_START);
            long countdownMillis = (System.nanoTime() - startRequestedAt) / 1_000_000L;
            if (countdownMillis < 2_700) {
                throw new AssertionError("Phòng riêng vào trận trước khi đếm ngược đủ 3 giây: "
                        + countdownMillis + "ms");
            }
            Message turn = one.until(Type.TURN_UPDATE);
            two.until(Type.TURN_UPDATE);
            GameServer.RoomSnapshot initial = server == null ? null : roomSnapshot(server, code);
            if (initial != null) {
                if (initial.cards().size() != boardSize * boardSize || initial.players().size() != 2
                        || !initial.current().equals(turn.string("current"))
                        || initial.cards().stream().anyMatch(card -> card.value() != -1 || card.state() != 0)) {
                    throw new AssertionError("Monitor: incorrect initial game state or exposed hidden cards");
                }
                List<String> monitorPlayers = initial.players().stream().map(GameServer.PlayerSnapshot::name).toList();
                List<String> clientOnePlayers = gameOne.value("players");
                List<String> clientTwoPlayers = gameTwo.value("players");
                if (!monitorPlayers.equals(clientOnePlayers) || !monitorPlayers.equals(clientTwoPlayers)
                        || !GameColors.forPlayers(monitorPlayers).equals(GameColors.forPlayers(clientOnePlayers))
                        || initial.deadline() != turn.longValue("deadline")) {
                    throw new AssertionError("Monitor and clients disagree on player order, colors or turn deadline");
                }
                try {
                    initial.cards().clear();
                    throw new AssertionError("Monitor snapshot must be immutable");
                } catch (UnsupportedOperationException expected) { }
            }
            TestClient current = turn.string("current").startsWith("testA") ? one : two;
            current.send(Message.of(Type.CARD_PICK).with("index", 0));
            Message first = current.until(Type.CARD_REVEALED);
            if (first.integer("value") < 1 || first.integer("value") > totalPairs) throw new AssertionError("Giá trị thẻ sai");
            if (server != null) {
                GameServer.RoomSnapshot revealed = roomSnapshot(server, code);
                if (revealed.cards().get(0).value() != first.integer("value")
                        || revealed.cards().get(0).state() != 1 || initial.cards().get(0).value() != -1) {
                    throw new AssertionError("Monitor: reveal not updated or previous snapshot mutated");
                }
            }
            current.send(Message.of(Type.CARD_PICK).with("index", 1));
            current.until(Type.CARD_REVEALED);
            Message matchResult = current.until(Type.MATCH_RESULT);
            if (server != null) {
                GameServer.RoomSnapshot resolved = roomSnapshot(server, code);
                Map<String, Integer> scores = matchResult.value("scores");
                for (GameServer.PlayerSnapshot player : resolved.players()) {
                    if (player.score() != scores.get(player.name())) {
                        throw new AssertionError("Monitor and clients disagree on scores");
                    }
                }
                if (matchResult.bool("match")) {
                    for (int index : new int[]{0, 1}) {
                        if (resolved.cards().get(index).state() != 2
                                || !matchResult.string("player").equals(resolved.cards().get(index).owner())) {
                            throw new AssertionError("Monitor and clients disagree on matched card owner");
                        }
                    }
                }
            }
            if (matchResult.string("player").isBlank()) throw new AssertionError("Thiếu chủ sở hữu lượt ghép");
            if (((int[]) gameOne.get("values"))[0] != -1 || ((int[]) gameTwo.get("values"))[0] != -1) {
                throw new AssertionError("Server đã làm lộ thẻ úp");
            }
            if (gameOne.integer("boardSize") != boardSize
                    || ((int[]) gameOne.get("states")).length != boardSize * boardSize) {
                throw new AssertionError("Bàn không đúng kích thước");
            }
            String[] owners = gameOne.value("owners");
            if (owners == null || owners.length != boardSize * boardSize
                    || Arrays.stream(owners).anyMatch(Objects::nonNull)) {
                throw new AssertionError("Dữ liệu màu sở hữu thẻ ban đầu không hợp lệ");
            }
            System.out.println("SMOKE_TEST_OK room=" + code + " privateCountdown=3s firstCard="
                    + first.integer("value"));
        }
    }

    private static GameServer.RoomSnapshot roomSnapshot(GameServer server, String code) {
        return server.monitorSnapshot().rooms().stream().filter(room -> room.code().equals(code))
                .findFirst().orElseThrow(() -> new AssertionError("Monitor: missing room " + code));
    }

    private static final class TestClient implements AutoCloseable {
        private final Socket socket;
        private final ObjectOutputStream output;
        private final ObjectInputStream input;

        TestClient(int port) throws Exception {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(20000);
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());
        }

        void send(Message message) throws Exception {
            output.reset();
            output.writeObject(message);
            output.flush();
        }

        Message until(Type type) throws Exception {
            for (int i = 0; i < 30; i++) {
                Message message = (Message) input.readObject();
                if (message.type() == Type.ERROR) throw new AssertionError(message.string("message"));
                if (message.type() == type) return message;
            }
            throw new AssertionError("Không nhận được " + type);
        }

        @Override public void close() throws Exception {
            socket.close();
        }
    }
}
