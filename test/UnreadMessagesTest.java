package memorygame.client;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class UnreadMessagesTest {
    public static void main(String[] args) throws Exception {
        UnreadMessages unread = new UnreadMessages();
        unread.receive("Alice", false, false);
        unread.receive("Alice", true, true);
        if (unread.total() != 0) throw new AssertionError("Outgoing or actively read message counted");
        unread.receive("Alice", true, false); unread.receive("ALICE", true, false); unread.receive("Bob", true, false);
        if (unread.total() != 3 || unread.count("alice") != 2) throw new AssertionError("Unread aggregation");
        unread.read("alice");
        if (unread.total() != 1 || unread.count("Bob") != 1) throw new AssertionError("Reading cleared another conversation");
        unread.clear(); if (unread.total() != 0) throw new AssertionError("Account reset");
        SwingUtilities.invokeAndWait(() -> {
            ChatNotificationButton button = new ChatNotificationButton(() -> {});
            button.setSize(46, 38); button.setUnread(1);
            BufferedImage image = new BufferedImage(46, 38, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics(); button.paint(g); g.dispose();
            int red = 0;
            for (int y = 0; y < 17; y++) for (int x = 25; x < 46; x++)
                if (image.getRGB(x, y) == memorygame.common.AppTheme.DANGER.getRGB()) red++;
            if (red < 20 || !button.getAccessibleContext().getAccessibleName().equals("1 tin nhắn chưa đọc"))
                throw new AssertionError("Badge not visible / accessible");
            button.setUnread(0);
            if (!button.getToolTipText().equals("Lịch sử trò chuyện")) throw new AssertionError("Badge reset");
        });
        System.out.println("UNREAD_MESSAGES_OK incomingOnly focusedRead perPeer total reset visibleBadge");
    }
}
