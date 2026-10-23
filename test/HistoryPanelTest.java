package memorygame.client;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class HistoryPanelTest {
    public static void main(String[] args) throws Exception {
        String recent = "13/10/2026 14:14 | Chỉ còn một người chơi | 1 cặp | Bỏ cuộc | Xếp hạng -1 | +10 xu";
        String winner = "13/10/2026 13:37 | Đã tìm đủ 8 cặp | 6 cặp | Hạng 1 | Xếp hạng +3 | +50 xu";
        HistoryPanel.Entry loss = HistoryPanel.Entry.parse(recent);
        if (loss.pairs() != 1 || loss.rating() != -1 || loss.coins() != 10 || !loss.result().equals("Bỏ cuộc")) {
            throw new AssertionError("Incorrect backend history parsing");
        }
        HistoryPanel.Entry win = HistoryPanel.Entry.parse(winner);
        if (win.rating() != 3 || win.coins() != 50 || win.pairs() != 6) throw new AssertionError("Winner parsing");
        HistoryPanel.Entry old = HistoryPanel.Entry.parse(winner.substring(0, winner.lastIndexOf(" |")));
        if (old.coins() != null || old.pairs() != 6) throw new AssertionError("Invented legacy reward");
        HistoryPanel.Entry unknown = HistoryPanel.Entry.parse("Dữ liệu cũ chưa có cấu trúc");
        if (unknown.pairs() != null || !unknown.reason().equals(unknown.raw())) throw new AssertionError("Lost old history");
        SwingUtilities.invokeAndWait(() -> {
            HistoryPanel view = new HistoryPanel("player", 99, List.of(recent, winner), () -> {});
            if (!hasText(view, "7") || !hasText(view, "+60") || !hasText(view, "2")) throw new AssertionError("History totals");
            HistoryPanel partial = new HistoryPanel("player", 99, List.of(recent, old.raw()), () -> {});
            if (!hasText(partial, "—")) throw new AssertionError("Legacy aggregate must be unknown");
            HistoryPanel empty = new HistoryPanel("player", 99, List.of(), () -> {});
            if (!hasText(empty, "Chưa có trận đấu") || !hasText(empty, "+0")) throw new AssertionError("Empty history");
        });
        System.out.println("HISTORY_PANEL_OK backendParsing signedRating legacyUnknown totals empty");
    }
    public static void assertBackendEntry(String raw, int pairs, int rating, int coins) {
        HistoryPanel.Entry entry = HistoryPanel.Entry.parse(raw);
        if (!Integer.valueOf(pairs).equals(entry.pairs()) || !Integer.valueOf(rating).equals(entry.rating())
                || !Integer.valueOf(coins).equals(entry.coins())) {
            throw new AssertionError("History view does not match actual backend game result: " + raw);
        }
    }
    private static boolean hasText(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JLabel label && text.equals(label.getText())) return true;
            if (child instanceof Container nested && hasText(nested, text)) return true;
        }
        return false;
    }
}
