package memorygame.common;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public final class Protocol {
    private Protocol() {}

    public enum Type {
        LOGIN, LOGIN_OK, REGISTER, REGISTER_OK, ERROR,
        LOBBY_UPDATE,
        PROFILE_REQUEST, PROFILE_UPDATE, AVATAR_UPDATE, PASSWORD_CHANGE, PROFILE_RESULT,
        WALLET_UPDATE, SHOP_REQUEST, SHOP_UPDATE, SHOP_BUY, SHOP_EQUIP,
        QUEUE_JOIN, QUEUE_LEAVE,
        ROOM_CREATE, ROOM_JOIN, ROOM_UPDATE, ROOM_LEAVE, ROOM_READY, ROOM_START, ROOM_KICK,
        INVITE_SEND, INVITE_RECEIVED, INVITE_REPLY,
        FRIEND_SEND, FRIEND_RECEIVED, FRIEND_REPLY, FRIEND_UPDATE, WHISPER_SEND, WHISPER_MESSAGE, WHISPER_HISTORY,
        CARD_PICK, CHAT_SEND, CHAT_MESSAGE,
        GAME_START, GAME_PAUSED, GAME_RESUME, CARD_REVEALED, CARD_HIDDEN, MATCH_RESULT, TURN_UPDATE, GAME_END,
        RANKING_REQUEST, RANKING_UPDATE, HISTORY_REQUEST, HISTORY_UPDATE,
        PLAY_AGAIN, BACK_LOBBY
    }

    public static final int CHAT_MAX_LENGTH = 300;
    public static final int CHAT_HISTORY_LIMIT = 100;

    public record ChatEntry(String username, String text, long timestamp) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    public static final class Message implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final Type type;
        private final Map<String, Object> data = new HashMap<>();

        public Message(Type type) {
            this.type = type;
        }

        public static Message of(Type type) {
            return new Message(type);
        }

        public Message with(String key, Object value) {
            data.put(key, value);
            return this;
        }

        public Type type() {
            return type;
        }

        public Object get(String key) {
            return data.get(key);
        }

        public String string(String key) {
            Object value = data.get(key);
            return value == null ? "" : String.valueOf(value);
        }

        public int integer(String key) {
            Object value = data.get(key);
            return value instanceof Number number ? number.intValue() : 0;
        }

        public long longValue(String key) {
            Object value = data.get(key);
            return value instanceof Number number ? number.longValue() : 0L;
        }

        public boolean bool(String key) {
            return Boolean.TRUE.equals(data.get(key));
        }

        @SuppressWarnings("unchecked")
        public <T> T value(String key) {
            return (T) data.get(key);
        }

        @Override
        public String toString() {
            Map<String, Object> safe = new HashMap<>(data);
            for (String key : new String[]{"password", "currentPassword", "newPassword"})
                if (safe.containsKey(key)) safe.put(key, "[hidden]");
            if (safe.containsKey("avatar")) safe.put("avatar", "[image]");
            return type + " " + safe;
        }
    }
}
