package memorygame.test;

import memorygame.common.Protocol.Message;
import memorygame.common.Protocol.Type;
import memorygame.server.GameServer;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.UUID;

public final class RoomSettingsTest {
    public static void main(String[] args) throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        GameServer server = new GameServer(port, Files.createTempDirectory("room-settings-test-"));
        Thread thread = new Thread(() -> { try { server.start(); } catch (Exception e) { throw new RuntimeException(e); } });
        thread.setDaemon(true); thread.start();
        try {
            for (int i = 0; i < 50; i++) {
                try (Socket ignored = new Socket("127.0.0.1", port)) { break; }
                catch (IOException e) { Thread.sleep(50); }
            }
            run(server, port);
        } finally { server.close(); thread.join(2000); }
    }
    static void run(GameServer server, int port) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String host = "rh" + suffix;
        try (Peer a = new Peer(port); Peer b = new Peer(port); Peer c = new Peer(port)) {
            a.register(host); b.register("rb" + suffix); c.register("rc" + suffix);
            for (Message bad : new Message[]{
                    request().with("capacity", 5), request().with("cardTheme", "bad"),
                    request().with("roomName", " "), request().with("roomName", "a".repeat(41)), request().with("boardSize", 7)}) {
                a.send(bad); a.until(Type.ERROR);
            }
            a.send(request()); Message room = a.until(Type.ROOM_UPDATE); checkSettings(room);
            String code = room.string("code");
            a.send(Message.of(Type.INVITE_SEND).with("username", "rc" + suffix));
            Message invite = c.until(Type.INVITE_RECEIVED);
            b.send(Message.of(Type.ROOM_JOIN).with("code", code)); checkSettings(b.until(Type.ROOM_UPDATE));
            c.send(Message.of(Type.INVITE_REPLY).with("inviteId", invite.string("inviteId")).with("accepted", true)); c.until(Type.ERROR);
            c.send(Message.of(Type.ROOM_JOIN).with("code", code)); c.until(Type.ERROR);
            a.send(Message.of(Type.ROOM_READY).with("ready", true));
            b.send(Message.of(Type.ROOM_READY).with("ready", true));
            // Drain the host's room updates until both ready flags have reached the server.
            while (true) {
                Message state = a.until(Type.ROOM_UPDATE);
                java.util.List<java.util.Map<String,Object>> players = state.value("players");
                if (players.size() == 2 && players.stream().allMatch(p -> Boolean.TRUE.equals(p.get("ready")))) break;
            }
            a.send(Message.of(Type.ROOM_START));
            Message game = a.until(Type.GAME_START); checkSettings(game); checkSettings(b.until(Type.GAME_START));
            if (((int[]) game.get("states")).length != 36) throw new AssertionError("Wrong board");
            a.close();
            boolean disconnected = false;
            for (int attempt = 0; attempt < 100; attempt++) {
                disconnected = server.monitorSnapshot().rooms().stream().filter(r -> r.code().equals(code))
                        .flatMap(r -> r.players().stream()).anyMatch(p -> p.name().equals(host) && !p.connected());
                if (disconnected) break;
                Thread.sleep(20);
            }
            if (!disconnected) throw new AssertionError("Disconnect not processed");
            try (Peer reconnect = new Peer(port)) {
                reconnect.login(host); checkSettings(reconnect.until(Type.GAME_START));
            }
        }
        testCreateAndInvite(server, port);
        testInvitationLifecycle(port);
        System.out.println("ROOM_SETTINGS_OK validation capacity join invite gameStart reconnect");
    }
    private static void testCreateAndInvite(GameServer server, int port) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String hostName = "ch" + suffix, guestName = "cg" + suffix;
        try (Peer host = new Peer(port); Peer guest = new Peer(port)) {
            host.register(hostName); guest.register(guestName);
            int before = server.monitorSnapshot().rooms().size();
            for (String invalid : new String[]{hostName, "missing" + suffix}) {
                host.send(request().with("inviteUsername", invalid)); host.until(Type.ERROR);
                if (server.monitorSnapshot().rooms().size() != before) throw new AssertionError("Invalid recipient created room");
            }
            guest.send(request()); guest.until(Type.ROOM_UPDATE);
            host.send(request().with("inviteUsername", guestName)); host.until(Type.ERROR);
            if (server.monitorSnapshot().rooms().size() != before + 1) throw new AssertionError("Busy recipient created room");
            guest.send(Message.of(Type.ROOM_LEAVE)); guest.until(Type.ROOM_UPDATE);
            host.send(request().with("inviteUsername", guestName));
            Message room = host.until(Type.ROOM_UPDATE); checkSettings(room);
            Message invite = guest.until(Type.INVITE_RECEIVED); checkSettings(invite);
            if (room.string("code").isEmpty() || !room.string("code").equals(invite.string("code"))
                    || !invite.string("roomName").equals(room.string("roomName")) || invite.integer("playerCount") != 1)
                throw new AssertionError("Invitation must reference created room");
            guest.send(Message.of(Type.INVITE_REPLY).with("inviteId", invite.string("inviteId")).with("accepted", true));
            Message joined = guest.until(Type.ROOM_UPDATE); checkSettings(joined);
            if (!room.string("code").equals(joined.string("code"))) throw new AssertionError("Joined different room");
        }
        System.out.println("CREATE_AND_INVITE_OK recipientValidation realRoom metadata accept");
    }
    private static void testInvitationLifecycle(int port) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        try (Peer host = new Peer(port); Peer guest = new Peer(port)) {
            host.register("ih" + suffix); guest.register("ig" + suffix);
            host.send(request()); host.until(Type.ROOM_UPDATE);
            for (boolean accept : new boolean[]{false, true}) {
                host.send(Message.of(Type.INVITE_SEND).with("username", "ig" + suffix));
                Message invite = guest.until(Type.INVITE_RECEIVED); checkSettings(invite);
                if (invite.integer("expiresIn") != 10 || invite.longValue("expiresAt") <= System.currentTimeMillis()) throw new AssertionError("Wrong invite expiry");
                guest.send(Message.of(Type.INVITE_REPLY).with("inviteId", invite.string("inviteId")).with("accepted", accept));
                if (accept) { checkSettings(guest.until(Type.ROOM_UPDATE)); host.until(Type.ROOM_UPDATE); }
                else host.until(Type.ERROR);
            }
            guest.send(Message.of(Type.ROOM_LEAVE)); guest.until(Type.ROOM_UPDATE); host.until(Type.ROOM_UPDATE);
            host.send(Message.of(Type.INVITE_SEND).with("username", "ig" + suffix));
            Message expiring = guest.until(Type.INVITE_RECEIVED);
            Thread.sleep(10_100);
            // No expiry ERROR should be queued: a fresh invite arrives directly.
            host.send(Message.of(Type.INVITE_SEND).with("username", "ig" + suffix));
            guest.until(Type.INVITE_RECEIVED);
            guest.send(Message.of(Type.INVITE_REPLY).with("inviteId", expiring.string("inviteId")).with("accepted", true));
            guest.until(Type.ERROR);
        }
        System.out.println("INVITATION_FLOW_OK metadata accept decline silentExpiry lateReplyRejected");
    }
    private static Message request() { return Message.of(Type.ROOM_CREATE).with("roomName", "Phòng trái cây")
            .with("capacity", 2).with("boardSize", 6).with("cardTheme", "FRUIT"); }
    private static void checkSettings(Message message) {
        if (!message.string("roomName").equals("Phòng trái cây") || message.integer("capacity") != 2
                || message.integer("boardSize") != 6 || !message.string("cardTheme").equals("FRUIT")) throw new AssertionError("Lost settings: " + message);
    }
    private static final class Peer implements AutoCloseable {
        final Socket socket; final ObjectOutputStream out; final ObjectInputStream in;
        Peer(int port) throws Exception {
            socket = new Socket("127.0.0.1", port); socket.setSoTimeout(10000);
            out = new ObjectOutputStream(socket.getOutputStream()); out.flush(); in = new ObjectInputStream(socket.getInputStream());
        }
        void send(Message message) throws Exception { out.reset(); out.writeObject(message); out.flush(); }
        void register(String name) throws Exception { send(Message.of(Type.REGISTER).with("username",name).with("password","test123")); until(Type.REGISTER_OK); login(name); }
        void login(String name) throws Exception { send(Message.of(Type.LOGIN).with("username",name).with("password","test123")); until(Type.LOGIN_OK); }
        Message until(Type type) throws Exception {
            for (int i=0;i<150;i++) { Message message=(Message)in.readObject(); if(message.type()==type)return message;
                if(message.type()==Type.ERROR)throw new AssertionError("Unexpected " + message); }
            throw new AssertionError("Missing " + type);
        }
        public void close() throws Exception { socket.close(); }
    }
}
