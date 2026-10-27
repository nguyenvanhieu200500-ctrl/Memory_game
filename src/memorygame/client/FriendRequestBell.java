package memorygame.client;

import memorygame.common.AppTheme;
import javax.swing.*;
import java.awt.*;

/** Lobby notification bell with a persistent pending-count badge. */
final class FriendRequestBell extends JButton {
    private int count;
    FriendRequestBell(Runnable open) {
        setText("Lời mời"); setPreferredSize(new Dimension(104, 38)); setMargin(new Insets(0, 0, 0, 0));
        setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12)); setForeground(AppTheme.PRIMARY);
        setBackground(AppTheme.SELECTED); setOpaque(true); setContentAreaFilled(true);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AppTheme.BORDER),
                BorderFactory.createEmptyBorder(0, 25, 0, 12)));
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Lời mời kết bạn"); getAccessibleContext().setAccessibleName("Lời mời kết bạn");
        addActionListener(e -> open.run());
    }
    void setCount(int value) {
        count = Math.max(0, value);
        String label=count==0?"Lời mời kết bạn":"Lời mời kết bạn: "+count+" đang chờ";
        setToolTipText(label); getAccessibleContext().setAccessibleName(label); repaint();
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D p = (Graphics2D) g.create();
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        p.setColor(AppTheme.PRIMARY); p.setStroke(new BasicStroke(2f));
        int x = 7, y = 5;
        p.drawArc(x+2,y,12,14,0,180); p.drawLine(x+2,y+7,x+1,y+19); p.drawLine(x+14,y+7,x+15,y+19);
        p.drawArc(x+1,y+14,14,10,180,180); p.fillOval(x+6,y+24,5,4); p.dispose();
        if (count == 0) return;
        p = (Graphics2D) g.create(); p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        String text = count > 99 ? "99+" : String.valueOf(count); p.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        int w=Math.max(17,p.getFontMetrics().stringWidth(text)+7), bx=getWidth()-w-1;
        p.setColor(AppTheme.DANGER); p.fillRoundRect(bx,0,w,17,17,17); p.setColor(Color.WHITE);
        p.drawString(text,bx+(w-p.getFontMetrics().stringWidth(text))/2,12); p.dispose();
    }
}
