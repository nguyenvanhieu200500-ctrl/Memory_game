package memorygame.client;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Unread incoming messages in the current signed-in session. */
final class UnreadMessages {
    private final Map<String, Integer> counts = new HashMap<>();
    void receive(String peer, boolean incoming, boolean reading) {
        if (incoming && !reading) counts.merge(peer.toLowerCase(Locale.ROOT), 1, Integer::sum);
    }
    int count(String peer) { return counts.getOrDefault(peer.toLowerCase(Locale.ROOT), 0); }
    int total() { return counts.values().stream().mapToInt(Integer::intValue).sum(); }
    void read(String peer) { counts.remove(peer.toLowerCase(Locale.ROOT)); }
    void clear() { counts.clear(); }
}
