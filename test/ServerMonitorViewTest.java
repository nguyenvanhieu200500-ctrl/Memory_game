package memorygame.server;

import memorygame.server.GameServer.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Headless view regression: data updates, selection stability and room removal. */
public final class ServerMonitorViewTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ServerMonitorFrame.Dashboard view = new ServerMonitorFrame.Dashboard();
            view.setSize(1080, 730);
            RoomSnapshot first = room("A12345", 4, 2, 0);
            RoomSnapshot second = room("B12345", 8, 4, 0);
            view.render(new MonitorSnapshot(true, 23456, 6, 0, List.of(first, second)));
            JList<?> list = findList(view);
            if (list == null || list.getModel().getSize() != 2) throw new AssertionError("Missing live room list");
            list.setSelectedIndex(1);
            requireText(view, "Phòng: B12345   •   Bàn: 8 × 8");
            requireText(view, "●  Server đang chạy  ·  Cổng 23456");
            RoomSnapshot updated = room("B12345", 8, 4, 7);
            view.render(new MonitorSnapshot(true, 23456, 6, 0, List.of(first, updated)));
            if (list.getSelectedIndex() != 1) throw new AssertionError("Refresh changed selected room");
            requireText(view, "7 / 32 cặp");
            if (!hasChat(view, "Xin chào B12345")) throw new AssertionError("Missing selected room chat");
            requireText(view, "7");
            paint(view);
            verifyBoardArtwork();
            // Inserting a room before the selected room must preserve identity, not index.
            view.render(new MonitorSnapshot(true, 23456, 6, 0,
                    List.of(room("000000", 6, 3, 0), first, updated)));
            if (list.getSelectedIndex() != 2) throw new AssertionError("Selection followed wrong room");
            view.render(new MonitorSnapshot(true, 23456, 2, 0, List.of(first)));
            requireText(view, "Phòng: A12345   •   Bàn: 4 × 4");
            paint(view);
            view.render(new MonitorSnapshot(false, 23456, 0, 0, List.of()));
            if (list.getModel().getSize() != 0) throw new AssertionError("Deleted rooms remain visible");
            requireText(view, "0 / 0 cặp");
            for (String removed : List.of("Chưa có phòng", "Tạo phòng từ client để bắt đầu",
                    "CHƯA CÓ TRẬN", "Chọn phòng để theo dõi")) {
                if (hasText(view, removed)) throw new AssertionError("Removed text remains visible: " + removed);
            }
            if (hasChat(view, "Xin chào")) throw new AssertionError("Deleted room chat remains visible");
            paint(view);
        });
        System.out.println("SERVER_MONITOR_VIEW_OK liveData selectedRoom updates removal emptyState");
    }

    private static void verifyBoardArtwork() {
        try {
            Class<?> type = Class.forName("memorygame.server.ServerMonitorFrame$Board");
            var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
            var roomField = type.getDeclaredField("room"); roomField.setAccessible(true);
            var bounds = type.getDeclaredMethod("cardBounds", int.class); bounds.setAccessible(true);
            java.util.Set<Integer> renderedThemes = new java.util.HashSet<>();
            for (String theme : List.of("FRUIT", "ANIMALS", "FOOD", "VEHICLES")) {
                for (int size : new int[]{4, 6, 8}) {
                    JPanel board = (JPanel) constructor.newInstance();
                    board.setSize(620, 480);
                    RoomSnapshot source = room("ART", size, 4, 2);
                    roomField.set(board, new RoomSnapshot(source.code(), size, false, source.status(),
                            source.players(), source.cards(), source.current(), 0, false, 2, 2, source.chat(), theme));
                    for (int i = 0; i < size * size; i++) {
                        Rectangle tile = (Rectangle) bounds.invoke(board, i);
                        if (tile.width != tile.height || tile.width <= 0)
                            throw new AssertionError("Observer card must be square");
                    }
                    BufferedImage image = new BufferedImage(620, 480, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = image.createGraphics(); board.paint(g); g.dispose();
                    if (size == 4) renderedThemes.add(java.util.Arrays.hashCode(
                            image.getRGB(0, 0, 620, 480, null, 0, 620)));
                }
            }
            if (renderedThemes.size() != 4) throw new AssertionError("Observer did not render all four themes");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static RoomSnapshot room(String code, int size, int count, int pairs) {
        List<PlayerSnapshot> players = new ArrayList<>();
        for (int i = 0; i < count; i++) players.add(new PlayerSnapshot("player_" + i,
                i == 0 ? pairs : 0, true, true, false));
        List<CardSnapshot> cards = new ArrayList<>();
        for (int i = 0; i < size * size; i++) cards.add(i < pairs * 2
                ? new CardSnapshot(i / 2 + 1, 2, "player_0") : new CardSnapshot(-1, 0, null));
        return new RoomSnapshot(code, size, false, "Đang chơi", List.copyOf(players), List.copyOf(cards),
                "player_0", System.currentTimeMillis() + 15000, false, pairs, pairs, List.of(new memorygame.common.Protocol.ChatEntry("player_0", "Xin chào " + code, 1000)), "FRUIT");
    }

    private static JList<?> findList(Container root) {
        for (Component component : root.getComponents()) {
            if (component instanceof JList<?> list) return list;
            if (component instanceof Container child) {
                JList<?> list = findList(child);
                if (list != null) return list;
            }
        }
        return null;
    }

    private static boolean hasChat(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JTextArea area && area.getText().contains(text)) return true;
            if (component instanceof Container child && hasChat(child, text)) return true;
        }
        return false;
    }

    private static boolean hasText(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JLabel label && text.equals(label.getText())) return true;
            if (component instanceof Container child && hasText(child, text)) return true;
        }
        return false;
    }

    private static void requireText(Container root, String text) {
        if (!hasText(root, text)) throw new AssertionError("View did not display backend value: " + text);
    }

    private static void layout(Container root) {
        root.doLayout();
        for (Component component : root.getComponents()) if (component instanceof Container child) layout(child);
    }

    private static void paint(JPanel view) {
        layout(view);
        layout(view);
        BufferedImage image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        view.printAll(graphics);
        graphics.dispose();
    }
}
