package memorygame.server;

import memorygame.common.Protocol.Message;
import memorygame.common.Protocol.Type;
import memorygame.common.Protocol;
import memorygame.common.Protocol.ChatEntry;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class GameServer implements AutoCloseable {
    public static final int DEFAULT_BOARD_SIZE = 8;
    public static final int TURN_SECONDS = 15;
    public static final int WIN_COINS = 50;
    public static final int LOSS_COINS = 10;
    private static final List<ShopProduct> SHOP_PRODUCTS = List.of(
            new ShopProduct("theme_classic", "Mặt thẻ Cổ điển", "Bề mặt 3D tối giản với họa tiết dập chìm", 0, "THEME", "#6D28D9"),
            new ShopProduct("theme_ocean", "Mặt thẻ Đại dương", "Sóng nước chuyển động và ánh sáng lướt trên mặt thẻ", 100, "THEME", "#0E7490"),
            new ShopProduct("theme_violet", "Mặt thẻ Tím neon", "Viền neon phát sáng và hạt sao chuyển động", 180, "THEME", "#6D28D9"),
            new ShopProduct("theme_gold", "Mặt thẻ Hoàng kim", "Ánh kim quét chéo trên bề mặt thẻ 3D", 300, "THEME", "#A16207"),
            new ShopProduct("title_memory", "Danh hiệu Bậc thầy trí nhớ", "Hào quang trí nhớ xanh chuyển động, hiển thị cạnh tên trong Lobby và phòng", 220, "TITLE", "#38BDF8"),
            new ShopProduct("title_champion", "Danh hiệu Nhà vô địch", "Vương miện vàng với tia sáng và hạt kim tuyến cao cấp", 400, "TITLE", "#FBBF24")
    );

    private final int port;
    private final Path stateFile;
    private final boolean mysqlStorage;
    private final DatabaseStore databaseStore;
    private final Object lock = new Object();
    private final ExecutorService clients = Executors.newCachedThreadPool();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final SecureRandom random = new SecureRandom();
    private final Map<String, UserRecord> users = new HashMap<>();
    private final Map<String, ClientHandler> online = new HashMap<>();
    private final Map<String, Room> rooms = new HashMap<>();
    private final Map<String, LinkedHashSet<String>> queues = new HashMap<>();
    private final Properties friendships = new Properties();
    private final Map<String, String[]> friendRequests = new HashMap<>();
    private final Map<String, Invite> invites = new HashMap<>();
    private final Map<String, ScheduledFuture<?>> disconnectTasks = new HashMap<>();
    private volatile boolean running;
    private ServerSocket serverSocket;

    public GameServer(int port, Path dataDirectory) {
        this.port = port;
        this.stateFile = dataDirectory.resolve("state.bin");
        this.mysqlStorage = !"file".equalsIgnoreCase(System.getenv().getOrDefault(
                "MEMORY_GAME_STORAGE", "mysql"));
        this.databaseStore = mysqlStorage ? new DatabaseStore() : null;
        for (int players = 2; players <= 4; players++) {
            for (int board : new int[]{4, 6, 8}) {
                for (String theme : List.of("FRUIT", "ANIMALS", "FOOD", "VEHICLES"))
                    queues.put(queueKey(players, board, theme), new LinkedHashSet<>());
            }
        }
        loadState();
        Path friendsFile = stateFile.resolveSibling("friends.properties");
        if (Files.exists(friendsFile)) {
            try (InputStream in = Files.newInputStream(friendsFile)) { friendships.load(in); }
            catch (IOException e) { throw new UncheckedIOException("Không thể đọc danh sách bạn bè", e); }
        }
        System.out.println(mysqlStorage
                ? "Lưu trữ: MySQL (tài khoản, thống kê, vật phẩm và lịch sử)"
                : "Lưu trữ: tệp nhị phân dành cho kiểm thử");
    }

    public void start() throws IOException {
        Files.createDirectories(stateFile.getParent());
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.printf("Memory Game Server đang chạy tại cổng %d%n", port);
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                clients.submit(new ClientHandler(socket));
            } catch (IOException e) {
                if (running) System.err.println("Không thể nhận kết nối: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        if (serverSocket != null) serverSocket.close();
        clients.shutdownNow();
        scheduler.shutdownNow();
        synchronized (lock) {
            saveState();
        }
    }

    // Local monitor only: immutable copies, never sent over the game protocol.
    public record MonitorSnapshot(boolean running, int port, int online, int queued,
                                  List<RoomSnapshot> rooms) {}
    public record PlayerSnapshot(String name, int score, boolean connected, boolean ready,
                                 boolean forfeited) {}
    public record CardSnapshot(int value, int state, String owner) {}
    public record RoomSnapshot(String code, int boardSize, boolean matchmaking, String status,
                               List<PlayerSnapshot> players, List<CardSnapshot> cards,
                               String current, long deadline, boolean resolving, int pairs, int turns, List<ChatEntry> chat, String cardTheme) {}

    public MonitorSnapshot monitorSnapshot() {
        synchronized (lock) {
            List<RoomSnapshot> snapshots = new ArrayList<>();
            for (Room room : rooms.values()) {
                Game game = room.playing || room.postGame ? room.game : null;
                List<PlayerSnapshot> players = new ArrayList<>();
                for (String name : game == null ? room.players : game.players) {
                    players.add(new PlayerSnapshot(name, game == null ? 0 : game.scores.getOrDefault(name, 0),
                            online.containsKey(key(name)), room.ready.contains(name),
                            game != null && game.forfeited.contains(name)));
                }
                List<CardSnapshot> cards = new ArrayList<>();
                if (game != null) {
                    for (int i = 0; i < game.values.length; i++) {
                        cards.add(new CardSnapshot(game.states[i] == 0 ? -1 : game.values[i],
                                game.states[i], game.matchedOwners[i]));
                    }
                }
                String status = room.playing ? "Đang chơi" : room.postGame ? "Kết thúc / chờ chơi lại"
                        : room.countdownDeadline > 0 ? "Đếm ngược" : "Chờ sẵn sàng";
                snapshots.add(new RoomSnapshot(room.code, room.boardSize, room.matchmaking, status,
                        List.copyOf(players), List.copyOf(cards),
                        game != null && !game.finished ? game.current() : "",
                        room.playing ? game.deadline : room.postGame ? room.replayDeadline : room.countdownDeadline,
                        game != null && game.resolving, game == null ? 0 : game.matchedPairs,
                        game == null ? 0 : game.turns, List.copyOf(room.chat),
                        room.cardTheme.isEmpty() ? "FRUIT" : room.cardTheme));
            }
            snapshots.sort(Comparator.comparing(RoomSnapshot::code));
            return new MonitorSnapshot(running, port, online.size(),
                    queues.values().stream().mapToInt(Set::size).sum(), List.copyOf(snapshots));
        }
    }

    private void handle(ClientHandler client, Message message) {
        synchronized (lock) {
            if (message.type() != Type.LOGIN && message.type() != Type.REGISTER && client.username == null) {
                client.error("Bạn cần đăng nhập trước.");
                return;
            }
            try {
                switch (message.type()) {
                    case LOGIN -> login(client, message);
                    case REGISTER -> register(client, message);
                    case PROFILE_REQUEST -> sendProfile(client);
                    case AVATAR_UPDATE -> updateAvatar(client, message.string("avatar"));
                    case PASSWORD_CHANGE -> changePassword(client, message);
                    case QUEUE_JOIN -> joinQueue(client, message.integer("size"), message.integer("boardSize"), message.string("cardTheme"));
                    case QUEUE_LEAVE -> leaveQueue(client.username);
                    case ROOM_CREATE -> createRoom(client, message);
                    case ROOM_JOIN -> joinRoom(client, message.string("code"));
                    case ROOM_LEAVE, BACK_LOBBY -> leaveRoom(client.username, true);
                    case ROOM_READY -> setReady(client, message.bool("ready"));
                    case ROOM_START -> requestStart(client);
                    case ROOM_KICK -> kick(client, message.string("username"));
                    case INVITE_SEND -> invite(client, message.string("username"), message.integer("boardSize"));
                    case INVITE_REPLY -> replyInvite(client, message.string("inviteId"), message.bool("accepted"));
                    case FRIEND_SEND -> requestFriend(client, message.string("username"));
                    case FRIEND_REPLY -> replyFriend(client, message.string("requestId"), message.bool("accepted"));
                    case WHISPER_SEND -> whisper(client, message.string("username"), message.string("text"));
                    case CHAT_SEND -> sendChat(client, message.string("text"));
                    case CARD_PICK -> pickCard(client, message.integer("index"));
                    case PLAY_AGAIN -> requestReplay(client);
                    case RANKING_REQUEST -> sendRanking(client);
                    case HISTORY_REQUEST -> sendHistory(client);
                    case SHOP_REQUEST -> sendShop(client);
                    case SHOP_BUY -> buyProduct(client, message.string("productId"));
                    case SHOP_EQUIP -> equipProduct(client, message.string("productId"));
                    default -> client.error("Yêu cầu không hợp lệ: " + message.type());
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
                client.error("Server không thể xử lý yêu cầu.");
            }
        }
    }

    private void login(ClientHandler client, Message message) {
        if (client.username != null) {
            client.error("Phiên này đã đăng nhập.");
            return;
        }
        String username = message.string("username").trim();
        String password = message.string("password");
        if (!username.matches("[A-Za-z0-9_]{3,16}") || password.length() < 4) {
            client.error("Tên gồm 3-16 ký tự chữ/số/_ và mật khẩu có ít nhất 4 ký tự.");
            return;
        }
        String key = username.toLowerCase(Locale.ROOT);
        if (online.containsKey(key)) {
            client.error("Tài khoản đang được sử dụng trên một phiên khác.");
            return;
        }
        UserRecord user = users.get(key);
        String passwordHash = hash(password);
        if (user == null) {
            client.error("Tài khoản không tồn tại. Hãy đăng ký trước.");
            return;
        }
        if (!user.passwordHash.equals(passwordHash)) {
            client.error("Sai mật khẩu.");
            return;
        }

        client.username = user.username;
        online.put(key, client);
        ScheduledFuture<?> task = disconnectTasks.remove(key);
        if (task != null) task.cancel(false);
        if (user.status.equals("OFFLINE")) user.status = "IDLE";

        client.send(Message.of(Type.LOGIN_OK)
                .with("username", user.username)
                .with("avatar", user.avatar)
                .with("rating", user.rating)
                .with("wins", user.wins)
                .with("coins", user.coins)
                .with("equippedTheme", user.equippedTheme)
                .with("equippedTitle", user.equippedTitle));

        sendFriends(client);

        Room room = roomOf(user.username);
        if (room != null) {
            if (room.matchmaking && !room.playing && !room.postGame) updateMatchmakingCountdown(room);
            else broadcastRoom(room);
            if (room.playing && room.game != null) sendGameState(client, room);
        }
        broadcastLobby();
    }

    private void register(ClientHandler client, Message message) {
        if (client.username != null) {
            client.error("Hãy đăng xuất trước khi tạo tài khoản khác.");
            return;
        }
        String username = message.string("username").trim();
        String password = message.string("password");
        if (!username.matches("[A-Za-z0-9_]{3,16}")) {
            client.error("Tên tài khoản phải gồm 3-16 ký tự chữ, số hoặc dấu gạch dưới.");
            return;
        }
        if (password.length() < 6) {
            client.error("Mật khẩu đăng ký phải có ít nhất 6 ký tự.");
            return;
        }
        String userKey = key(username);
        if (users.containsKey(userKey)) {
            client.error("Tên tài khoản đã tồn tại.");
            return;
        }
        users.put(userKey, new UserRecord(username, hash(password)));
        saveState();
        client.send(Message.of(Type.REGISTER_OK).with("username", username));
    }

    private void joinQueue(ClientHandler client, int size, int requestedBoardSize, String requestedTheme) {
        String theme = requestedTheme.isEmpty() ? "FRUIT" : requestedTheme;
        if (!Set.of("FRUIT", "ANIMALS", "FOOD", "VEHICLES").contains(theme)) {
            client.error("Chủ đề thẻ không hợp lệ.");
            return;
        }
        if (size < 2 || size > 4) {
            client.error("Số người phải từ 2 đến 4.");
            return;
        }
        int boardSize = normalizeBoardSize(requestedBoardSize);
        UserRecord user = user(client.username);
        if (!user.status.equals("IDLE")) {
            client.error("Bạn đang ở hàng chờ hoặc phòng khác.");
            return;
        }
        user.status = "QUEUE";
        user.queueSize = size;
        user.queueBoardSize = boardSize;
        user.queueTheme = theme;
        queues.get(queueKey(size, boardSize, theme)).add(key(client.username));
        client.send(Message.of(Type.LOBBY_UPDATE).with("notice", "Đang tìm trận " + size
                + " người — bàn " + boardSize + "×" + boardSize + "..."));
        tryMatch(size, boardSize, theme);
        broadcastLobby();
    }

    private void leaveQueue(String username) {
        UserRecord user = user(username);
        if (user.status.equals("QUEUE")) {
            queues.get(queueKey(user.queueSize, normalizeBoardSize(user.queueBoardSize), user.queueTheme)).remove(key(username));
            user.queueSize = 0;
            user.queueBoardSize = 0;
            user.queueTheme = null;
            user.status = "IDLE";
            broadcastLobby();
        }
    }

    private void tryMatch(int size, int boardSize, String theme) {
        LinkedHashSet<String> queue = queues.get(queueKey(size, boardSize, theme));
        while (queue.size() >= size) {
            List<String> matched = new ArrayList<>();
            Iterator<String> iterator = queue.iterator();
            while (iterator.hasNext() && matched.size() < size) {
                String userKey = iterator.next();
                iterator.remove();
                UserRecord user = users.get(userKey);
                if (user != null && user.status.equals("QUEUE") && online.containsKey(userKey)) {
                    matched.add(user.username);
                }
            }
            if (matched.size() < size) {
                for (String name : matched) queue.add(key(name));
                return;
            }
            Room room = new Room(newRoomCode(), "", boardSize);
            room.matchmaking = true;
            room.cardTheme = theme;
            room.targetPlayers = size;
            room.players.addAll(matched);
            rooms.put(room.code, room);
            for (String name : matched) {
                UserRecord user = user(name);
                user.status = "ROOM";
                user.queueSize = 0;
                user.queueBoardSize = 0;
                user.queueTheme = null;
            }
            broadcastRoom(room);
            broadcastLobby();
        }
    }

    private void createRoom(ClientHandler client, Message request) {
        UserRecord user = user(client.username);
        if (!user.status.equals("IDLE")) {
            client.error("Bạn phải ở trạng thái rảnh để tạo phòng.");
            return;
        }
        String name = request.string("roomName").trim();
        int capacity = request.get("capacity") == null ? 4 : request.integer("capacity");
        String cardTheme = request.string("cardTheme");
        if (name.length() > 40 || name.chars().anyMatch(Character::isISOControl)
                || capacity < 2 || capacity > 4
                || (!cardTheme.isEmpty() && !Set.of("FRUIT", "ANIMALS", "FOOD", "VEHICLES").contains(cardTheme))) {
            client.error("Tên phòng tối đa 40 ký tự, số người từ 2 đến 4 và chủ đề phải hợp lệ.");
            return;
        }
        if (request.get("roomName") != null && name.isEmpty()) {
            client.error("Vui lòng nhập tên phòng.");
            return;
        }
        int requestedBoardSize = request.integer("boardSize");
        if (request.get("capacity") != null && requestedBoardSize != 4 && requestedBoardSize != 6 && requestedBoardSize != 8) {
            client.error("Kích thước bàn phải là 4, 6 hoặc 8.");
            return;
        }
        String invitee = request.string("inviteUsername");
        if (!invitee.isEmpty()) {
            UserRecord target = users.get(key(invitee));
            if (target == null || target.username.equalsIgnoreCase(client.username)
                    || !online.containsKey(key(invitee)) || !"IDLE".equals(target.status)) {
                client.error("Người được mời không còn rảnh hoặc đã ngoại tuyến. Chưa tạo phòng.");
                return;
            }
        }
        Room room = new Room(newRoomCode(), user.username, normalizeBoardSize(requestedBoardSize));
        room.name = name.isEmpty() ? "Phòng " + room.code : name;
        room.capacity = capacity;
        room.cardTheme = cardTheme;
        room.players.add(user.username);
        rooms.put(room.code, room);
        user.status = "ROOM";
        broadcastRoom(room);
        broadcastLobby();
        if (!invitee.isEmpty()) invite(client, invitee, room.boardSize);
    }

    private void joinRoom(ClientHandler client, String rawCode) {
        UserRecord user = user(client.username);
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        Room room = rooms.get(code);
        if (!user.status.equals("IDLE")) {
            client.error("Bạn đang ở hàng chờ hoặc phòng khác.");
        } else if (room == null) {
            client.error("Mã phòng không tồn tại.");
        } else if (room.matchmaking) {
            client.error("Phòng ghép ngẫu nhiên do Server quản lý, không thể vào bằng mã.");
        } else if (room.countdownDeadline > System.currentTimeMillis()) {
            client.error("Phòng đang đếm ngược để bắt đầu, không thể tham gia lúc này.");
        } else if (room.playing || room.postGame) {
            client.error("Phòng đang thi đấu hoặc chờ quyết định chơi lại.");
        } else if (room.players.size() >= room.capacity) {
            client.error("Phòng đã đủ " + room.capacity + " người.");
        } else {
            room.players.add(user.username);
            user.status = "ROOM";
            broadcastRoom(room);
            broadcastLobby();
        }
    }

    private void setReady(ClientHandler client, boolean ready) {
        Room room = roomOf(client.username);
        if (room == null || room.playing || room.postGame) {
            client.error("Bạn không ở phòng chờ.");
            return;
        }
        if (ready) room.ready.add(client.username); else room.ready.remove(client.username);
        if (room.matchmaking) updateMatchmakingCountdown(room);
        else {
            cancelRoomCountdown(room);
            broadcastRoom(room);
        }
    }

    private void cancelRoomCountdown(Room room) {
        if (room.countdownFuture != null) room.countdownFuture.cancel(false);
        room.countdownFuture = null;
        room.countdownDeadline = 0;
    }

    private void updateMatchmakingCountdown(Room room) {
        if (!room.matchmaking || room.playing || room.postGame) return;
        cancelRoomCountdown(room);
        boolean canStart = room.players.size() == room.targetPlayers
                && room.ready.containsAll(room.players)
                && room.players.stream().allMatch(name -> online.containsKey(key(name)));
        if (canStart) {
            room.countdownDeadline = System.currentTimeMillis() + 3_000L;
            broadcastRoom(room);
            room.countdownFuture = scheduler.schedule(() -> {
                synchronized (lock) {
                    if (!rooms.containsKey(room.code) || room.playing || room.postGame) return;
                    boolean stillReady = room.players.size() == room.targetPlayers
                            && room.ready.containsAll(room.players)
                            && room.players.stream().allMatch(name -> online.containsKey(key(name)));
                    if (stillReady) startGame(room);
                    else {
                        room.countdownDeadline = 0;
                        room.countdownFuture = null;
                        broadcastRoom(room);
                    }
                }
            }, 3, TimeUnit.SECONDS);
        } else {
            broadcastRoom(room);
        }
    }

    private void requestReplay(ClientHandler client) {
        Room room = roomOf(client.username);
        if (room == null || !room.postGame) {
            client.error("Hiện không có phiên chơi lại.");
            return;
        }
        room.replayVotes.add(client.username);
        broadcastRoom(room);
        if (room.players.size() >= 2 && room.replayVotes.containsAll(room.players)) {
            finalizeReplay(room);
        }
    }

    private void finalizeReplay(Room room) {
        if (!room.postGame) return;
        if (room.replayFuture != null) room.replayFuture.cancel(false);

        List<String> returningToLobby = room.players.stream()
                .filter(name -> !room.replayVotes.contains(name))
                .toList();
        room.players.removeAll(returningToLobby);
        room.ready.clear();
        room.postGame = false;

        for (String name : returningToLobby) {
            UserRecord user = user(name);
            user.status = online.containsKey(key(name)) ? "IDLE" : "OFFLINE";
            ClientHandler handler = online.get(key(name));
            if (handler != null) sendEmptyRoom(handler);
        }

        if (room.players.size() < 2) {
            for (String name : new ArrayList<>(room.players)) {
                UserRecord user = user(name);
                user.status = online.containsKey(key(name)) ? "IDLE" : "OFFLINE";
                ClientHandler handler = online.get(key(name));
                if (handler != null) sendEmptyRoom(handler);
            }
            room.players.clear();
            rooms.remove(room.code);
        } else {
            if (room.matchmaking) room.targetPlayers = room.players.size();
            else if (!room.players.contains(room.host)) room.host = room.players.get(0);
            for (String name : room.players) user(name).status = "ROOM";
            broadcastRoom(room);
        }
        room.replayVotes.clear();
        broadcastLobby();
    }

    private void requestStart(ClientHandler client) {
        Room room = roomOf(client.username);
        if (room != null && room.matchmaking) {
            client.error("Phòng ghép ngẫu nhiên do Server tự bắt đầu khi tất cả đã sẵn sàng.");
            return;
        }
        if (room == null || room.playing || room.postGame || !room.host.equals(client.username)) {
            client.error("Chỉ chủ phòng có thể bắt đầu.");
            return;
        }
        if (room.countdownDeadline > System.currentTimeMillis()) {
            client.error("Phòng đang đếm ngược để bắt đầu.");
            return;
        }
        if (room.players.size() < 2 || !room.ready.containsAll(room.players)) {
            client.error("Cần 2-4 người và tất cả phải sẵn sàng.");
            return;
        }
        for (String name : room.players) {
            if (!online.containsKey(key(name))) {
                client.error("Có người chơi đang mất kết nối.");
                return;
            }
        }
        room.countdownDeadline = System.currentTimeMillis() + 3_000L;
        broadcastRoom(room);
        room.countdownFuture = scheduler.schedule(() -> {
            synchronized (lock) {
                if (!rooms.containsKey(room.code) || room.playing || room.postGame || room.matchmaking) return;
                boolean canStart = room.players.size() >= 2 && room.players.size() <= room.capacity
                        && room.ready.containsAll(room.players)
                        && room.players.stream().allMatch(name -> online.containsKey(key(name)));
                if (canStart) startGame(room);
                else {
                    cancelRoomCountdown(room);
                    broadcastRoom(room);
                }
            }
        }, 3, TimeUnit.SECONDS);
    }

    private void startGame(Room room) {
        room.playing = true;
        room.postGame = false;
        room.replayVotes.clear();
        if (room.replayFuture != null) room.replayFuture.cancel(false);
        cancelRoomCountdown(room);
        room.chat.clear();
        room.game = new Game(room.players, room.boardSize, random);
        for (String name : room.players) user(name).status = "PLAYING";
        broadcastGameStart(room);
        beginTurn(room);
        scheduleInactivity(room);
        broadcastLobby();
    }

    private void kick(ClientHandler client, String target) {
        Room room = roomOf(client.username);
        if (room != null && room.matchmaking) {
            client.error("Phòng ghép ngẫu nhiên do Server quản lý, không có quyền mời người khác ra.");
            return;
        }
        if (room == null || room.playing || room.postGame || !room.host.equals(client.username)) {
            client.error("Không thể đuổi người chơi lúc này.");
            return;
        }
        String actual = findName(room.players, target);
        if (actual == null || actual.equals(room.host)) {
            client.error("Người chơi không hợp lệ.");
            return;
        }
        cancelRoomCountdown(room);
        room.players.remove(actual);
        room.ready.remove(actual);
        user(actual).status = online.containsKey(key(actual)) ? "IDLE" : "OFFLINE";
        ClientHandler kicked = online.get(key(actual));
        if (kicked != null) {
            kicked.send(Message.of(Type.ERROR).with("message", "Bạn đã bị chủ phòng mời ra."));
            sendEmptyRoom(kicked);
        }
        broadcastRoom(room);
        broadcastLobby();
    }

    private void leaveRoom(String username, boolean voluntary) {
        Room room = roomOf(username);
        if (room == null) return;
        if (room.matchmaking && !room.playing && !room.postGame) {
            dissolveMatchmakingRoom(room, username, voluntary);
            return;
        }
        if (!room.playing && !room.postGame) cancelRoomCountdown(room);
        if (room.playing && room.game != null) forfeit(room, username, voluntary ? "bỏ cuộc" : "mất kết nối");
        room.players.remove(username);
        room.ready.remove(username);
        room.replayVotes.remove(username);
        if (room.players.isEmpty()) {
            if (room.replayFuture != null) room.replayFuture.cancel(false);
            rooms.remove(room.code);
        } else {
            if (!room.matchmaking && room.host.equals(username)) room.host = room.players.get(0);
            broadcastRoom(room);
        }
        UserRecord user = user(username);
        user.status = online.containsKey(key(username)) ? "IDLE" : "OFFLINE";
        ClientHandler leaving = online.get(key(username));
        if (leaving != null) sendEmptyRoom(leaving);
        if (room.postGame && room.players.size() >= 2 && room.replayVotes.containsAll(room.players)) {
            finalizeReplay(room);
        }
        broadcastLobby();
    }

    private void dissolveMatchmakingRoom(Room room, String leavingName, boolean voluntary) {
        if (room.countdownFuture != null) room.countdownFuture.cancel(false);
        room.countdownFuture = null;
        room.countdownDeadline = 0;
        rooms.remove(room.code);
        for (String name : new ArrayList<>(room.players)) {
            UserRecord user = user(name);
            user.status = online.containsKey(key(name)) ? "IDLE" : "OFFLINE";
            ClientHandler handler = online.get(key(name));
            if (handler != null) {
                sendEmptyRoom(handler);
                if (!name.equalsIgnoreCase(leavingName)) {
                    handler.error(voluntary
                            ? leavingName + " đã rời phòng ghép. Bạn được đưa về Lobby."
                            : leavingName + " mất kết nối quá lâu. Phòng ghép đã đóng.");
                }
            }
        }
        room.players.clear();
        room.ready.clear();
        broadcastLobby();
    }

    private void invite(ClientHandler sender, String targetName, int requestedBoardSize) {
        UserRecord senderUser = user(sender.username);
        UserRecord target = users.get(key(targetName));
        if (target == null || !online.containsKey(key(targetName)) || !target.status.equals("IDLE")) {
            sender.error("Người chơi không online hoặc không rảnh.");
            return;
        }
        if (!(senderUser.status.equals("IDLE") || senderUser.status.equals("ROOM"))) {
            sender.error("Bạn không thể gửi lời mời lúc này.");
            return;
        }
        Room senderRoom = roomOf(sender.username);
        if (senderRoom != null && (senderRoom.matchmaking
                || senderRoom.countdownDeadline > System.currentTimeMillis() || senderRoom.playing
                || senderRoom.postGame || senderRoom.players.size() >= senderRoom.capacity)) {
            sender.error("Phòng không thể nhận thêm người.");
            return;
        }
        String id = UUID.randomUUID().toString();
        int boardSize = senderRoom == null ? normalizeBoardSize(requestedBoardSize) : senderRoom.boardSize;
        Invite invite = new Invite(id, sender.username, target.username, boardSize,
                senderRoom == null ? "" : senderRoom.code, System.currentTimeMillis() + 10_000L);
        invites.put(id, invite);
        online.get(key(target.username)).send(Message.of(Type.INVITE_RECEIVED)
                .with("inviteId", id).with("from", sender.username).with("expiresIn", 10)
                .with("expiresAt", invite.expiresAt).with("code", invite.roomCode)
                .with("roomName", senderRoom == null ? "Phòng của " + sender.username : senderRoom.name)
                .with("boardSize", boardSize).with("capacity", senderRoom == null ? 4 : senderRoom.capacity)
                .with("playerCount", senderRoom == null ? 1 : senderRoom.players.size())
                .with("cardTheme", senderRoom == null || senderRoom.cardTheme.isEmpty() ? "FRUIT" : senderRoom.cardTheme));
        scheduler.schedule(() -> {
            synchronized (lock) { invites.remove(id); }
        }, 10, TimeUnit.SECONDS);
    }

    private void replyInvite(ClientHandler targetClient, String inviteId, boolean accepted) {
        Invite invite = invites.get(inviteId);
        if (invite == null || !invite.target.equalsIgnoreCase(targetClient.username)) {
            targetClient.error("Lời mời không còn hiệu lực.");
            return;
        }
        invites.remove(inviteId);
        if (System.currentTimeMillis() >= invite.expiresAt) {
            targetClient.error("Lời mời đã hết hạn.");
            return;
        }
        ClientHandler senderClient = online.get(key(invite.sender));
        if (!accepted) {
            if (senderClient != null) senderClient.error(invite.target + " đã từ chối lời mời.");
            return;
        }
        UserRecord target = user(invite.target);
        if (!target.status.equals("IDLE") || senderClient == null) {
            targetClient.error("Không thể chấp nhận lời mời lúc này.");
            return;
        }
        Room room = roomOf(invite.sender);
        if ((room == null && !invite.roomCode.isEmpty())
                || (room != null && !room.code.equals(invite.roomCode))) {
            targetClient.error("Phòng được mời không còn khả dụng.");
            return;
        }
        if (room == null) {
            UserRecord sender = user(invite.sender);
            if (!sender.status.equals("IDLE")) {
                targetClient.error("Người mời không còn ở Lobby.");
                return;
            }
            room = new Room(newRoomCode(), sender.username, invite.boardSize);
            room.name = "Phòng của " + sender.username;
            room.cardTheme = "FRUIT";
            room.players.add(sender.username);
            rooms.put(room.code, room);
            sender.status = "ROOM";
        }
        if (room.matchmaking || room.countdownDeadline > System.currentTimeMillis()
                || room.playing || room.postGame || room.players.size() >= room.capacity) {
            targetClient.error("Phòng đã đầy hoặc đã bắt đầu.");
            return;
        }
        room.players.add(target.username);
        target.status = "ROOM";
        broadcastRoom(room);
        broadcastLobby();
    }

    private String friendshipKey(String first, String second) {
        String a = key(first), b = key(second);
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    private void sendFriends(ClientHandler client) {
        List<String> names = new ArrayList<>();
        for (UserRecord other : users.values()) {
            if (friendships.containsKey(friendshipKey(client.username, other.username))) names.add(other.username);
        }
        client.send(Message.of(Type.FRIEND_UPDATE).with("friends", names));
    }

    private ClientHandler socialTarget(ClientHandler sender, String name) {
        ClientHandler target = online.get(key(name));
        if (target == null || target == sender) {
            sender.error("Hãy chọn một người chơi khác đang online."); return null;
        }
        return target;
    }

    private void requestFriend(ClientHandler sender, String name) {
        ClientHandler target = socialTarget(sender, name);
        if (target == null) return;
        String pair = friendshipKey(sender.username, target.username);
        if (friendships.containsKey(pair)) { sender.error("Hai bạn đã là bạn bè."); return; }
        if (friendRequests.values().stream().anyMatch(r -> friendshipKey(r[0], r[1]).equals(pair))) {
            sender.error("Đã có lời mời kết bạn đang chờ phản hồi."); return;
        }
        String id = UUID.randomUUID().toString();
        friendRequests.put(id, new String[]{sender.username, target.username});
        scheduler.schedule(() -> { synchronized (lock) { friendRequests.remove(id); } }, 2, TimeUnit.MINUTES);
        target.send(Message.of(Type.FRIEND_RECEIVED).with("from", sender.username).with("requestId", id));
        sender.send(Message.of(Type.FRIEND_UPDATE).with("notice", "Đã gửi lời mời kết bạn đến " + target.username + "."));
    }

    private void replyFriend(ClientHandler target, String id, boolean accepted) {
        String[] request = friendRequests.get(id);
        if (request == null || !request[1].equalsIgnoreCase(target.username)) {
            target.error("Lời mời kết bạn không còn hiệu lực."); return;
        }
        friendRequests.remove(id);
        if (accepted) {
            String pair = friendshipKey(request[0], request[1]);
            friendships.setProperty(pair, "true");
            Path file = stateFile.resolveSibling("friends.properties");
            Path temporary = file.resolveSibling("friends.properties.tmp");
            try {
                Files.createDirectories(file.getParent());
                try (OutputStream out = Files.newOutputStream(temporary)) { friendships.store(out, "Memory Game friends"); }
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                friendships.remove(pair); target.error("Không thể lưu kết bạn. Hãy thử lại."); return;
            }
        }
        ClientHandler sender = online.get(key(request[0]));
        sendFriends(target);
        if (sender != null) {
            sendFriends(sender);
            sender.send(Message.of(Type.FRIEND_UPDATE).with("notice", target.username
                    + (accepted ? " đã chấp nhận lời mời kết bạn." : " đã từ chối lời mời kết bạn.")));
        }
    }

    private void whisper(ClientHandler sender, String name, String text) {
        ClientHandler target = socialTarget(sender, name);
        if (target == null) return;
        text = text.strip();
        if (text.isBlank() || text.length() > Protocol.CHAT_MAX_LENGTH || text.codePoints().anyMatch(Character::isISOControl)) {
            sender.error("Tin nhắn cần có 1–300 ký tự và không chứa ký tự điều khiển."); return;
        }
        Message message = Message.of(Type.WHISPER_MESSAGE).with("from", sender.username)
                .with("to", target.username).with("entry", new ChatEntry(sender.username, text, System.currentTimeMillis()));
        target.send(message); sender.send(message);
    }

    private void sendChat(ClientHandler client, String text) {
        Room room = roomOf(client.username);
        if (room == null || !room.playing || room.game == null || room.game.finished
                || !room.game.active.contains(client.username)) {
            client.error("Bạn chỉ có thể chat khi đang trong trận.");
            return;
        }
        text = text.strip();
        if (text.isBlank() || text.length() > Protocol.CHAT_MAX_LENGTH
                || text.codePoints().anyMatch(Character::isISOControl)) {
            client.error("Tin nhắn cần có 1–300 ký tự và không chứa ký tự điều khiển.");
            return;
        }
        ChatEntry entry = new ChatEntry(client.username, text, System.currentTimeMillis());
        room.chat.add(entry);
        while (room.chat.size() > Protocol.CHAT_HISTORY_LIMIT) room.chat.remove(0);
        broadcast(room, Message.of(Type.CHAT_MESSAGE).with("code", room.code).with("entry", entry));
    }

    private void pickCard(ClientHandler client, int index) {
        Room room = roomOf(client.username);
        if (room == null || !room.playing || room.game == null) {
            client.error("Bạn không ở trong trận.");
            return;
        }
        Game game = room.game;
        if (game.finished || game.resolving || !game.current().equals(client.username)) {
            client.error("Chưa đến lượt của bạn.");
            return;
        }
        if (index < 0 || index >= game.values.length || game.states[index] != 0 || game.selected.contains(index)) {
            client.error("Ô thẻ không hợp lệ hoặc đã được ghép.");
            return;
        }
        game.states[index] = 1;
        game.selected.add(index);
        game.lastValidAction = System.currentTimeMillis();
        scheduleInactivity(room);
        broadcast(room, Message.of(Type.CARD_REVEALED)
                .with("index", index).with("value", game.values[index]).with("player", client.username));

        if (game.selected.size() < 2) return;
        game.resolving = true;
        if (game.turnFuture != null) game.turnFuture.cancel(false);
        int first = game.selected.get(0);
        int second = game.selected.get(1);
        boolean match = game.values[first] == game.values[second];
        game.turns++;

        if (match) {
            game.states[first] = game.states[second] = 2;
            game.matchedOwners[first] = game.matchedOwners[second] = client.username;
            game.matchedPairs++;
            game.scores.merge(client.username, 1, Integer::sum);
            game.selected.clear();
            game.resolving = false;
            broadcast(room, Message.of(Type.MATCH_RESULT).with("match", true)
                    .with("first", first).with("second", second)
                    .with("player", client.username).with("scores", new LinkedHashMap<>(game.scores))
                    .with("pairs", game.matchedPairs));
            if (game.matchedPairs == game.totalPairs) {
                finishGame(room, "Đã tìm đủ " + game.totalPairs + " cặp", false);
            } else {
                scheduler.schedule(() -> { synchronized (lock) { if (room.game == game && !game.finished) beginTurn(room); } },
                        400, TimeUnit.MILLISECONDS);
            }
        } else {
            broadcast(room, Message.of(Type.MATCH_RESULT).with("match", false)
                    .with("first", first).with("second", second)
                    .with("player", client.username).with("scores", new LinkedHashMap<>(game.scores))
                    .with("pairs", game.matchedPairs));
            scheduler.schedule(() -> {
                synchronized (lock) {
                    if (room.game != game || game.finished) return;
                    game.states[first] = game.states[second] = 0;
                    game.selected.clear();
                    game.resolving = false;
                    broadcast(room, Message.of(Type.CARD_HIDDEN).with("indexes", new int[]{first, second}));
                    advanceTurn(room);
                }
            }, 1310, TimeUnit.MILLISECONDS);
        }
    }

    private void beginTurn(Room room) {
        Game game = room.game;
        if (game == null || game.finished) return;
        if (!game.active.contains(game.current())) {
            advanceTurn(room);
            return;
        }
        int token = ++game.turnToken;
        game.deadline = System.currentTimeMillis() + TURN_SECONDS * 1000L;
        broadcast(room, Message.of(Type.TURN_UPDATE)
                .with("current", game.current()).with("deadline", game.deadline));
        game.turnFuture = scheduler.schedule(() -> {
            synchronized (lock) {
                if (room.game != game || game.finished || game.turnToken != token || game.resolving) return;
                if (!game.selected.isEmpty()) {
                    int[] indexes = game.selected.stream().mapToInt(Integer::intValue).toArray();
                    for (int index : indexes) game.states[index] = 0;
                    game.selected.clear();
                    broadcast(room, Message.of(Type.CARD_HIDDEN).with("indexes", indexes));
                }
                advanceTurn(room);
            }
        }, TURN_SECONDS, TimeUnit.SECONDS);
    }

    private void advanceTurn(Room room) {
        Game game = room.game;
        if (game == null || game.finished) return;
        for (int i = 0; i < game.players.size(); i++) {
            game.currentIndex = (game.currentIndex + 1) % game.players.size();
            if (game.active.contains(game.current())) {
                beginTurn(room);
                return;
            }
        }
        finishGame(room, "Không còn người chơi", true);
    }

    private void forfeit(Room room, String username, String reason) {
        Game game = room.game;
        if (game == null || game.finished || !game.active.remove(username)) return;
        game.forfeited.add(username);
        game.events.add(username + " " + reason);
        if (game.active.size() <= 1) {
            finishGame(room, game.active.isEmpty() ? "Tất cả đã bỏ cuộc" : "Chỉ còn một người chơi", true);
            return;
        }
        if (game.current().equals(username)) {
            if (game.turnFuture != null) game.turnFuture.cancel(false);
            if (!game.selected.isEmpty()) {
                int[] indexes = game.selected.stream().mapToInt(Integer::intValue).toArray();
                for (int index : indexes) game.states[index] = 0;
                game.selected.clear();
                broadcast(room, Message.of(Type.CARD_HIDDEN).with("indexes", indexes));
            }
            game.resolving = false;
            advanceTurn(room);
        }
    }

    private void scheduleInactivity(Room room) {
        Game game = room.game;
        if (game == null) return;
        if (game.inactivityFuture != null) game.inactivityFuture.cancel(false);
        long marker = game.lastValidAction;
        game.inactivityFuture = scheduler.schedule(() -> {
            synchronized (lock) {
                if (room.game == game && !game.finished && game.lastValidAction == marker) {
                    finishGame(room, "Tất cả người chơi không hoạt động trong 60 giây", true);
                }
            }
        }, 60, TimeUnit.SECONDS);
    }

    private void finishGame(Room room, String reason, boolean abnormal) {
        Game game = room.game;
        if (game == null || game.finished) return;
        game.finished = true;
        if (game.turnFuture != null) game.turnFuture.cancel(false);
        if (game.inactivityFuture != null) game.inactivityFuture.cancel(false);

        int top = game.active.stream().mapToInt(name -> game.scores.getOrDefault(name, 0)).max().orElse(-1);
        List<String> winners;
        if (reason.contains("không hoạt động") || game.active.isEmpty()) {
            winners = new ArrayList<>();
        } else if (abnormal && game.active.size() == 1) {
            winners = new ArrayList<>(game.active);
        } else {
            winners = game.active.stream().filter(name -> game.scores.getOrDefault(name, 0) == top).toList();
        }

        List<String> rankedPlayers = new ArrayList<>(game.players);
        rankedPlayers.sort(Comparator
                .comparingInt((String name) -> game.scores.getOrDefault(name, 0)).reversed()
                .thenComparing(String.CASE_INSENSITIVE_ORDER));

        Map<String, Integer> placements = new HashMap<>();
        int placement = 0;
        int previousScore = Integer.MIN_VALUE;
        for (String name : rankedPlayers) {
            int score = game.scores.getOrDefault(name, 0);
            if (score != previousScore) {
                placement++;
                previousScore = score;
            }
            placements.put(name, placement);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        for (String name : rankedPlayers) {
            UserRecord user = user(name);
            boolean forfeited = game.forfeited.contains(name);
            boolean winner = winners.contains(name);
            int playerPlacement = placements.get(name);
            int delta;
            if (reason.contains("không hoạt động")) delta = -1;
            else if (winner) delta = winners.size() == 1 ? 3 : 1;
            else delta = -1;
            user.rating = Math.max(0, user.rating + delta);
            user.games++;
            if (winner && winners.size() == 1) user.wins++;
            int coinReward = winner ? WIN_COINS : LOSS_COINS;
            user.coins += coinReward;
            String result = stamp + " | " + reason + " | " + game.scores.getOrDefault(name, 0)
                    + " cặp | " + (forfeited ? "Bỏ cuộc" : "Hạng " + playerPlacement)
                    + " | Xếp hạng " + (delta >= 0 ? "+" : "") + delta
                    + " | +" + coinReward + " xu";
            user.history.add(0, result);
            while (user.history.size() > 50) user.history.remove(user.history.size() - 1);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", name);
            row.put("score", game.scores.getOrDefault(name, 0));
            row.put("placement", playerPlacement);
            row.put("winner", winner);
            row.put("forfeited", forfeited);
            row.put("ratingDelta", delta);
            row.put("rating", user.rating);
            row.put("coinReward", coinReward);
            row.put("coins", user.coins);
            results.add(row);
            ClientHandler walletClient = online.get(key(name));
            if (walletClient != null) {
                walletClient.send(Message.of(Type.WALLET_UPDATE)
                        .with("coins", user.coins).with("earned", coinReward));
            }
        }
        saveState();
        long replayDeadline = System.currentTimeMillis() + 30_000L;
        broadcast(room, Message.of(Type.GAME_END).with("reason", reason)
                .with("results", (Serializable) results).with("replayDeadline", replayDeadline));

        room.playing = false;
        room.postGame = true;
        room.replayDeadline = replayDeadline;
        room.replayVotes.clear();
        room.ready.clear();
        room.game = null;
        for (String name : new ArrayList<>(room.players)) {
            UserRecord user = user(name);
            user.status = online.containsKey(key(name)) ? "ROOM" : "OFFLINE";
        }
        broadcastRoom(room);
        broadcastLobby();
        room.replayFuture = scheduler.schedule(() -> {
            synchronized (lock) {
                if (room.postGame) finalizeReplay(room);
            }
        }, 30, TimeUnit.SECONDS);
    }

    private void sendProfile(ClientHandler client) {
        UserRecord u = user(client.username);
        client.send(Message.of(Type.PROFILE_UPDATE).with("username", u.username).with("avatar", u.avatar)
                .with("rating", u.rating).with("wins", u.wins).with("games", u.games).with("coins", u.coins));
    }

    private void profileResult(ClientHandler client, String action, boolean success, String message) {
        client.send(Message.of(Type.PROFILE_RESULT).with("action", action).with("success", success).with("message", message));
    }

    private void updateAvatar(ClientHandler client, String avatar) {
        UserRecord u = user(client.username);
        String previous = u.avatar;
        try {
            memorygame.common.AvatarImages.decode(avatar);
            u.avatar = avatar;
            persistState();
        } catch (Exception e) {
            u.avatar = previous;
            profileResult(client, "avatar", false, "Không lưu được ảnh. Hãy chọn PNG/JPG hợp lệ và thử lại.");
            return;
        }
        profileResult(client, "avatar", true, "Đã cập nhật ảnh đại diện.");
        sendProfile(client); broadcastLobby();
    }

    private void changePassword(ClientHandler client, Message request) {
        UserRecord u = user(client.username);
        String next = request.string("newPassword");
        if (!u.passwordHash.equals(hash(request.string("currentPassword")))) {
            profileResult(client, "password", false, "Mật khẩu hiện tại không đúng."); return;
        }
        if (next.length() < 6 || next.length() > 128) {
            profileResult(client, "password", false, "Mật khẩu mới cần từ 6 đến 128 ký tự."); return;
        }
        String previous = u.passwordHash;
        u.passwordHash = hash(next);
        try { persistState(); }
        catch (Exception e) {
            u.passwordHash = previous;
            profileResult(client, "password", false, "Không lưu được mật khẩu. Vui lòng thử lại."); return;
        }
        profileResult(client, "password", true, "Đổi mật khẩu thành công.");
    }

    private void sendRanking(ClientHandler client) {
        List<UserRecord> ordered = users.values().stream()
                .sorted(Comparator.comparingInt((UserRecord u) -> u.rating).reversed()
                        .thenComparing(Comparator.comparingInt((UserRecord u) -> u.wins).reversed())
                        .thenComparing(Comparator.comparingDouble(UserRecord::winRate).reversed()))
                .toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        int rank = 1;
        for (UserRecord user : ordered) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank++);
            row.put("username", user.username);
            row.put("rating", user.rating);
            row.put("wins", user.wins);
            row.put("games", user.games);
            row.put("winRate", user.winRate());
            row.put("avatar", user.avatar);
            rows.add(row);
        }
        client.send(Message.of(Type.RANKING_UPDATE).with("rows", (Serializable) rows));
    }

    private void sendHistory(ClientHandler client) {
        client.send(Message.of(Type.HISTORY_UPDATE)
                .with("history", new ArrayList<>(user(client.username).history)));
    }

    private void sendShop(ClientHandler client) {
        UserRecord user = user(client.username);
        List<Map<String, Object>> products = new ArrayList<>();
        for (ShopProduct product : SHOP_PRODUCTS) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", product.id);
            row.put("name", product.name);
            row.put("description", product.description);
            row.put("price", product.price);
            row.put("type", product.type);
            row.put("color", product.color);
            row.put("owned", product.price == 0 || user.ownedItems.contains(product.id));
            row.put("equipped", product.type.equals("THEME")
                    ? product.id.equals(user.equippedTheme) : product.id.equals(user.equippedTitle));
            products.add(row);
        }
        client.send(Message.of(Type.SHOP_UPDATE)
                .with("coins", user.coins).with("products", (Serializable) products)
                .with("equippedTheme", user.equippedTheme).with("equippedTitle", user.equippedTitle));
    }

    private void buyProduct(ClientHandler client, String productId) {
        UserRecord user = user(client.username);
        ShopProduct product = productById(productId);
        if (product == null || product.price == 0) {
            client.error("Vật phẩm không hợp lệ.");
            return;
        }
        if (user.ownedItems.contains(product.id)) {
            client.error("Bạn đã sở hữu vật phẩm này.");
            return;
        }
        if (user.coins < product.price) {
            client.error("Bạn chưa đủ xu để mua vật phẩm này.");
            return;
        }
        user.coins -= product.price;
        user.ownedItems.add(product.id);
        saveState();
        client.send(Message.of(Type.WALLET_UPDATE).with("coins", user.coins).with("earned", 0));
        sendShop(client);
    }

    private void equipProduct(ClientHandler client, String productId) {
        UserRecord user = user(client.username);
        ShopProduct product = productById(productId);
        if (product == null || !(product.price == 0 || user.ownedItems.contains(product.id))) {
            client.error("Bạn chưa sở hữu vật phẩm này.");
            return;
        }
        if (product.type.equals("THEME")) user.equippedTheme = product.id;
        else if (product.type.equals("TITLE")) user.equippedTitle = product.id;
        saveState();
        sendShop(client);
        broadcastLobby();
        Room room = roomOf(client.username);
        if (room != null) broadcastRoom(room);
    }

    private static ShopProduct productById(String productId) {
        return SHOP_PRODUCTS.stream().filter(product -> product.id.equals(productId)).findFirst().orElse(null);
    }

    private void broadcastLobby() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ClientHandler handler : online.values()) {
            UserRecord user = user(handler.username);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", user.username);
            row.put("rating", user.rating);
            row.put("wins", user.wins);
            row.put("status", user.status);
            row.put("title", titleFor(user.equippedTitle));
            row.put("avatar", user.avatar);
            rows.add(row);
        }
        rows.sort(Comparator.comparing(row -> String.valueOf(row.get("username")), String.CASE_INSENSITIVE_ORDER));
        Message update = Message.of(Type.LOBBY_UPDATE).with("players", (Serializable) rows);
        for (ClientHandler handler : new ArrayList<>(online.values())) handler.send(update);
    }

    private void broadcastRoom(Room room) {
        List<Map<String, Object>> playerRows = new ArrayList<>();
        for (String name : room.players) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", name);
            row.put("ready", room.ready.contains(name));
            row.put("online", online.containsKey(key(name)));
            row.put("title", titleFor(user(name).equippedTitle));
            playerRows.add(row);
        }
        Message message = Message.of(Type.ROOM_UPDATE)
                .with("code", room.code).with("host", room.host)
                .with("roomName", room.name).with("capacity", room.capacity).with("cardTheme", room.cardTheme)
                .with("boardSize", room.boardSize)
                .with("matchmaking", room.matchmaking)
                .with("targetPlayers", room.targetPlayers)
                .with("countdownDeadline", room.countdownDeadline)
                .with("playing", room.playing).with("postGame", room.postGame)
                .with("replayVotes", new ArrayList<>(room.replayVotes))
                .with("replayDeadline", room.replayDeadline)
                .with("players", (Serializable) playerRows);
        broadcast(room, message);
    }

    private void broadcastGameStart(Room room) {
        Game game = room.game;
        broadcast(room, Message.of(Type.GAME_START)
                .with("code", room.code).with("chat", new ArrayList<>(room.chat))
                .with("roomName", room.name).with("capacity", room.capacity).with("cardTheme", room.cardTheme)
                .with("boardSize", game.boardSize)
                .with("totalPairs", game.totalPairs)
                .with("players", new ArrayList<>(game.players))
                .with("scores", new LinkedHashMap<>(game.scores))
                .with("states", game.states.clone())
                .with("values", game.knownValues())
                .with("owners", game.matchedOwners.clone())
                .with("current", game.current())
                .with("pairs", game.matchedPairs));
    }

    private void sendGameState(ClientHandler client, Room room) {
        Game game = room.game;
        if (game == null) return;
        client.send(Message.of(Type.GAME_START)
                .with("code", room.code).with("chat", new ArrayList<>(room.chat))
                .with("roomName", room.name).with("capacity", room.capacity).with("cardTheme", room.cardTheme)
                .with("boardSize", game.boardSize)
                .with("totalPairs", game.totalPairs)
                .with("players", new ArrayList<>(game.players))
                .with("scores", new LinkedHashMap<>(game.scores))
                .with("states", game.states.clone())
                .with("values", game.visibleValues())
                .with("owners", game.matchedOwners.clone())
                .with("current", game.current())
                .with("pairs", game.matchedPairs));
        client.send(Message.of(Type.TURN_UPDATE).with("current", game.current()).with("deadline", game.deadline));
    }

    private void broadcast(Room room, Message message) {
        for (String name : new ArrayList<>(room.players)) {
            ClientHandler handler = online.get(key(name));
            if (handler != null) handler.send(message);
        }
    }

    private void sendEmptyRoom(ClientHandler handler) {
        handler.send(Message.of(Type.ROOM_UPDATE).with("code", "").with("host", "")
                .with("boardSize", 0).with("playing", false).with("postGame", false)
                .with("matchmaking", false).with("targetPlayers", 0).with("countdownDeadline", 0L)
                .with("players", new ArrayList<>()));
    }

    private void disconnected(ClientHandler client) {
        synchronized (lock) {
            if (client.username == null) return;
            String userKey = key(client.username);
            if (online.get(userKey) != client) return;
            online.remove(userKey);
            friendRequests.values().removeIf(r -> key(r[0]).equals(userKey) || key(r[1]).equals(userKey));
            UserRecord user = user(client.username);
            if (user.status.equals("QUEUE")) {
                queues.get(queueKey(user.queueSize, normalizeBoardSize(user.queueBoardSize), user.queueTheme)).remove(userKey);
                user.queueSize = 0;
                user.queueBoardSize = 0;
                user.queueTheme = null;
                user.status = "OFFLINE";
            } else if (user.status.equals("ROOM") || user.status.equals("PLAYING")) {
                Room room = roomOf(client.username);
                if (room != null) {
                    if (room.matchmaking && !room.playing && !room.postGame) updateMatchmakingCountdown(room);
                    else {
                        if (!room.playing && !room.postGame) cancelRoomCountdown(room);
                        broadcastRoom(room);
                    }
                }
                ScheduledFuture<?> old = disconnectTasks.remove(userKey);
                if (old != null) old.cancel(false);
                disconnectTasks.put(userKey, scheduler.schedule(() -> {
                    synchronized (lock) {
                        if (!online.containsKey(userKey)) leaveRoom(client.username, false);
                        disconnectTasks.remove(userKey);
                    }
                }, 30, TimeUnit.SECONDS));
            } else {
                user.status = "OFFLINE";
            }
            broadcastLobby();
        }
    }

    private Room roomOf(String username) {
        for (Room room : rooms.values()) {
            if (containsIgnoreCase(room.players, username)) return room;
        }
        return null;
    }

    private static int normalizeBoardSize(int size) {
        return size == 4 || size == 6 || size == 8 ? size : DEFAULT_BOARD_SIZE;
    }

    private static String queueKey(int players, int boardSize, String theme) {
        return players + "x" + boardSize + ":" + theme;
    }

    private String newRoomCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        String code;
        do {
            StringBuilder builder = new StringBuilder(6);
            for (int i = 0; i < 6; i++) builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
            code = builder.toString();
        } while (rooms.containsKey(code));
        return code;
    }

    private UserRecord user(String username) {
        return users.get(key(username));
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private static boolean containsIgnoreCase(Collection<String> values, String target) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(target));
    }

    private static String findName(Collection<String> values, String target) {
        return values.stream().filter(value -> value.equalsIgnoreCase(target)).findFirst().orElse(null);
    }

    private static String hash(String input) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String titleFor(String productId) {
        ShopProduct product = productById(productId);
        return product != null && product.type.equals("TITLE") ? product.name.replace("Danh hiệu ", "") : "";
    }

    @SuppressWarnings("unchecked")
    private void loadState() {
        if (mysqlStorage) {
            users.putAll(databaseStore.loadUsers());
            if (!users.isEmpty() || !Files.exists(stateFile)) return;
            loadLegacyFile();
            if (!users.isEmpty()) {
                databaseStore.saveUsers(users);
                System.out.println("Đã nhập " + users.size() + " tài khoản từ data/state.bin vào MySQL.");
            }
            return;
        }
        loadLegacyFile();
    }

    @SuppressWarnings("unchecked")
    private void loadLegacyFile() {
        if (!Files.exists(stateFile)) return;
        try (ObjectInputStream input = new ObjectInputStream(Files.newInputStream(stateFile))) {
            Map<String, UserRecord> saved = (Map<String, UserRecord>) input.readObject();
            users.putAll(saved);
            users.values().forEach(user -> {
                user.status = "OFFLINE";
                user.queueSize = 0;
                user.queueBoardSize = 0;
                user.queueTheme = null;
                if (user.ownedItems == null) user.ownedItems = new HashSet<>();
                if (user.equippedTheme == null || productById(user.equippedTheme) == null) {
                    user.equippedTheme = "theme_classic";
                }
                if (user.equippedTitle == null) user.equippedTitle = "";
            });
        } catch (Exception e) {
            System.err.println("Không thể đọc dữ liệu cũ: " + e.getMessage());
        }
    }

    private void saveState() {
        try { persistState(); }
        catch (Exception e) { System.err.println("Không thể lưu dữ liệu: " + e.getMessage()); }
    }

    private void persistState() throws Exception {
        if (mysqlStorage) { databaseStore.saveUsers(users); return; }
        Files.createDirectories(stateFile.getParent());
        Path temp = stateFile.resolveSibling("state.bin.tmp");
        try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(temp))) {
            output.writeObject(users);
        }
        Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private final class ClientHandler implements Runnable {
        private final Socket socket;
        private ObjectOutputStream output;
        private ObjectInputStream input;
        private String username;

        private ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (socket) {
                output = new ObjectOutputStream(socket.getOutputStream());
                output.flush();
                input = new ObjectInputStream(socket.getInputStream());
                while (!socket.isClosed()) {
                    Object object = input.readObject();
                    if (object instanceof Message message) handle(this, message);
                }
            } catch (EOFException | SocketException ignored) {
            } catch (Exception e) {
                System.err.println("Lỗi client: " + e.getMessage());
            } finally {
                disconnected(this);
            }
        }

        private synchronized void send(Message message) {
            if (output == null) return;
            try {
                output.reset();
                output.writeObject(message);
                output.flush();
            } catch (IOException e) {
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void error(String text) {
            send(Message.of(Type.ERROR).with("message", text));
        }
    }

    static final class UserRecord implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        final String username;
        String passwordHash;
        String avatar = "";
        int rating;
        int wins;
        int games;
        int coins;
        int queueSize;
        int queueBoardSize;
        String queueTheme;
        String status = "OFFLINE";
        Set<String> ownedItems = new HashSet<>();
        String equippedTheme = "theme_classic";
        String equippedTitle = "";
        final List<String> history = new ArrayList<>();

        UserRecord(String username, String passwordHash) {
            this.username = username;
            this.passwordHash = passwordHash;
        }

        double winRate() {
            return games == 0 ? 0 : wins * 100.0 / games;
        }
    }

    private record ShopProduct(String id, String name, String description,
                               int price, String type, String color) { }

    private static final class Room {
        final String code;
        String name;
        int capacity = 4;
        String cardTheme = "";
        final int boardSize;
        String host;
        final List<String> players = new ArrayList<>();
        final List<ChatEntry> chat = new ArrayList<>();
        final Set<String> ready = new HashSet<>();
        final Set<String> replayVotes = new HashSet<>();
        boolean playing;
        boolean postGame;
        boolean matchmaking;
        int targetPlayers;
        long countdownDeadline;
        ScheduledFuture<?> countdownFuture;
        long replayDeadline;
        ScheduledFuture<?> replayFuture;
        Game game;

        Room(String code, String host, int boardSize) {
            this.code = code;
            this.name = "Phòng " + code;
            this.host = host;
            this.boardSize = boardSize;
        }
    }

    private static final class Invite {
        final String id;
        final String sender;
        final String target;
        final int boardSize;

        final String roomCode;
        final long expiresAt;

        Invite(String id, String sender, String target, int boardSize, String roomCode, long expiresAt) {
            this.roomCode = roomCode;
            this.expiresAt = expiresAt;
            this.id = id;
            this.sender = sender;
            this.target = target;
            this.boardSize = boardSize;
        }
    }

    private static final class Game {
        final List<String> players;
        final Set<String> active;
        final Set<String> forfeited = new HashSet<>();
        final Map<String, Integer> scores = new LinkedHashMap<>();
        final List<String> events = new ArrayList<>();
        final int boardSize;
        final int totalPairs;
        final int[] values;
        final int[] states;
        final String[] matchedOwners;
        final List<Integer> selected = new ArrayList<>(2);
        int currentIndex;
        int matchedPairs;
        int turns;
        int turnToken;
        long deadline;
        long lastValidAction = System.currentTimeMillis();
        boolean resolving;
        boolean finished;
        ScheduledFuture<?> turnFuture;
        ScheduledFuture<?> inactivityFuture;

        Game(List<String> roomPlayers, int boardSize, Random random) {
            players = new ArrayList<>(roomPlayers);
            Collections.shuffle(players, random);
            active = new LinkedHashSet<>(players);
            for (String player : players) scores.put(player, 0);
            this.boardSize = boardSize;
            this.totalPairs = boardSize * boardSize / 2;
            this.values = new int[boardSize * boardSize];
            this.states = new int[boardSize * boardSize];
            this.matchedOwners = new String[boardSize * boardSize];
            List<Integer> cards = new ArrayList<>();
            for (int value = 1; value <= totalPairs; value++) {
                cards.add(value);
                cards.add(value);
            }
            Collections.shuffle(cards, random);
            for (int i = 0; i < values.length; i++) values[i] = cards.get(i);
        }

        String current() {
            return players.get(currentIndex);
        }

        int[] knownValues() {
            int[] known = new int[values.length];
            Arrays.fill(known, -1);
            return known;
        }

        int[] visibleValues() {
            int[] visible = new int[values.length];
            Arrays.fill(visible, -1);
            for (int i = 0; i < values.length; i++) if (states[i] != 0) visible[i] = values[i];
            return visible;
        }
    }
}
