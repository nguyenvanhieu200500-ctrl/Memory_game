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
    static final List<ShopProduct> SHOP_PRODUCTS = List.of(
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
    private final WhisperStore whisperStore;
    private final FriendshipStore friendshipStore;
    private boolean accountStatsDirty;
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
    private volatile boolean closed;
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
        whisperStore = new WhisperStore(databaseStore, dataDirectory.resolve("private-messages.bin"));
        Path friendsFile = stateFile.resolveSibling("friends.properties");
        if (Files.exists(friendsFile)) {
            try (InputStream in = Files.newInputStream(friendsFile)) { friendships.load(in); }
            catch (IOException e) { throw new UncheckedIOException("Không thể đọc danh sách bạn bè", e); }
        }
        friendshipStore = mysqlStorage ? new FriendshipStore(databaseStore) : null;
        if(mysqlStorage) friendshipStore.importLegacy(friendships);
        System.out.println(mysqlStorage
                ? "Lưu trữ: MySQL (tài khoản, thống kê, vật phẩm và lịch sử)"
                : "Lưu trữ: tệp nhị phân dành cho kiểm thử");
    }

    public void start() throws IOException {
        Files.createDirectories(stateFile.getParent());
        serverSocket = new ServerSocket(port);
        try {
            if (mysqlStorage) {
                databaseStore.acquireServerLease();
                recoverGames();
                scheduler.scheduleAtFixedRate(() -> { synchronized(lock) {
                    if(closed)return;
                    try { databaseStore.heartbeatServerLease(); }
                    catch(RuntimeException e){try{close();}catch(IOException ignored){}return;}
                } },30,30,TimeUnit.SECONDS);
                scheduler.scheduleAtFixedRate(() -> { synchronized(lock) {
                    if(!closed) for(Room r:new ArrayList<>(rooms.values()))if(r.game!=null && !r.game.paused && !r.game.finished)checkpoint(r);
                } },1,1,TimeUnit.SECONDS);
            }
        } catch (RuntimeException e) { if(mysqlStorage)databaseStore.releaseServerLease();serverSocket.close();throw e; }
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
        synchronized (lock) {
            if (closed) return;
            closed = true;
            running = false;
            scheduler.shutdownNow();
            try {
                if (mysqlStorage) for (Room room : rooms.values()) {
                    Game game=room.game;
                    if (game != null && !room.pendingStart) {
                        byte[] payload=encodeSnapshot(room);
                        // Completed matches cannot be rewritten as snapshots after an uncertain commit.
                        if(databaseStore.playingSnapshots().stream().anyMatch(stored->stored.id().equals(game.id)))
                            room.lastRevision=databaseStore.saveSnapshot(game.id,payload,room.lastRevision);
                    }
                }
                saveState();
            } catch (RuntimeException e) { throw new IOException("Không lưu được trạng thái dừng server",e); }
            finally {
                if (serverSocket != null) serverSocket.close();
                for (ClientHandler client : online.values()) try { client.socket.close(); } catch (IOException ignored) { }
                clients.shutdownNow();
                if(mysqlStorage)databaseStore.releaseServerLease();
            }
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
                String status = game != null && game.paused ? "Tạm dừng / chờ xác nhận" : room.playing ? "Đang chơi" : room.postGame ? "Kết thúc / chờ chơi lại"
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
            if (closed) return;
            if (message.type() != Type.LOGIN && message.type() != Type.REGISTER && client.username == null) {
                client.error("Bạn cần đăng nhập trước.");
                return;
            }
            try {
                if(mysqlStorage && accountStatsDirty)refreshAccountStats();
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
                    case WHISPER_PAGE_REQUEST -> whisperPage(client,message);
                    case WHISPER_READ -> {
                        try { whisperStore.markRead(client.username,message.string("username"),message.longValue("through")); }
                        catch(java.sql.SQLException e){throw new IllegalStateException("Không lưu được trạng thái đã đọc",e);}
                    }
                    case WHISPER_SEND -> whisper(client, message.string("username"), message.string("text"));
                    case CHAT_SEND -> sendChat(client, message.string("text"));
                    case GAME_RESUME -> confirmResume(client,message.string("matchId"));
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

        if (user == null) {
            client.error("Tài khoản không tồn tại. Hãy đăng ký trước.");
            return;
        }
        if (!PasswordHashes.verify(password,user.passwordHash)) {
            client.error("Sai mật khẩu.");
            return;
        }

        if(PasswordHashes.legacy(user.passwordHash)) {
            String previous=user.passwordHash, upgraded=PasswordHashes.hash(password);
            if(mysqlStorage)databaseStore.updatePassword(user.username,previous,upgraded);
            user.passwordHash=upgraded;
            if(!mysqlStorage)try{persistState();}catch(Exception e){user.passwordHash=previous;throw new IllegalStateException("Không nâng cấp được mật khẩu",e);}
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

        try {
            if(mysqlStorage) for(var invitation:friendshipStore.pending(client.username))
                client.send(Message.of(Type.FRIEND_RECEIVED).with("from",invitation.sender()).with("requestId",invitation.id()));
            client.send(Message.of(Type.WHISPER_HISTORY).with("conversations", whisperStore.history(client.username)));
        } catch (Exception e) {
            client.error("Không thể tải lịch sử tin nhắn từ server. Vui lòng đăng nhập lại để thử lại.");
        }
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
        UserRecord created=new UserRecord(username, PasswordHashes.hash(password));
        if (mysqlStorage) databaseStore.saveUsers(Map.of(userKey,created));
        users.put(userKey, created);
        if (!mysqlStorage) {
            try { persistState(); }
            catch(Exception e) { users.remove(userKey); client.error("Không lưu được tài khoản. Vui lòng thử lại."); return; }
        }
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
        room.cardTheme = cardTheme.isEmpty() ? "FRUIT" : cardTheme;
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

    /** Server-only serialization: no hidden card values are included in protocol messages. */
    private record Snapshot(Room room, long turnRemaining, long idleRemaining, long actionRemaining) implements Serializable {
        @Serial private static final long serialVersionUID=1L;
    }

    private byte[] encodeSnapshot(Room room) {
        Game g=room.game;long now=System.currentTimeMillis();
        long turn=g.paused?g.remainingTurn:Math.max(0,g.deadline-now);
        long idle=g.paused?g.remainingIdle:Math.max(0,g.lastValidAction+60_000-now);
        long action=g.paused?g.remainingAction:Math.max(0,g.actionDeadline-now);
        try(var bytes=new ByteArrayOutputStream();var out=new ObjectOutputStream(bytes)) {
            out.writeObject(new Snapshot(room,Math.min(15_000,turn),Math.min(60_000,idle),Math.min(1310,action)));
            out.flush();return bytes.toByteArray();
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private Snapshot decodeSnapshot(byte[] payload) throws IOException,ClassNotFoundException {
        if(payload==null || payload.length>1_000_000)throw new IOException("Snapshot thiếu hoặc quá lớn");
        try(var in=new ObjectInputStream(new ByteArrayInputStream(payload))) {
            in.setObjectInputFilter(info->{
                if(info.depth()>40 || info.references()>10000 || info.arrayLength()>10000 || info.streamBytes()>1_000_000)
                    return ObjectInputFilter.Status.REJECTED;
                Class<?> type=info.serialClass();if(type==null)return ObjectInputFilter.Status.UNDECIDED;
                while(type.isArray())type=type.getComponentType();
                String name=type.getName();
                return type.isPrimitive() || name.equals("memorygame.server.GameServer$Snapshot")
                    || name.equals("memorygame.server.GameServer$Room") || name.equals("memorygame.server.GameServer$Game")
                    || name.equals("memorygame.common.Protocol$ChatEntry") || name.startsWith("java.lang.") || name.startsWith("java.util.")
                    ?ObjectInputFilter.Status.ALLOWED:ObjectInputFilter.Status.REJECTED;
            });
            Object object=in.readObject();if(!(object instanceof Snapshot snapshot))throw new IOException("Sai định dạng snapshot");
            return snapshot;
        }
    }
    private void validateSnapshot(Snapshot s,DatabaseStore.StoredSnapshot stored) throws IOException {
        Room r=s.room();Game g=r==null?null:r.game;
        if(g==null || !r.playing || r.postGame || !g.id.equals(stored.id()) || !r.code.equals(stored.room())
                || r.boardSize!=stored.board() || !r.cardTheme.equals(stored.theme()) || g.startedAt!=stored.started()
                || !new HashSet<>(g.players).equals(stored.players()) || g.players.size()!=stored.playerCount()
                || g.players.size()<2 || g.players.size()>4 || g.boardSize!=r.boardSize
                || g.totalPairs!=g.boardSize*g.boardSize/2 || r.capacity<g.players.size() || r.capacity>4
                || !Set.of(4,6,8).contains(g.boardSize) || !Set.of("FRUIT","ANIMALS","FOOD","VEHICLES").contains(r.cardTheme)
                || r.name==null || r.name.length()>40 || (r.matchmaking ? !"".equals(r.host) : !g.players.contains(r.host))
                || !g.players.containsAll(r.players) || new HashSet<>(r.players).size()!=r.players.size()
                || !users.keySet().containsAll(g.players.stream().map(GameServer::key).toList()))throw new IOException("Cấu hình trận không khớp");
        int cells=g.boardSize*g.boardSize;
        if(g.values.length!=cells || g.states.length!=cells || g.matchedOwners.length!=cells
                || !g.scores.keySet().equals(stored.players()) || !g.players.containsAll(g.active) || !g.players.containsAll(g.forfeited)
                || !Collections.disjoint(g.active,g.forfeited) || g.active.size()+g.forfeited.size()!=g.players.size()
                || g.currentIndex<0 || g.currentIndex>=g.players.size() || g.turns<0 || g.turnToken<0
                || g.recoveryDeadline<0 || s.turnRemaining()<0 || s.turnRemaining()>15_000 || s.idleRemaining()<0 || s.idleRemaining()>60_000
                || s.actionRemaining()<0 || s.actionRemaining()>1310 || !Set.of("TURN","WRONG_PAIR","MATCH_DELAY","SETTLING").contains(g.phase))
            throw new IOException("Trạng thái trận không hợp lệ");
        int[] counts=new int[g.totalPairs+1];Map<String,Integer> scored=new HashMap<>();Set<Integer> faceUp=new HashSet<>();int matched=0;
        Map<Integer,String> owners=new HashMap<>();Map<Integer,Integer> matchedCounts=new HashMap<>();
        for(int i=0;i<cells;i++) {
            int value=g.values[i],state=g.states[i];String owner=g.matchedOwners[i];
            if(value<1 || value>g.totalPairs || state<0 || state>2)throw new IOException("Thẻ không hợp lệ");
            counts[value]++;
            if(state==1)faceUp.add(i);
            if(state==2) {
                if(owner==null || !g.players.contains(owner))throw new IOException("Người ghép không hợp lệ");
                if(owners.containsKey(value) && !owner.equals(owners.get(value)))throw new IOException("Chủ cặp thẻ không khớp");
                owners.put(value,owner);matchedCounts.merge(value,1,Integer::sum);scored.merge(owner,1,Integer::sum);matched++;
            } else if(owner!=null)throw new IOException("Thẻ chưa ghép có chủ");
        }
        for(int v=1;v<=g.totalPairs;v++)if(counts[v]!=2 || (matchedCounts.containsKey(v)&&matchedCounts.get(v)!=2))throw new IOException("Thiếu cặp thẻ");
        if(matched!=g.matchedPairs*2 || !faceUp.equals(new HashSet<>(g.selected)) || g.selected.size()!=faceUp.size() || faceUp.size()>2)
            throw new IOException("Các thẻ được chọn không khớp");
        for(String name:g.players)if(g.scores.get(name)==null || g.scores.get(name)*2!=scored.getOrDefault(name,0))throw new IOException("Điểm không khớp thẻ");
        boolean wrong=g.phase.equals("WRONG_PAIR");
        if(wrong!=(g.resolving) || (wrong && (g.selected.size()!=2 || g.values[g.selected.get(0)]==g.values[g.selected.get(1)]))
                || (g.phase.equals("TURN") && (g.selected.size()>1 || !g.active.contains(g.current())))
                || (g.phase.equals("MATCH_DELAY") && !g.selected.isEmpty())
                || g.finished!=g.phase.equals("SETTLING") || (g.finished && (g.endReason==null || g.endedAt<g.startedAt)))
            throw new IOException("Giai đoạn xử lý không hợp lệ");
    }
    private Room pausedRoom(Snapshot s,byte[] payload) {
        Room r=s.room();Game g=r.game;
        g.paused=true;g.remainingTurn=s.turnRemaining();g.remainingIdle=s.idleRemaining();g.remainingAction=s.actionRemaining();
        g.resumeVotes.clear();g.deadline=0;g.actionDeadline=0;
        r.lastCheckpoint=payload;r.players.removeIf(g.forfeited::contains);
        if(!r.matchmaking && !r.players.isEmpty() && !r.players.contains(r.host))r.host=r.players.get(0);
        return r;
    }
    private void recoverGames() {
        for(var stored:databaseStore.playingSnapshots()) {
            Room room;
            try {
                if(stored.version()!=1 || stored.payload()==null || !DatabaseStore.checksum(stored.payload()).equals(stored.checksum()))throw new IOException("Snapshot thiếu/hỏng");
                Snapshot snapshot=decodeSnapshot(stored.payload());validateSnapshot(snapshot,stored);
                room=pausedRoom(snapshot,stored.payload());room.lastRevision=stored.revision();
                if(rooms.containsKey(room.code) || room.game.active.stream().anyMatch(name->roomOf(name)!=null))throw new IOException("Trùng phòng hoặc người chơi");
            }catch(Exception e) {
                databaseStore.interruptMatch(stored.id(),System.currentTimeMillis(),"Không khôi phục được ván: snapshot thiếu hoặc không hợp lệ",Map.of(),Set.of());
                System.err.println("Không khôi phục ván "+stored.id()+": "+e.getMessage());continue;
            }
            rooms.put(room.code,room);
            scheduleRecoveryTimeout(room);
            if(!room.game.finished && !checkpoint(room))continue;
            for(String name:room.players)user(name).status="PLAYING";
            if(room.game.phase.equals("SETTLING")) {
                room.game.finished=false;
                finishGame(room,room.game.endReason,room.game.endAbnormal);
            }
        }
    }
    private static final long RECOVERY_WAIT_MILLIS=120_000;
    private void scheduleRecoveryTimeout(Room room) {
        Game game=room.game;
        if(game==null || game.finished)return;
        if(game.recoveryDeadline==0)game.recoveryDeadline=System.currentTimeMillis()+RECOVERY_WAIT_MILLIS;
        if(room.recoveryFuture!=null)room.recoveryFuture.cancel(false);
        room.recoveryFuture=scheduler.schedule(() -> { synchronized(lock) {
            if(closed || rooms.get(room.code)!=room || room.game!=game || !game.paused || game.finished)return;
            // Apply all absent/unconfirmed players together, avoiding a spurious last-absent winner.
            Set<String> missing=new HashSet<>(game.active);
            missing.removeIf(name->online.containsKey(key(name)) && game.resumeVotes.contains(name));
            for(String name:missing) {game.active.remove(name);game.forfeited.add(name);game.resumeVotes.remove(name);}
            if(game.active.size()<=1) {
                finishGame(room,game.active.isEmpty()?"Tất cả đã bỏ cuộc sau thời gian chờ kết nối lại":"Chỉ còn một người chơi",true);
                return;
            }
            if(!game.active.contains(game.current())) {
                for(int index:game.selected)game.states[index]=0;
                game.selected.clear();game.resolving=false;game.phase="TURN";
                while(!game.active.contains(game.current()))game.currentIndex=(game.currentIndex+1)%game.players.size();
                game.remainingTurn=15_000;
            }
            if(!checkpoint(room))return;
            ClientHandler remaining=online.get(key(game.current()));
            if(remaining!=null)confirmResume(remaining,game.id);
        } },Math.max(3000,game.recoveryDeadline-System.currentTimeMillis()),TimeUnit.MILLISECONDS);
    }

    private void cancelGameTimers(Game game) {
        if(game.turnFuture!=null)game.turnFuture.cancel(false);
        if(game.inactivityFuture!=null)game.inactivityFuture.cancel(false);
        if(game.actionFuture!=null)game.actionFuture.cancel(false);
    }
    private boolean checkpoint(Room room) {
        if(room.pendingStart)return false;
        if(!mysqlStorage)return true;
        try {
            byte[] payload=encodeSnapshot(room);room.lastRevision=databaseStore.saveSnapshot(room.game.id,payload,room.lastRevision);room.lastCheckpoint=payload;return true;
        }catch(RuntimeException failure) {
            cancelGameTimers(room.game);
            try {
                byte[] payload=room.lastCheckpoint;long revision=room.lastRevision;
                try {
                    var stored=databaseStore.playingSnapshots().stream().filter(row->row.id().equals(room.game.id)).findFirst().orElseThrow();
                    if(stored.version()!=1 || !DatabaseStore.checksum(stored.payload()).equals(stored.checksum()))throw new IOException("Snapshot hỏng");
                    Snapshot committed=decodeSnapshot(stored.payload());validateSnapshot(committed,stored);
                    payload=stored.payload();revision=stored.revision();
                }catch(Exception unavailable) { /* Freeze the last acknowledged state; CAS prevents overwriting an uncertain commit. */ }
                Room restored=pausedRoom(decodeSnapshot(payload),payload);restored.lastRevision=revision;
                rooms.put(room.code,restored);
                if(restored.game.finished) {
                    scheduler.schedule(() -> { synchronized(lock) {
                        if(!closed && rooms.get(restored.code)==restored && restored.game!=null && restored.game.finished) {
                            restored.game.finished=false;finishGame(restored,restored.game.endReason,restored.game.endAbnormal);
                        }
                    } },3,TimeUnit.SECONDS);
                } else scheduleRecoveryTimeout(restored);
                for(String name:restored.players)user(name).status="PLAYING";
                for(String name:restored.players){ClientHandler client=online.get(key(name));if(client!=null)sendGameState(client,restored);}
            }catch(Exception e){room.game.paused=true;}
            broadcast(room,Message.of(Type.ERROR).with("message","Chưa lưu được trạng thái ván. Ván đã tạm dừng; hãy xác nhận tiếp tục khi kết nối database ổn định."));
            return false;
        }
    }
    private void sendRecoveryState(Room room) {
        Game g=room.game;
        broadcast(room,Message.of(Type.GAME_PAUSED).with("matchId",g.id).with("paused",g.paused)
                .with("recoveryDeadline",g.recoveryDeadline).with("confirmed",new ArrayList<>(g.resumeVotes)).with("required",new ArrayList<>(g.active)));
    }
    private void confirmResume(ClientHandler client,String matchId) {
        Room room=roomOf(client.username);Game g=room==null?null:room.game;
        if(g==null || !g.id.equals(matchId) || !g.paused || g.finished || !g.active.contains(client.username)) {
            client.error("Không có ván tạm dừng cần xác nhận.");return;
        }
        g.resumeVotes.add(client.username);
        if(!checkpoint(room))return;
        if(g.resumeVotes.containsAll(g.active) && g.active.stream().allMatch(name->online.containsKey(key(name)))) {
            long now=System.currentTimeMillis();g.paused=false;g.recoveryDeadline=0;
            if(room.recoveryFuture!=null)room.recoveryFuture.cancel(false);
            g.deadline=now+g.remainingTurn;g.lastValidAction=now-(60_000-g.remainingIdle);g.actionDeadline=now+g.remainingAction;
            if(!checkpoint(room))return;
            for(String name:room.players){ClientHandler c=online.get(key(name));if(c!=null)sendGameState(c,room);}
            armGameTimers(room);
        } else sendRecoveryState(room);
    }

    private void startGame(Room room) {
        Game next = new Game(room.players, room.boardSize, random);
        next.deadline=System.currentTimeMillis()+15_000;
        List<ChatEntry> previousChat=new ArrayList<>(room.chat);boolean previousPostGame=room.postGame;
        room.postGame=false;room.chat.clear();
        room.playing=true;room.game=next;
        if (mysqlStorage) {
            try {
                byte[] payload=encodeSnapshot(room);
                databaseStore.beginMatch(next.id,room.code,room.boardSize,room.cardTheme,next.startedAt,next.players,payload);
                room.lastCheckpoint=payload;room.lastRevision=1;
            } catch (RuntimeException e) {
                cancelRoomCountdown(room);
                if(DatabaseStore.unknownCommit(e)) {
                    room.pendingStart=true;next.paused=true;
                    broadcast(room,Message.of(Type.ERROR).with("message","Chưa xác định được kết quả tạo trận. Phòng tạm dừng để đối chiếu database; không tạo trận khác."));
                    reconcileStart(room,next,previousChat,previousPostGame);return;
                }
                room.playing=false;room.game=null;room.postGame=previousPostGame;room.chat.addAll(previousChat);
                broadcast(room,Message.of(Type.ERROR).with("message","Không tạo được trận trong database. Hãy thử bắt đầu lại."));
                broadcastRoom(room);return;
            }
        }
        room.playing = true;
        room.postGame = false;
        room.replayVotes.clear();
        if (room.replayFuture != null) room.replayFuture.cancel(false);
        cancelRoomCountdown(room);
        room.chat.clear();
        room.game = next;
        for (String name : room.players) user(name).status = "PLAYING";
        broadcastGameStart(room);
        broadcast(room,Message.of(Type.TURN_UPDATE).with("current",next.current()).with("deadline",next.deadline));
        armGameTimers(room);
        broadcastLobby();
    }

    private void reconcileStart(Room room,Game pending,List<ChatEntry> previousChat,boolean previousPostGame) {
        scheduler.schedule(() -> { synchronized(lock) {
            if(closed || rooms.get(room.code)!=room || !room.pendingStart)return;
            try {
                var saved=databaseStore.playingSnapshots().stream().filter(row->row.id().equals(pending.id)).findFirst();
                if(saved.isEmpty()) {
                    room.pendingStart=false;room.game=null;room.playing=false;room.postGame=previousPostGame;
                    room.chat.clear();room.chat.addAll(previousChat);broadcastRoom(room);return;
                }
                var stored=saved.get();
                if(stored.version()!=1 || !DatabaseStore.checksum(stored.payload()).equals(stored.checksum()))throw new IOException("Snapshot không hợp lệ");
                Snapshot snapshot=decodeSnapshot(stored.payload());validateSnapshot(snapshot,stored);
                Room restored=pausedRoom(snapshot,stored.payload());restored.lastRevision=stored.revision();rooms.put(room.code,restored);
                scheduleRecoveryTimeout(restored);if(!checkpoint(restored))return;
                for(String name:restored.players) {user(name).status="PLAYING";ClientHandler client=online.get(key(name));if(client!=null)sendGameState(client,restored);}
                broadcastRoom(restored);broadcastLobby();
            }catch(Exception unavailable){reconcileStart(room,pending,previousChat,previousPostGame);}
        } },3,TimeUnit.SECONDS);
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
        if(room.pendingStart) {ClientHandler c=online.get(key(username));if(c!=null)c.error("Đang đối chiếu kết quả tạo trận; vui lòng chờ.");return;}
        if (room.matchmaking && !room.playing && !room.postGame) {
            dissolveMatchmakingRoom(room, username, voluntary);
            return;
        }
        if (!room.playing && !room.postGame) cancelRoomCountdown(room);
        if (room.playing && room.game != null) {
            forfeit(room, username, voluntary ? "bỏ cuộc" : "mất kết nối");
            if(rooms.get(room.code)!=room)return;
        }
        room.players.remove(username);
        if (room.game != null && !room.game.finished) {
            if (room.game.paused && !room.game.active.contains(room.game.current()) && !room.game.active.isEmpty()) {
                while (!room.game.active.contains(room.game.current()))
                    room.game.currentIndex = (room.game.currentIndex + 1) % room.game.players.size();
                room.game.phase = "TURN"; room.game.remainingTurn = 15_000;
            }
            if (!checkpoint(room)) return;
            if (room.game.paused) sendRecoveryState(room);
        }
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
        if(mysqlStorage) {
            client.send(Message.of(Type.FRIEND_UPDATE).with("friends",friendshipStore.friends(client.username)));
            return;
        }
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
        if(mysqlStorage) {
            String id=friendshipStore.request(sender.username,target.username);
            target.send(Message.of(Type.FRIEND_RECEIVED).with("from",sender.username).with("requestId",id));
            sender.send(Message.of(Type.FRIEND_UPDATE).with("notice","Đã gửi lời mời kết bạn đến "+target.username+"."));
            return;
        }
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
        if(mysqlStorage) {
            String from=friendshipStore.reply(target.username,id,accepted);
            sendFriends(target);
            ClientHandler sender=online.get(key(from));
            if(sender!=null) {
                sendFriends(sender);
                sender.send(Message.of(Type.FRIEND_UPDATE).with("notice",target.username+(accepted?" đã chấp nhận lời mời kết bạn.":" đã từ chối lời mời kết bạn.")));
            }
            return;
        }
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

    private void whisperPage(ClientHandler client,Message request) {
        try {
            var page=whisperStore.page(client.username,request.string("username"),request.longValue("before"));
            client.send(Message.of(Type.WHISPER_PAGE).with("username",request.string("username"))
                .with("messages",new ArrayList<>(page.messages())).with("before",page.before()).with("newest",page.newest())
                .with("hasMore",page.hasMore()));
        }catch(java.sql.SQLException e){throw new IllegalStateException("Không đọc được lịch sử tin nhắn",e);}
    }

    private void whisper(ClientHandler sender, String name, String text) {
        ClientHandler target = socialTarget(sender, name);
        if (target == null) return;
        text = text.strip();
        if (text.isBlank() || text.length() > Protocol.CHAT_MAX_LENGTH || text.codePoints().anyMatch(Character::isISOControl)) {
            sender.error("Tin nhắn cần có 1–300 ký tự và không chứa ký tự điều khiển."); return;
        }
        ChatEntry entry = new ChatEntry(sender.username, text, System.currentTimeMillis());
        long messageId;
        try { messageId=whisperStore.append(target.username, entry); }
        catch (Exception e) {
            sender.error("Không thể lưu tin nhắn trên server. Tin chưa được gửi, vui lòng thử lại.");
            return;
        }
        Message message = Message.of(Type.WHISPER_MESSAGE).with("from", sender.username)
                .with("to", target.username).with("entry", entry).with("id",messageId);
        target.send(message); sender.send(message);
    }

    private void sendChat(ClientHandler client, String text) {
        Room room = roomOf(client.username);
        if (room == null || !room.playing || room.game == null || room.game.finished
                || !room.game.active.contains(client.username) || room.game.paused) {
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
        if(!checkpoint(room))return;
        broadcast(room, Message.of(Type.CHAT_MESSAGE).with("code", room.code).with("entry", entry));
    }

    private void pickCard(ClientHandler client, int index) {
        Room room = roomOf(client.username);
        if (room == null || !room.playing || room.game == null) {
            client.error("Bạn không ở trong trận."); return;
        }
        Game game = room.game;
        if (game.finished || game.paused || !game.phase.equals("TURN") || !game.current().equals(client.username)) {
            client.error("Chưa đến lượt của bạn hoặc ván đang tạm dừng."); return;
        }
        if (index < 0 || index >= game.values.length || game.states[index] != 0) {
            client.error("Ô thẻ không hợp lệ hoặc đã được ghép."); return;
        }
        game.states[index] = 1;
        game.selected.add(index);
        game.lastValidAction = System.currentTimeMillis();
        boolean pair = game.selected.size() == 2;
        int first = game.selected.get(0), second = index;
        boolean match = pair && game.values[first] == game.values[second];
        if (pair) {
            game.turns++;
            if (match) {
                game.states[first] = game.states[second] = 2;
                game.matchedOwners[first] = game.matchedOwners[second] = client.username;
                game.matchedPairs++;
                game.scores.merge(client.username, 1, Integer::sum);
                game.selected.clear();
                game.phase = "MATCH_DELAY";
                game.actionDeadline = System.currentTimeMillis() + 400;
            } else {
                game.resolving = true;
                game.phase = "WRONG_PAIR";
                game.actionDeadline = System.currentTimeMillis() + 1310;
            }
        }
        if (!checkpoint(room)) return;
        broadcast(room, Message.of(Type.CARD_REVEALED)
                .with("index", index).with("value", game.values[index]).with("player", client.username));
        if (pair) broadcast(room, Message.of(Type.MATCH_RESULT).with("match", match)
                .with("first", first).with("second", second).with("player", client.username)
                .with("scores", new LinkedHashMap<>(game.scores)).with("pairs", game.matchedPairs));
        if (game.matchedPairs == game.totalPairs) finishGame(room, "Đã tìm đủ " + game.totalPairs + " cặp", false);
        else armGameTimers(room);
    }

    private boolean liveGame(Room room, Game game) {
        return !closed && rooms.get(room.code) == room && room.game == game && !game.finished && !game.paused;
    }

    private void armGameTimers(Room room) {
        Game game = room.game;
        cancelGameTimers(game);
        if (!liveGame(room, game)) return;
        scheduleInactivity(room);
        int token = ++game.turnToken;
        if (game.phase.equals("TURN")) {
            game.turnFuture = scheduler.schedule(() -> {
                synchronized (lock) {
                    if (!liveGame(room, game) || game.turnToken != token) return;
                    int[] indexes = game.selected.stream().mapToInt(Integer::intValue).toArray();
                    for (int index : indexes) game.states[index] = 0;
                    game.selected.clear();
                    advanceTurn(room, indexes);
                }
            }, Math.max(0, game.deadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
        } else {
            game.actionFuture = scheduler.schedule(() -> {
                synchronized (lock) {
                    if (!liveGame(room, game) || game.turnToken != token) return;
                    if (game.phase.equals("WRONG_PAIR")) {
                        int[] indexes = game.selected.stream().mapToInt(Integer::intValue).toArray();
                        for (int index : indexes) game.states[index] = 0;
                        game.selected.clear(); game.resolving = false;
                        advanceTurn(room, indexes);
                    } else if (game.matchedPairs == game.totalPairs) {
                        finishGame(room, "Đã tìm đủ " + game.totalPairs + " cặp", false);
                    } else beginTurn(room);
                }
            }, Math.max(0, game.actionDeadline - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
        }
    }

    private void beginTurn(Room room) { beginTurn(room, new int[0]); }

    private void beginTurn(Room room, int[] hidden) {
        Game game = room.game;
        if (game == null || game.finished || game.paused) return;
        if (!game.active.contains(game.current())) { advanceTurn(room); return; }
        game.phase = "TURN";
        game.deadline = System.currentTimeMillis() + TURN_SECONDS * 1000L;
        if (!checkpoint(room)) return;
        if (hidden.length > 0) broadcast(room, Message.of(Type.CARD_HIDDEN).with("indexes", hidden));
        broadcast(room, Message.of(Type.TURN_UPDATE).with("current", game.current()).with("deadline", game.deadline));
        armGameTimers(room);
    }

    private void advanceTurn(Room room) { advanceTurn(room, new int[0]); }

    private void advanceTurn(Room room, int[] hidden) {
        Game game = room.game;
        if (game == null || game.finished) return;
        for (int i = 0; i < game.players.size(); i++) {
            game.currentIndex = (game.currentIndex + 1) % game.players.size();
            if (game.active.contains(game.current())) {
                beginTurn(room, hidden);
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
        game.resumeVotes.remove(username);
        if (game.current().equals(username)) {
            cancelGameTimers(game);
            int[] indexes = game.selected.stream().mapToInt(Integer::intValue).toArray();
            for (int index : indexes) game.states[index] = 0;
            game.selected.clear();
            game.resolving = false;
            game.phase = "TURN";
            if (game.paused) {
                do { game.currentIndex = (game.currentIndex + 1) % game.players.size(); }
                while (!game.active.contains(game.current()));
                game.remainingTurn = 15_000;
                if (!checkpoint(room)) return;
                broadcast(room, Message.of(Type.CARD_HIDDEN).with("indexes", indexes));
            } else advanceTurn(room, indexes);
        }
    }

    private void scheduleInactivity(Room room) {
        Game game = room.game;
        if (game == null) return;
        if (game.inactivityFuture != null) game.inactivityFuture.cancel(false);
        long marker = game.lastValidAction;
        game.inactivityFuture = scheduler.schedule(() -> {
            synchronized (lock) {
                if (liveGame(room, game) && game.lastValidAction == marker) {
                    finishGame(room, "Tất cả người chơi không hoạt động trong 60 giây", true);
                }
            }
        }, Math.max(0, marker + 60_000 - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
    }

    private void finishGame(Room room, String reason, boolean abnormal) {
        Game game = room.game;
        if (game == null || game.finished) return;
        game.finished = true;
        game.phase = "SETTLING";
        game.resolving = false;
        game.endReason = reason; game.endAbnormal = abnormal;
        if (game.endedAt == 0) game.endedAt = System.currentTimeMillis();
        cancelGameTimers(game);
        if (!checkpoint(room)) return;

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
            int coinReward = winner ? WIN_COINS : LOSS_COINS;
            String result = stamp + " | " + reason + " | " + game.scores.getOrDefault(name, 0)
                    + " cặp | " + (forfeited ? "Bỏ cuộc" : "Hạng " + playerPlacement)
                    + " | Xếp hạng " + (delta >= 0 ? "+" : "") + delta
                    + " | +" + coinReward + " xu";
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", name);
            row.put("score", game.scores.getOrDefault(name, 0));
            row.put("placement", playerPlacement);
            row.put("winner", winner);
            row.put("winCredit", winner && winners.size() == 1);
            row.put("resultText", result);
            row.put("forfeited", forfeited);
            row.put("ratingDelta", delta);
            row.put("rating", user.rating);
            row.put("coinReward", coinReward);
            row.put("coins", user.coins);
            results.add(row);
        }
        settleGame(room, game, reason, abnormal, game.endedAt, results);
    }

    private void refreshAccountStats() {
        for (UserRecord stored : databaseStore.loadUsers().values()) {
            UserRecord live = user(stored.username);
            if (live == null) continue;
            live.rating = stored.rating; live.wins = stored.wins; live.games = stored.games; live.coins = stored.coins;
            live.ownedItems = new HashSet<>(stored.ownedItems);
            live.history.clear(); live.history.addAll(stored.history);
        }
        accountStatsDirty=false;
    }

    private void settleGame(Room room, Game game, String reason, boolean abnormal, long ended,
                            List<Map<String,Object>> results) {
        if (closed) return;
        try {
            if (mysqlStorage) {
                accountStatsDirty=true;
                databaseStore.completeMatch(game.id, reason, abnormal, ended, results);
                refreshAccountStats();
            } else {
                Map<String,int[]> before = new HashMap<>();
                Map<String,List<String>> histories = new HashMap<>();
                for (var row : results) {
                    UserRecord u=user((String)row.get("username"));
                    before.put(u.username,new int[]{u.rating,u.wins,u.games,u.coins});
                    histories.put(u.username,new ArrayList<>(u.history));
                    u.rating=Math.max(0,u.rating+(Integer)row.get("ratingDelta"));
                    u.games++; if(Boolean.TRUE.equals(row.get("winCredit"))) u.wins++;
                    u.coins+=(Integer)row.get("coinReward");
                    u.history.add(0,(String)row.get("resultText"));
                    while(u.history.size()>50) u.history.remove(u.history.size()-1);
                }
                try { persistState(); }
                catch(Exception e) {
                    for(var entry:before.entrySet()) {
                        UserRecord u=user(entry.getKey()); int[] values=entry.getValue();
                        u.rating=values[0];u.wins=values[1];u.games=values[2];u.coins=values[3];
                        u.history.clear();u.history.addAll(histories.get(u.username));
                    }
                    throw e;
                }
            }
        } catch (Exception e) {
            System.err.println("Đang thử lưu lại trận " + game.id + ": " + e.getMessage());
            broadcast(room, Message.of(Type.ERROR).with("message", "Đang lưu kết quả trận đấu. Server sẽ tự thử lại, vui lòng chờ."));
            scheduler.schedule(() -> { synchronized(lock) { settleGame(room,game,reason,abnormal,ended,results); } }, 3, TimeUnit.SECONDS);
            return;
        }
        for (var row : results) {
            String name=(String)row.get("username"); UserRecord u=user(name);
            row.put("rating",u.rating);row.put("coins",u.coins);
            ClientHandler client=online.get(key(name));
            if(client!=null) client.send(Message.of(Type.WALLET_UPDATE).with("coins",u.coins).with("earned",row.get("coinReward")));
        }
        long replayDeadline = System.currentTimeMillis() + 30_000L;
        broadcast(room, Message.of(Type.GAME_END).with("matchId", game.id).with("reason", reason)
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
            if (mysqlStorage) databaseStore.updateAvatar(u.username, avatar); else persistState();
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
        if (!PasswordHashes.verify(request.string("currentPassword"),u.passwordHash)) {
            profileResult(client, "password", false, "Mật khẩu hiện tại không đúng."); return;
        }
        if (next.length() < 6 || next.length() > 128) {
            profileResult(client, "password", false, "Mật khẩu mới cần từ 6 đến 128 ký tự."); return;
        }
        String previous = u.passwordHash;
        u.passwordHash = PasswordHashes.hash(next);
        try { if (mysqlStorage) databaseStore.updatePassword(u.username, previous, u.passwordHash); else persistState(); }
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
                .with("history", mysqlStorage ? databaseStore.history(client.username) : new ArrayList<>(user(client.username).history)));
    }

    private void sendShop(ClientHandler client) {
        UserRecord user = user(client.username);
        List<Map<String, Object>> products = new ArrayList<>();
        for (ShopProduct product : mysqlStorage ? databaseStore.productsFor(client.username) : SHOP_PRODUCTS) {
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
        if (mysqlStorage) {
            accountStatsDirty=true;
            String error = databaseStore.purchase(client.username, productId);
            refreshAccountStats();
            if (!error.isEmpty()) { client.error(error); return; }
        } else {
            ShopProduct product = productById(productId);
            if (product == null || product.price <= 0) { client.error("Vật phẩm không hợp lệ."); return; }
            if (user.ownedItems.contains(product.id)) { client.error("Bạn đã sở hữu vật phẩm này."); return; }
            if (user.coins < product.price) { client.error("Bạn chưa đủ xu để mua vật phẩm này."); return; }
            user.coins -= product.price; user.ownedItems.add(product.id);
            try { persistState(); }
            catch (Exception e) {
                user.coins += product.price; user.ownedItems.remove(product.id);
                client.error("Không lưu được giao dịch. Vui lòng thử lại."); return;
            }
        }
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
        if (!product.type.equals("THEME") && !product.type.equals("TITLE")) { client.error("Vật phẩm cũ không hỗ trợ trang bị."); return; }
        String previousTheme=user.equippedTheme, previousTitle=user.equippedTitle;
        if (product.type.equals("THEME")) user.equippedTheme = product.id;
        else user.equippedTitle = product.id;
        try {
            if (mysqlStorage) databaseStore.updateEquipment(user.username, product.id, product.type.equals("THEME"));
            else persistState();
        } catch(Exception e) {
            user.equippedTheme=previousTheme;user.equippedTitle=previousTitle;
            client.error("Không lưu được trang bị. Vui lòng thử lại.");return;
        }
        sendShop(client);
        broadcastLobby();
        Room room = roomOf(client.username);
        if (room != null) broadcastRoom(room);
    }

    private ShopProduct productById(String productId) {
        if (productId == null || productId.isBlank()) return null;
        return (mysqlStorage ? databaseStore.products(false) : SHOP_PRODUCTS).stream().filter(product -> product.id.equals(productId)).findFirst().orElse(null);
    }

    private void broadcastLobby() {
        if(accountStatsDirty)return; // Never publish balances/statistics from an uncertain transaction.
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
        broadcast(room, Message.of(Type.GAME_START).with("matchId", game.id)
                .with("recoveryDeadline",game.recoveryDeadline).with("paused",game.paused).with("resolving",game.resolving).with("confirmed",new ArrayList<>(game.resumeVotes)).with("required",new ArrayList<>(game.active))
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
        client.send(Message.of(Type.GAME_START).with("matchId", game.id)
                .with("recoveryDeadline",game.recoveryDeadline).with("paused",game.paused).with("resolving",game.resolving).with("confirmed",new ArrayList<>(game.resumeVotes)).with("required",new ArrayList<>(game.active))
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
        client.send(Message.of(Type.TURN_UPDATE).with("current", game.current()).with("deadline", game.paused?0:game.deadline).with("paused",game.paused));
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
            if (closed) return;
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
                if (room != null && room.game != null && room.game.paused) {
                    room.game.resumeVotes.remove(client.username);
                    if(checkpoint(room))sendRecoveryState(room);
                }
                ScheduledFuture<?> old = disconnectTasks.remove(userKey);
                if (old != null) old.cancel(false);
                disconnectTasks.put(userKey, scheduler.schedule(() -> {
                    synchronized (lock) {
                        if (!online.containsKey(userKey) && (room == null || room.game == null || !room.game.paused)) leaveRoom(client.username, false);
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

    private String titleFor(String productId) {
        ShopProduct product = productById(productId);
        return product != null && product.type.equals("TITLE") ? product.name.replace("Danh hiệu ", "") : "";
    }

    @SuppressWarnings("unchecked")
    private void loadState() {
        if (mysqlStorage) {
            users.putAll(databaseStore.loadUsers());
            if (Files.exists(stateFile)) {
                loadLegacyFile();
                databaseStore.saveUsers(users); // Inserts only missing accounts, including partially migrated databases.
                users.clear();users.putAll(databaseStore.loadUsers());
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
            saved.forEach(users::putIfAbsent);
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
            if(mysqlStorage)throw new IllegalStateException("Không thể đọc dữ liệu cũ; dừng nhập để tránh mất quan hệ",e);
            System.err.println("Không thể đọc dữ liệu cũ: " + e.getMessage());
        }
    }

    private void saveState() {
        try { persistState(); }
        catch (Exception e) { System.err.println("Không thể lưu dữ liệu: " + e.getMessage()); }
    }

    private void persistState() throws Exception {
        // MySQL changes are persisted by their own operations, never a whole RAM snapshot.
        if (mysqlStorage) return;
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

    record ShopProduct(String id, String name, String description,
                               int price, String type, String color) { }

    private static final class Room implements Serializable {
        @Serial private static final long serialVersionUID=1L;
        transient byte[] lastCheckpoint;
        transient long lastRevision;
        transient boolean pendingStart;
        transient ScheduledFuture<?> recoveryFuture;
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
        transient ScheduledFuture<?> countdownFuture;
        long replayDeadline;
        transient ScheduledFuture<?> replayFuture;
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

    private static final class Game implements Serializable {
        @Serial private static final long serialVersionUID=1L;
        String phase="TURN";
        boolean paused;
        long remainingTurn, remainingIdle, remainingAction, actionDeadline;
        long recoveryDeadline;
        String endReason;
        boolean endAbnormal;
        long endedAt;
        final Set<String> resumeVotes=new HashSet<>();
        transient ScheduledFuture<?> actionFuture;
        final String id = UUID.randomUUID().toString();
        final long startedAt = System.currentTimeMillis();
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
        transient ScheduledFuture<?> turnFuture;
        transient ScheduledFuture<?> inactivityFuture;

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
