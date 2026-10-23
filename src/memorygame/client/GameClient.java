package memorygame.client;

import memorygame.common.Protocol.Message;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public final class GameClient implements AutoCloseable {
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private Thread listener;
    private Consumer<Message> messageHandler;
    private Consumer<String> disconnectHandler;
    private long generation;
    private boolean closedByUser;

    public synchronized void connect(String host, int port, Consumer<Message> onMessage, Consumer<String> onDisconnect)
            throws IOException {
        generation++;
        closeSocket();
        closedByUser = false;
        messageHandler = onMessage;
        disconnectHandler = onDisconnect;
        long connectionId = generation;
        Socket newSocket = new Socket(host, port);
        try {
            newSocket.setTcpNoDelay(true);
            ObjectOutputStream newOutput = new ObjectOutputStream(newSocket.getOutputStream());
            newOutput.flush();
            ObjectInputStream newInput = new ObjectInputStream(newSocket.getInputStream());
            socket = newSocket;
            output = newOutput;
            input = newInput;
            listener = new Thread(() -> listen(connectionId, newSocket, newInput),
                    "memory-game-client-listener-" + connectionId);
        } catch (IOException e) {
            try { newSocket.close(); } catch (IOException ignored) {}
            throw e;
        }
        listener.setDaemon(true);
        listener.start();
    }

    private void listen(long connectionId, Socket activeSocket, ObjectInputStream activeInput) {
        String reason = "Mất kết nối tới server.";
        try {
            while (!activeSocket.isClosed()) {
                Object object = activeInput.readObject();
                Consumer<Message> handler;
                synchronized (this) {
                    if (connectionId != generation) return;
                    handler = messageHandler;
                }
                if (object instanceof Message message && handler != null) handler.accept(message);
            }
        } catch (EOFException ignored) {
        } catch (Exception e) {
            reason = "Mất kết nối: " + e.getMessage();
        } finally {
            notifyDisconnect(connectionId, reason);
        }
    }

    public synchronized void send(Message message) {
        if (output == null) return;
        long connectionId = generation;
        try {
            output.reset();
            output.writeObject(message);
            output.flush();
        } catch (IOException e) {
            notifyDisconnect(connectionId, "Không thể gửi dữ liệu: " + e.getMessage());
        }
    }

    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    private void notifyDisconnect(long connectionId, String reason) {
        Consumer<String> handler;
        synchronized (this) {
            if (connectionId != generation || closedByUser) return;
            generation++;
            closeSocket();
            handler = disconnectHandler;
        }
        if (handler != null) handler.accept(reason);
    }

    @Override
    public synchronized void close() {
        closedByUser = true;
        generation++;
        closeSocket();
    }

    private void closeSocket() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        socket = null;
        output = null;
        input = null;
    }
}
