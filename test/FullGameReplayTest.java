package memorygame.test;

import memorygame.common.Protocol.Message;
import memorygame.common.Protocol.Type;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class FullGameReplayTest {
    public static void main(String[] args) throws Exception {
        int port = args.length == 0 ? 12347 : Integer.parseInt(args[0]);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String nameOne = "fullA_" + suffix;
        String nameTwo = "fullB_" + suffix;

        try (AsyncClient one = new AsyncClient(port); AsyncClient two = new AsyncClient(port)) {
            one.login(nameOne);
            two.login(nameTwo);
            Message loginOne = one.await(Type.LOGIN_OK);
            Message loginTwo = two.await(Type.LOGIN_OK);
            if (loginOne.integer("coins") != 0 || loginTwo.integer("coins") != 0) {
                throw new AssertionError("Tài khoản mới phải bắt đầu với 0 xu");
            }

            one.send(Message.of(Type.ROOM_CREATE).with("boardSize", 4));
            String roomCode = one.await(Type.ROOM_UPDATE).string("code");
            two.send(Message.of(Type.ROOM_JOIN).with("code", roomCode));
            one.await(Type.ROOM_UPDATE, FullGameReplayTest::hasTwoPlayers);

            one.send(Message.of(Type.ROOM_READY).with("ready", true));
            one.await(Type.ROOM_UPDATE);
            two.send(Message.of(Type.ROOM_READY).with("ready", true));
            one.await(Type.ROOM_UPDATE, FullGameReplayTest::everyoneReady);
            one.send(Message.of(Type.ROOM_START));

            Message start = one.await(Type.GAME_START);
            int[] hiddenValues = start.value("values");
            if (hiddenValues.length != 16 || Arrays.stream(hiddenValues).anyMatch(value -> value != -1)) {
                throw new AssertionError("Server làm lộ giá trị thẻ trước khi lật");
            }
            Message turn = one.await(Type.TURN_UPDATE);
            String current = turn.string("current");

            int[] known = new int[16];
            boolean[] matched = new boolean[16];
            int matchedPairs = 0;
            Map<String, AsyncClient> clients = Map.of(nameOne, one, nameTwo, two);

            while (matchedPairs < 8) {
                int first = knownPairFirst(known, matched);
                if (first < 0) first = firstUnknown(known, matched, -1);
                if (first < 0) throw new AssertionError("Không còn thẻ hợp lệ để chọn");

                AsyncClient actor = clients.get(current);
                actor.send(Message.of(Type.CARD_PICK).with("index", first));
                Message firstReveal = one.await(Type.CARD_REVEALED);
                int firstValue = firstReveal.integer("value");
                known[first] = firstValue;

                int second = knownPartner(first, firstValue, known, matched);
                if (second < 0) second = firstUnknown(known, matched, first);
                if (second < 0) second = firstKnownUnmatchedOther(known, matched, first);
                if (second < 0) throw new AssertionError("Không tìm được thẻ thứ hai");

                actor.send(Message.of(Type.CARD_PICK).with("index", second));
                Message secondReveal = one.await(Type.CARD_REVEALED);
                int secondValue = secondReveal.integer("value");
                known[second] = secondValue;
                Message result = one.await(Type.MATCH_RESULT);

                if (result.bool("match")) {
                    if (firstValue != secondValue) throw new AssertionError("Server báo ghép sai thành đúng");
                    matched[first] = matched[second] = true;
                    matchedPairs++;
                } else {
                    if (firstValue == secondValue) throw new AssertionError("Server báo cặp đúng thành sai");
                    one.await(Type.CARD_HIDDEN);
                }

                if (matchedPairs < 8) current = one.await(Type.TURN_UPDATE).string("current");
            }

            Message end = one.await(Type.GAME_END);
            List<Map<String, Object>> results = end.value("results");
            int scoreTotal = results.stream().mapToInt(row -> ((Number) row.get("score")).intValue()).sum();
            if (scoreTotal != 8) throw new AssertionError("Tổng điểm phải bằng 8 nhưng là " + scoreTotal);
            int previousScore = Integer.MAX_VALUE;
            int previousPlacement = 0;
            for (Map<String, Object> row : results) {
                int score = ((Number) row.get("score")).intValue();
                int placement = ((Number) row.get("placement")).intValue();
                if (score > previousScore) {
                    throw new AssertionError("Kết quả chưa được sắp theo số cặp giảm dần");
                }
                if (score == previousScore && placement != previousPlacement) {
                    throw new AssertionError("Người bằng điểm phải có cùng thứ hạng");
                }
                if (score < previousScore && placement != previousPlacement + 1) {
                    throw new AssertionError("Thứ hạng phải liên tục theo kiểu 1, 2, 2, 3");
                }
                previousScore = score;
                previousPlacement = placement;
                int expectedReward = Boolean.TRUE.equals(row.get("winner")) ? 50 : 10;
                if (((Number) row.get("coinReward")).intValue() != expectedReward) {
                    throw new AssertionError("Thưởng xu không đúng cho " + row.get("username"));
                }
            }
            two.await(Type.GAME_END);

            one.send(Message.of(Type.SHOP_REQUEST));
            Message shop = one.await(Type.SHOP_UPDATE);
            List<Map<String, Object>> products = shop.value("products");
            if (products == null || products.size() < 6) {
                throw new AssertionError("Cửa hàng chưa trả đủ danh mục vật phẩm");
            }
            Map<String, Object> oneResult = results.stream()
                    .filter(row -> nameOne.equals(row.get("username"))).findFirst().orElseThrow();
            if (shop.integer("coins") != ((Number) oneResult.get("coinReward")).intValue()) {
                throw new AssertionError("Ví xu không đồng bộ với phần thưởng trận đấu");
            }

            one.send(Message.of(Type.HISTORY_REQUEST));
            List<String> history = one.await(Type.HISTORY_UPDATE).value("history");
            if (history == null || history.isEmpty()) throw new AssertionError("Missing completed match history");
            memorygame.client.HistoryPanelTest.assertBackendEntry(history.get(0),
                    ((Number) oneResult.get("score")).intValue(),
                    ((Number) oneResult.get("ratingDelta")).intValue(),
                    ((Number) oneResult.get("coinReward")).intValue());

            one.send(Message.of(Type.PLAY_AGAIN));
            one.await(Type.ROOM_UPDATE, message -> message.bool("postGame"));
            two.send(Message.of(Type.PLAY_AGAIN));
            Message replayRoom = one.await(Type.ROOM_UPDATE,
                    message -> !message.bool("postGame") && hasTwoPlayers(message));
            List<Map<String, Object>> replayPlayers = replayRoom.value("players");
            if (replayPlayers.stream().anyMatch(row -> Boolean.TRUE.equals(row.get("ready")))) {
                throw new AssertionError("Trạng thái sẵn sàng chưa được xóa khi chơi lại");
            }
            one.send(Message.of(Type.ROOM_READY).with("ready", true));
            two.send(Message.of(Type.ROOM_READY).with("ready", true));
            one.await(Type.ROOM_UPDATE, FullGameReplayTest::everyoneReady);
            one.send(Message.of(Type.ROOM_START));
            Message secondStart=one.await(Type.GAME_START);
            if(start.string("matchId").isBlank() || start.string("matchId").equals(secondStart.string("matchId")))
                throw new AssertionError("Replay reused match ID");
            two.send(Message.of(Type.ROOM_LEAVE));
            Message secondEnd=one.await(Type.GAME_END);
            List<Map<String,Object>> replayResults=secondEnd.value("results");
            Map<String,Object> forfeited=replayResults.stream().filter(row->nameTwo.equals(row.get("username"))).findFirst().orElseThrow();
            if(!Boolean.TRUE.equals(forfeited.get("forfeited")) || (Integer)forfeited.get("coinReward")!=10)
                throw new AssertionError("Replay forfeit reward");
            one.send(Message.of(Type.HISTORY_REQUEST));
            List<String> replayHistory=one.await(Type.HISTORY_UPDATE).value("history");
            if(replayHistory.size()!=2) throw new AssertionError("Replay history not separate");
            if(args.length>1 && args[1].equals("database")) {
                one.send(Message.of(Type.SHOP_REQUEST));
                List<Map<String,Object>> catalog=one.await(Type.SHOP_UPDATE).value("products");
                Map<String,Object> ocean=catalog.stream().filter(row->"theme_ocean".equals(row.get("id"))).findFirst().orElseThrow();
                if(!"Giá từ database".equals(ocean.get("name")) || (Integer)ocean.get("price")!=1)
                    throw new AssertionError("Shop did not read DB");
                one.send(Message.of(Type.SHOP_BUY).with("productId","theme_ocean"));
                one.await(Type.WALLET_UPDATE);
                List<Map<String,Object>> owned=one.await(Type.SHOP_UPDATE).value("products");
                if(owned.stream().noneMatch(row->"theme_ocean".equals(row.get("id")) && Boolean.TRUE.equals(row.get("owned"))))
                    throw new AssertionError("Bought item missing");
            }
            System.out.println("FULL_GAME_REPLAY_OK room=" + roomCode
                    + " pairs=8 scoreTotal=8 coinRewards=50/10 shopCatalog=" + products.size());
        }
    }

    private static boolean hasTwoPlayers(Message message) {
        List<Map<String, Object>> players = message.value("players");
        return players != null && players.size() == 2;
    }

    private static boolean everyoneReady(Message message) {
        List<Map<String, Object>> players = message.value("players");
        return players != null && players.size() == 2
                && players.stream().allMatch(row -> Boolean.TRUE.equals(row.get("ready")));
    }

    private static int knownPairFirst(int[] known, boolean[] matched) {
        for (int i = 0; i < known.length; i++) {
            if (known[i] == 0 || matched[i]) continue;
            for (int j = i + 1; j < known.length; j++) {
                if (!matched[j] && known[j] == known[i]) return i;
            }
        }
        return -1;
    }

    private static int knownPartner(int first, int value, int[] known, boolean[] matched) {
        for (int i = 0; i < known.length; i++) {
            if (i != first && !matched[i] && known[i] == value) return i;
        }
        return -1;
    }

    private static int firstUnknown(int[] known, boolean[] matched, int excluded) {
        for (int i = 0; i < known.length; i++) {
            if (i != excluded && !matched[i] && known[i] == 0) return i;
        }
        return -1;
    }

    private static int firstKnownUnmatchedOther(int[] known, boolean[] matched, int excluded) {
        for (int i = 0; i < known.length; i++) {
            if (i != excluded && !matched[i] && known[i] != 0) return i;
        }
        return -1;
    }

    private static final class AsyncClient implements AutoCloseable {
        private final Socket socket;
        private final ObjectOutputStream output;
        private final ObjectInputStream input;
        private final LinkedBlockingQueue<Message> messages = new LinkedBlockingQueue<>();
        private final Thread reader;

        AsyncClient(int port) throws Exception {
            socket = new Socket("127.0.0.1", port);
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());
            reader = new Thread(() -> {
                try {
                    while (!socket.isClosed()) messages.offer((Message) input.readObject());
                } catch (Exception ignored) { }
            }, "full-game-test-reader");
            reader.setDaemon(true);
            reader.start();
        }

        void login(String username) throws Exception {
            send(Message.of(Type.REGISTER).with("username", username).with("password", "test123"));
            await(Type.REGISTER_OK);
            send(Message.of(Type.LOGIN).with("username", username).with("password", "test123"));
        }

        synchronized void send(Message message) throws Exception {
            output.reset();
            output.writeObject(message);
            output.flush();
        }

        Message await(Type type) throws Exception {
            return await(type, message -> true);
        }

        Message await(Type type, Predicate<Message> condition) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
            while (System.nanoTime() < deadline) {
                Message message = messages.poll(1, TimeUnit.SECONDS);
                if (message == null) continue;
                if (message.type() == Type.ERROR) throw new AssertionError(message.string("message"));
                if (message.type() == type && condition.test(message)) return message;
            }
            throw new AssertionError("Timeout chờ " + type);
        }

        @Override public void close() throws Exception {
            socket.close();
        }
    }
}
