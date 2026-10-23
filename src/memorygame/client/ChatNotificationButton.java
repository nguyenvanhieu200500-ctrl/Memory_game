package memorygame.client;

import memorygame.common.AppTheme;
import javax.swing.*;
import java.awt.*;

/** Painted badge avoids text clipping in compact chat buttons. */
final class ChatNotificationButton extends JButton {
    private int unread;
    ChatNotificationButton(Runnable open) {
        setIcon(new ConversationsPanel.ChatIcon()); setMargin(new Insets(0, 0, 0, 0));
        setPreferredSize(new Dimension(46, 38)); setContentAreaFilled(false); setBorderPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); addActionListener(e -> open.run()); setUnread(0);
    }
    void setUnread(int count) {
        unread = Math.max(0, count);
        String description = unread == 0 ? "Lịch sử trò chuyện" : unread + " tin nhắn chưa đọc";
        setToolTipText(description); getAccessibleContext().setAccessibleName(description); repaint();
    }
    @Override protected void paintComponent(Graphics g) {
        Graphics2D p = (Graphics2D) g.create();
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        p.setColor(AppTheme.BORDER); p.drawRoundRect(1, 3, getWidth()-4, getHeight()-6, 12, 12);
        p.dispose(); super.paintComponent(g);
        if (unread == 0) return;
        p = (Graphics2D) g.create();
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        String text = unread > 99 ? "99+" : String.valueOf(unread);
        p.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        int width = Math.max(17, p.getFontMetrics().stringWidth(text) + 7), x = getWidth()-width-1;
        p.setColor(AppTheme.DANGER); p.fillRoundRect(x, 0, width, 17, 17, 17);
        p.setColor(Color.WHITE); p.drawString(text, x+(width-p.getFontMetrics().stringWidth(text))/2, 12); p.dispose();
    }
}
