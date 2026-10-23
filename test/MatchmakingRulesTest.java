package memorygame.test;

import memorygame.common.Protocol.Message;
import memorygame.common.Protocol.Type;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class MatchmakingRulesTest {
    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 12347 : Integer.parseInt(args[0]);
        verifyThemeQueues(port);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String aName = "matchA_" + suffix;
        String bName = "matchB_" + suffix;
        String cName = "matchC_" + suffix;
        String dName = "matchD_" + suffix;

        try (QueueClient a = new QueueClient(port, aName);
             QueueClient b = new QueueClient(port, bName);
             QueueClient c = new QueueClient(port, cName);
             QueueClient d = new QueueClient(port, dName)) {
            a.login(); b.login(); c.login(); d.login();
            a.await(Type.LOGIN_OK); b.await(Type.LOGIN_OK); c.await(Type.LOGIN_OK); d.await(Type.LOGIN_OK);

            a.queue(2, 4);
            b.queue(2, 6);
            a.expectNo(Type.GAME_START, 700);
            b.expectNo(Type.GAME_START, 700);

            c.queue(2, 4);
            Message roomA = a.await(Type.ROOM_UPDATE, message -> message.bool("matchmaking"));
            Message roomC = c.await(Type.ROOM_UPDATE, message -> message.bool("matchmaking"));
            assertServerManaged(roomA, 2, 4);
            assertServerManaged(roomC, 2, 4);
            a.ready();
            long readyAt = System.nanoTime();
            c.ready();
            a.await(Type.ROOM_UPDATE, message -> message.longValue("countdownDeadline") > System.currentTimeMillis());
            c.await(Type.ROOM_UPDATE, message -> message.longValue("countdownDeadline") > System.currentTimeMillis());
            Message gameA = a.await(Type.GAME_START);
            Message gameC = c.await(Type.GAME_START);
            assertCountdownElapsed(readyAt);
            if (gameA.integer("boardSize") != 4 || gameC.integer("boardSize") != 4) {
                throw new AssertionError("Ghép sai kích thước bàn 4x4");
            }

            d.queue(2, 6);
            Message roomB = b.await(Type.ROOM_UPDATE, message -> message.bool("matchmaking"));
            Message roomD = d.await(Type.ROOM_UPDATE, message -> message.bool("matchmaking"));
            assertServerManaged(roomB, 2, 6);
            assertServerManaged(roomD, 2, 6);
            b.ready();
            readyAt = System.nanoTime();
            d.ready();
            b.await(Type.ROOM_UPDATE, message -> message.longValue("countdownDeadline") > System.currentTimeMillis());
            d.await(Type.ROOM_UPDATE, message -> message.longValue("countdownDeadline") > System.currentTimeMillis());
            Message gameB = b.await(Type.GAME_START);
            Message gameD = d.await(Type.GAME_START);
            assertCountdownElapsed(readyAt);
            if (gameB.integer("boardSize") != 6 || gameD.integer("boardSize") != 6) {
                throw new AssertionError("Ghép sai kích thước bàn 6x6");
            }

            String current = a.await(Type.TURN_UPDATE).string("current");
            c.await(Type.TURN_UPDATE);
            Map<String, QueueClient> firstMatch = Map.of(aName, a, cName, c);
            QueueClient currentClient = firstMatch.get(current);
            QueueClient waitingClient = current.equals(aName) ? c : a;

            waitingClient.send(Message.of(Type.CARD_PICK).with("index", 0));
            Message wrongTurn = waitingClient.await(Type.ERROR);
            if (!wrongTurn.string("message").contains("lượt")) {
                throw new AssertionError("Không từ chối người chơi sai lượt");
            }

            currentClient.send(Message.of(Type.CARD_PICK).with("index", 0));
            a.await(Type.CARD_REVEALED);
            currentClient.send(Message.of(Type.CARD_PICK).with("index", 0));
            Message duplicateCard = currentClient.await(Type.ERROR);
            if (!duplicateCard.string("message").contains("không hợp lệ")) {
                throw new AssertionError("Không từ chối chọn trùng thẻ");
            }
            System.out.println("MATCHMAKING_RULES_OK serverOwned readyCountdown=3s isolatedBoards=4x4,6x6 wrongTurnRejected duplicateRejected");
        }
    }

    private static void verifyThemeQueues(int port) throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        try (QueueClient a = new QueueClient(port, "themeA_" + id);
             QueueClient b = new QueueClient(port, "themeB_" + id);
             QueueClient c = new QueueClient(port, "themeC_" + id);
             QueueClient d = new QueueClient(port, "themeD_" + id)) {
            for (QueueClient client : new QueueClient[]{a, b, c, d}) { client.login(); client.await(Type.LOGIN_OK); }
            a.send(Message.of(Type.QUEUE_JOIN).with("size", 2).with("boardSize", 4).with("cardTheme", "INVALID"));
            a.await(Type.ERROR);
            a.queue(2, 4, "FOOD");
            b.queue(2, 4, "ANIMALS");
            a.expectNo(Type.ROOM_UPDATE, 400); b.expectNo(Type.ROOM_UPDATE, 400);
            c.queue(3, 4, "FOOD");
            a.expectNo(Type.ROOM_UPDATE, 400); c.expectNo(Type.ROOM_UPDATE, 400);
            c.send(Message.of(Type.QUEUE_LEAVE));
            c.queue(2, 4, "FOOD"); d.queue(2, 4, "ANIMALS");
            Message ra = a.await(Type.ROOM_UPDATE, m -> m.bool("matchmaking"));
            Message rb = b.await(Type.ROOM_UPDATE, m -> m.bool("matchmaking"));
            Message rc = c.await(Type.ROOM_UPDATE, m -> m.bool("matchmaking"));
            Message rd = d.await(Type.ROOM_UPDATE, m -> m.bool("matchmaking"));
            if (!ra.string("code").equals(rc.string("code")) || !rb.string("code").equals(rd.string("code"))
                    || ra.string("code").equals(rb.string("code"))) throw new AssertionError("Themes mixed in rooms");
            for (Message room : new Message[]{ra, rc}) if (!"FOOD".equals(room.string("cardTheme")))
                throw new AssertionError("Food room lost theme");
            for (Message room : new Message[]{rb, rd}) if (!"ANIMALS".equals(room.string("cardTheme")))
                throw new AssertionError("Animal room lost theme");
            for (QueueClient client : new QueueClient[]{a, b, c, d}) client.ready();
            for (QueueClient client : new QueueClient[]{a, c}) if (!"FOOD".equals(client.await(Type.GAME_START).string("cardTheme")))
                throw new AssertionError("Game did not propagate FOOD");
            for (QueueClient client : new QueueClient[]{b, d}) if (!"ANIMALS".equals(client.await(Type.GAME_START).string("cardTheme")))
                throw new AssertionError("Game did not propagate ANIMALS");
            System.out.println("THEME_QUEUES_OK isolatedThemes isolatedPlayerCounts cancelRejoin invalidTheme sharedGameTheme");
        }
    }

    private static void assertServerManaged(Message room, int players, int boardSize) {
        if (!room.bool("matchmaking") || !room.string("host").isBlank()
                || room.integer("targetPlayers") != players || room.integer("boardSize") != boardSize) {
            throw new AssertionError("Phòng ghép ngẫu nhiên không do Server quản lý đúng cấu hình");
        }
    }

    private static void assertCountdownElapsed(long readyAt) {
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - readyAt);
        if (elapsedMillis < 2_700) {
            throw new AssertionError("Server vào trận trước khi đếm ngược đủ 3 giây: " + elapsedMillis + "ms");
        }
    }

    private static final class QueueClient implements AutoCloseable {
        private final String username;
        private final Socket socket;
        private final ObjectOutputStream output;
        private final ObjectInputStream input;
        private final LinkedBlockingQueue<Message> messages = new LinkedBlockingQueue<>();

        QueueClient(int port, String username) throws Exception {
            this.username = username;
            socket = new Socket("127.0.0.1", port);
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());
            Thread reader = new Thread(() -> {
                try {
                    while (!socket.isClosed()) messages.offer((Message) input.readObject());
                } catch (Exception ignored) { }
            }, "matchmaking-test-reader");
            reader.setDaemon(true);
            reader.start();
        }

        void login() throws Exception {
            send(Message.of(Type.REGISTER).with("username", username).with("password", "test123"));
            await(Type.REGISTER_OK);
            send(Message.of(Type.LOGIN).with("username", username).with("password", "test123"));
        }

        void queue(int players, int boardSize) throws Exception {
            send(Message.of(Type.QUEUE_JOIN).with("size", players).with("boardSize", boardSize));
        }

        void queue(int players, int boardSize, String theme) throws Exception {
            send(Message.of(Type.QUEUE_JOIN).with("size", players).with("boardSize", boardSize).with("cardTheme", theme));
        }

        void ready() throws Exception {
            send(Message.of(Type.ROOM_READY).with("ready", true));
        }

        synchronized void send(Message message) throws Exception {
            output.reset();
            output.writeObject(message);
            output.flush();
        }

        Message await(Type type) throws Exception {
            return await(type, message -> true);
        }

        Message await(Type type, Predicate<Message> predicate) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < deadline) {
                Message message = messages.poll(1, TimeUnit.SECONDS);
                if (message == null) continue;
                if (message.type() == type && predicate.test(message)) return message;
                if (message.type() == Type.ERROR) throw new AssertionError(message.string("message"));
            }
            throw new AssertionError("Timeout chờ " + type);
        }

        void expectNo(Type type, long milliseconds) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(milliseconds);
            while (System.nanoTime() < deadline) {
                Message message = messages.poll(50, TimeUnit.MILLISECONDS);
                if (message != null && message.type() == type) {
                    throw new AssertionError("Nhận " + type + " dù chế độ ghép trận khác nhau");
                }
            }
        }

        @Override public void close() throws Exception {
            socket.close();
        }
    }
}
