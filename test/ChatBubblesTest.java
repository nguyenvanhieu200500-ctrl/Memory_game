package memorygame.test;

import memorygame.common.ChatPanel;
import memorygame.common.Protocol.ChatEntry;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class ChatBubblesTest {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container nested) layout(nested); }
    static <T> T find(Container c, Class<T> type) {
        for (Component child : c.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) { T found = find(nested, type); if (found != null) return found; }
        }
        return null;
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChatPanel panel = new ChatPanel(text -> {});
            Icon avatar = new ImageIcon(new BufferedImage(36, 36, BufferedImage.TYPE_INT_ARGB));
            panel.useMessageBubbles("Me", name -> avatar);
            panel.setMessages(List.of(new ChatEntry("Peer", "<html>Xin chào", 0), new ChatEntry("ME", "x".repeat(300), 1)));
            for (int width : new int[]{460, 340, 600}) {
                panel.setSize(width, 360); layout(panel); panel.refreshBubbles(); layout(panel);
                JScrollPane scroll = find(panel, JScrollPane.class);
                JPanel rows = (JPanel) scroll.getViewport().getView();
                check(rows.getComponentCount() == 2, "message count");
                JPanel incoming = (JPanel) rows.getComponent(0), outgoing = (JPanel) rows.getComponent(1);
                check(((BorderLayout) incoming.getLayout()).getLayoutComponent(BorderLayout.WEST) != null, "incoming left");
                check(((BorderLayout) outgoing.getLayout()).getLayoutComponent(BorderLayout.EAST) != null, "own right case insensitive");
                check(find(incoming, JTextArea.class).getText().equals("<html>Xin chào"), "literal text");
                check(find(outgoing, JTextArea.class).getPreferredSize().height > 30, "long message wraps");
                JPanel group = (JPanel) ((BorderLayout) incoming.getLayout()).getLayoutComponent(BorderLayout.WEST);
                check(((JLabel) ((BorderLayout) group.getLayout()).getLayoutComponent(BorderLayout.WEST)).getIcon() == avatar, "sender avatar");
                check(incoming.getPreferredSize().width <= width, "fits window");
            }
            panel.setMessages(List.of());
            check(((Container) find(panel, JScrollPane.class).getViewport().getView()).getComponentCount() == 0, "clear");
        });
        System.out.println("CHAT_BUBBLES_OK alignment avatar wrapping resize literalText clear");
    }
}
