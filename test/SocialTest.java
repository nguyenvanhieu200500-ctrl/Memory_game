package memorygame.test;

import memorygame.common.Protocol.Message;
import memorygame.common.Protocol.Type;
import memorygame.common.Protocol.ChatEntry;
import memorygame.server.GameServer;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/** Real socket checks for consent, identity, privacy and friendship persistence. */
public final class SocialTest {
    public static void main(String[] args) throws Exception {
        Path data = Files.createTempDirectory("memory-social-test");
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        try (GameServer server = start(port, data)) { run(port); }
        try (GameServer server = start(port, data); Peer a = new Peer(port)) {
            a.login("socialAlice");
            check(a.until(Type.FRIEND_UPDATE).<List<String>>value("friends").contains("socialBob"), "Friendship lost after restart");
        }
        System.out.println("SOCIAL_OK consent rejection forgedReply duplicate self offline privacy identity validation persistence");
    }
    private static GameServer start(int port, Path data) throws Exception {
        GameServer server = new GameServer(port, data);
        Thread thread = new Thread(() -> { try { server.start(); } catch (IOException e) { throw new UncheckedIOException(e); } });
        thread.setDaemon(true); thread.start();
        for (int i=0; i<50; i++) {
            try (Socket socket = new Socket("127.0.0.1", port)) { return server; }
            catch (IOException e) { Thread.sleep(20); }
        }
        throw new AssertionError("Server not ready");
    }
    private static void run(int port) throws Exception {
        try (Peer a = new Peer(port); Peer b = new Peer(port); Peer c = new Peer(port)) {
            a.send(Message.of(Type.WHISPER_SEND).with("username", "socialBob").with("text", "hello")); a.until(Type.ERROR);
            a.register("socialAlice"); b.register("socialBob"); c.register("socialCarol");
            a.until(Type.FRIEND_UPDATE); b.until(Type.FRIEND_UPDATE); c.until(Type.FRIEND_UPDATE);
            a.send(Message.of(Type.FRIEND_SEND).with("username", "socialBob"));
            String id = b.until(Type.FRIEND_RECEIVED).string("requestId"); a.until(Type.FRIEND_UPDATE);
            a.send(Message.of(Type.FRIEND_SEND).with("username", "socialBob")); a.until(Type.ERROR);
            c.send(Message.of(Type.FRIEND_REPLY).with("requestId", id).with("accepted", true)); c.until(Type.ERROR);
            b.send(Message.of(Type.FRIEND_REPLY).with("requestId", id).with("accepted", false));
            check(b.until(Type.FRIEND_UPDATE).<List<String>>value("friends").isEmpty(), "Declined request created friendship");
            a.until(Type.FRIEND_UPDATE); a.until(Type.FRIEND_UPDATE);
            a.send(Message.of(Type.FRIEND_SEND).with("username", "SOCIALBOB"));
            id = b.until(Type.FRIEND_RECEIVED).string("requestId"); a.until(Type.FRIEND_UPDATE);
            b.send(Message.of(Type.FRIEND_REPLY).with("requestId", id).with("accepted", true));
            check(b.until(Type.FRIEND_UPDATE).<List<String>>value("friends").contains("socialAlice"), "Missing reciprocal friend");
            check(a.until(Type.FRIEND_UPDATE).<List<String>>value("friends").contains("socialBob"), "Missing friend"); a.until(Type.FRIEND_UPDATE);
            b.send(Message.of(Type.FRIEND_REPLY).with("requestId", id).with("accepted", true));
            b.until(Type.FRIEND_UPDATE); // Duplicate same decision is idempotent; the authoritative snapshot remains valid.
            a.send(Message.of(Type.WHISPER_SEND).with("username", "socialBob").with("from", "socialCarol").with("text", "Xin chào"));
            Message received = b.until(Type.WHISPER_MESSAGE);
            check(received.string("from").equals("socialAlice"), "Forged sender");
            ChatEntry entry = received.value("entry");
            check(entry.equals(a.until(Type.WHISPER_MESSAGE).value("entry")), "Missing echo");
            b.send(Message.of(Type.WHISPER_SEND).with("username", "socialAlice").with("text", "Chào bạn"));
            b.until(Type.WHISPER_MESSAGE); a.until(Type.WHISPER_MESSAGE);
            for (String text : List.of(" ", "x".repeat(301), "fake\nline")) {
                a.send(Message.of(Type.WHISPER_SEND).with("username", "socialBob").with("text", text)); a.until(Type.ERROR);
            }
            for (String target : List.of("socialAlice", "offline")) {
                for (Type type : List.of(Type.WHISPER_SEND, Type.FRIEND_SEND)) {
                    a.send(Message.of(type).with("username", target).with("text", "hello")); a.until(Type.ERROR);
                }
            }
            c.send(Message.of(Type.HISTORY_REQUEST)); c.until(Type.HISTORY_UPDATE); // No private event may precede this barrier.
        }
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static final class Peer implements AutoCloseable {
        final Socket socket; final ObjectOutputStream out; final ObjectInputStream in;
        Peer(int port) throws IOException {
            socket = new Socket("127.0.0.1", port); socket.setSoTimeout(5000);
            out = new ObjectOutputStream(socket.getOutputStream()); out.flush(); in = new ObjectInputStream(socket.getInputStream());
        }
        void send(Message message) throws IOException { out.reset(); out.writeObject(message); out.flush(); }
        void register(String name) throws Exception {
            send(Message.of(Type.REGISTER).with("username", name).with("password", "test123")); until(Type.REGISTER_OK); login(name);
        }
        void login(String name) throws Exception { send(Message.of(Type.LOGIN).with("username", name).with("password", "test123")); until(Type.LOGIN_OK); }
        Message until(Type type) throws Exception {
            for (int i=0; i<100; i++) {
                Message m = (Message) in.readObject(); if (m.type() == type) return m;
                if (m.type() == Type.ERROR || m.type() == Type.WHISPER_MESSAGE || m.type() == Type.FRIEND_RECEIVED)
                    throw new AssertionError("Unexpected " + m);
            }
            throw new AssertionError("Missing " + type);
        }
        public void close() throws IOException { socket.close(); }
    }
}
