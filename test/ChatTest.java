package memorygame.test;

import memorygame.common.Protocol;
import memorygame.common.Protocol.ChatEntry;
import memorygame.common.Protocol.Message;
import memorygame.common.Protocol.Type;
import memorygame.server.GameServer;
import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.UUID;

public final class ChatTest {
    static void run(GameServer server, int port) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String alice = "chatA_" + suffix, bob = "chatB_" + suffix;
        try (Peer a = new Peer(port); Peer b = new Peer(port); Peer outsider = new Peer(port)) {
            a.send(Message.of(Type.CHAT_SEND).with("text", "unauthenticated"));
            a.until(Type.ERROR);
            a.register(alice); b.register(bob); outsider.register("chatC_" + suffix);
            outsider.send(Message.of(Type.ROOM_CREATE).with("boardSize", 4));
            outsider.until(Type.ROOM_UPDATE);
            outsider.send(Message.of(Type.CHAT_SEND).with("text", "outside match"));
            outsider.until(Type.ERROR);
            a.send(Message.of(Type.ROOM_CREATE).with("boardSize", 4));
            String code = a.until(Type.ROOM_UPDATE).string("code");
            b.send(Message.of(Type.ROOM_JOIN).with("code", code));
            b.until(Type.ROOM_UPDATE);
            a.send(Message.of(Type.ROOM_READY).with("ready", true));
            b.send(Message.of(Type.ROOM_READY).with("ready", true));
            // Wait for both readiness updates before asking the host to start.
            while (true) {
                Message room = a.until(Type.ROOM_UPDATE);
                List<java.util.Map<String, Object>> players = room.value("players");
                if (players.size() == 2 && players.stream().allMatch(p -> Boolean.TRUE.equals(p.get("ready")))) break;
            }
            a.send(Message.of(Type.ROOM_START));
            a.until(Type.GAME_START); b.until(Type.GAME_START);
            var before = snapshot(server, code);
            a.send(Message.of(Type.CHAT_SEND).with("text", "Xin chào bạn 👋").with("username", bob).with("code", "fake"));
            ChatEntry first = a.until(Type.CHAT_MESSAGE).value("entry");
            check(first.equals(b.until(Type.CHAT_MESSAGE).value("entry")), "Clients disagree");
            check(first.username().equals(alice) && first.text().equals("Xin chào bạn 👋") && first.timestamp() > 0,
                    "Sender or content incorrect");
            b.send(Message.of(Type.CHAT_SEND).with("text", "Chào lại!"));
            ChatEntry reply = b.until(Type.CHAT_MESSAGE).value("entry");
            check(reply.equals(a.until(Type.CHAT_MESSAGE).value("entry")) && reply.username().equals(bob), "Reply missing");
            check(snapshot(server, code).chat().equals(List.of(first, reply)), "Monitor differs from clients");
            check(before.chat().isEmpty(), "Old snapshot was mutated");
            for (String invalid : List.of("   ", "x".repeat(301), "fake\nline")) {
                a.send(Message.of(Type.CHAT_SEND).with("text", invalid));
                a.until(Type.ERROR);
            }
            check(snapshot(server, code).chat().size() == 2, "Invalid message was saved");
            for (int i = 0; i < 101; i++) {
                a.send(Message.of(Type.CHAT_SEND).with("text", "Tin " + i));
                a.until(Type.CHAT_MESSAGE); b.until(Type.CHAT_MESSAGE);
            }
            check(snapshot(server, code).chat().size() == Protocol.CHAT_HISTORY_LIMIT, "Unbounded chat history");
            // This ordered reply acts as a barrier: no chat from another room may precede it.
            outsider.send(Message.of(Type.HISTORY_REQUEST));
            outsider.until(Type.HISTORY_UPDATE);
            b.close();
            long deadline = System.currentTimeMillis() + 3000;
            while (snapshot(server, code).players().stream().anyMatch(p -> p.name().equals(bob) && p.connected())) {
                if (System.currentTimeMillis() > deadline) throw new AssertionError("Disconnect not processed");
                Thread.sleep(20);
            }
            try (Peer resumed = new Peer(port)) {
                resumed.login(bob);
                Message state = resumed.until(Type.GAME_START);
                check(code.equals(state.string("code")) && snapshot(server, code).chat().equals(state.value("chat")),
                        "Reconnect lost room history");
            }
            a.send(Message.of(Type.BACK_LOBBY));
            a.until(Type.ROOM_UPDATE);
            a.send(Message.of(Type.CHAT_SEND).with("text", "left room"));
            a.until(Type.ERROR);
        }
        System.out.println("CHAT_OK twoWay authoritativeSender roomIsolation validation boundedHistory reconnect monitor");
    }

    private static GameServer.RoomSnapshot snapshot(GameServer server, String code) {
        return server.monitorSnapshot().rooms().stream().filter(r -> r.code().equals(code)).findFirst().orElseThrow();
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static final class Peer implements AutoCloseable {
        final Socket socket;
        final ObjectOutputStream out;
        final ObjectInputStream in;
        Peer(int port) throws Exception {
            socket = new Socket("127.0.0.1", port); socket.setSoTimeout(10000);
            out = new ObjectOutputStream(socket.getOutputStream()); out.flush();
            in = new ObjectInputStream(socket.getInputStream());
        }
        void send(Message message) throws Exception { out.reset(); out.writeObject(message); out.flush(); }
        void register(String name) throws Exception {
            send(Message.of(Type.REGISTER).with("username", name).with("password", "test123"));
            until(Type.REGISTER_OK); login(name);
        }
        void login(String name) throws Exception {
            send(Message.of(Type.LOGIN).with("username", name).with("password", "test123")); until(Type.LOGIN_OK);
        }
        Message until(Type type) throws Exception {
            for (int i = 0; i < 100; i++) {
                Message message = (Message) in.readObject();
                if (message.type() == type) return message;
                if (message.type() == Type.ERROR || message.type() == Type.CHAT_MESSAGE)
                    throw new AssertionError("Unexpected " + message);
            }
            throw new AssertionError("Missing " + type);
        }
        public void close() throws Exception { socket.close(); }
    }
}
