package memorygame.client;

import memorygame.common.AppTheme;
import memorygame.common.AvatarImages;
import memorygame.common.Protocol.FriendInvitation;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;

/** Lobby list for persistent friend requests. */
final class FriendRequestsPanel extends JPanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
    private final JPanel rows = new JPanel();
    private final JLabel empty = new JLabel("Bạn chưa có lời mời kết bạn", SwingConstants.CENTER);
    private final JLabel status = new JLabel(" ");
    private final JButton sync = new JButton("Đồng bộ lại");
    private final BiConsumer<String, Boolean> reply;
    private final Runnable synchronize;
    private final Set<String> inFlight = new HashSet<>();
    private List<FriendInvitation> invitations = List.of();
    private boolean ready;

    FriendRequestsPanel(BiConsumer<String, Boolean> reply, Runnable synchronize) {
        super(new BorderLayout(0, 12)); this.reply=reply; this.synchronize=synchronize;
        setBackground(AppTheme.SURFACE); setBorder(new EmptyBorder(18,18,16,18));
        JLabel title=new JLabel("Lời mời kết bạn"); title.setForeground(AppTheme.PRIMARY); title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,21));
        JPanel top=new JPanel(new BorderLayout()); top.setOpaque(false); top.add(title); top.add(sync,BorderLayout.EAST); add(top,BorderLayout.NORTH);
        rows.setOpaque(false); rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));
        JScrollPane scroll=new JScrollPane(rows); scroll.setBorder(BorderFactory.createLineBorder(AppTheme.BORDER)); scroll.getViewport().setBackground(AppTheme.SURFACE); add(scroll);
        status.setForeground(AppTheme.MUTED); add(status,BorderLayout.SOUTH);
        sync.addActionListener(e -> { ready=false; render(); status.setText("Đang đồng bộ lời mời..."); synchronize.run(); });
        render();
    }
    void update(List<FriendInvitation> values, boolean synchronizedState) {
        Map<String,FriendInvitation> latest=new LinkedHashMap<>();
        for(FriendInvitation invitation:values) latest.put(invitation.requestId(),invitation);
        inFlight.retainAll(latest.keySet()); invitations=List.copyOf(latest.values());
        if(synchronizedState) { ready=true; status.setText(" "); }
        render();
    }
    void markSending(String id) { inFlight.add(id); render(); }
    void synchronizing() { ready=false; render(); status.setForeground(AppTheme.MUTED); status.setText("Đang đồng bộ lời mời..."); }
    void failed(String message) { ready=false; status.setForeground(AppTheme.DANGER); status.setText(message); render(); }
    private void render() {
        rows.removeAll();
        if(invitations.isEmpty()) rows.add(empty);
        for(FriendInvitation invitation:invitations) rows.add(row(invitation));
        sync.setEnabled(true);
        rows.revalidate(); rows.repaint();
    }
    private JPanel row(FriendInvitation invitation) {
        JPanel card=new JPanel(new BorderLayout(12,5)); card.setOpaque(true); card.setBackground(AppTheme.INSET);
        card.setBorder(new EmptyBorder(11,12,11,12)); card.setMaximumSize(new Dimension(Integer.MAX_VALUE,84));
        JLabel avatar=new JLabel(avatar(invitation.avatar()),SwingConstants.CENTER); avatar.setPreferredSize(new Dimension(48,48));
        JPanel identity=new JPanel(new BorderLayout(0,5)); identity.setOpaque(false);
        JLabel name=new JLabel(invitation.from()); name.setForeground(AppTheme.TEXT); name.setFont(new Font(Font.SANS_SERIF,Font.BOLD,15)); identity.add(name,BorderLayout.NORTH);
        JLabel time=new JLabel(invitation.sentAt()>0?"Đã gửi "+TIME.format(Instant.ofEpochMilli(invitation.sentAt())):"Thời gian không có sẵn"); time.setForeground(AppTheme.MUTED); time.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12)); identity.add(time);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,4)); actions.setOpaque(false);
        JButton accept=new JButton("Chấp nhận"), decline=new JButton("Từ chối");
        boolean enabled=ready&&!inFlight.contains(invitation.requestId()); accept.setEnabled(enabled); decline.setEnabled(enabled);
        accept.addActionListener(e -> {markSending(invitation.requestId()); reply.accept(invitation.requestId(),true);});
        decline.addActionListener(e -> {markSending(invitation.requestId()); reply.accept(invitation.requestId(),false);});
        actions.add(accept); actions.add(decline); card.add(avatar,BorderLayout.WEST); card.add(identity); card.add(actions,BorderLayout.EAST); return card;
    }
    private static Icon avatar(String encoded) {
        try {
            var image=AvatarImages.decode(encoded);
            if(image!=null) return new ImageIcon(image.getScaledInstance(44,44,Image.SCALE_SMOOTH));
        } catch(Exception ignored) { }
        return new Icon() {
            public int getIconWidth(){return 44;} public int getIconHeight(){return 44;}
            public void paintIcon(Component c,Graphics g,int x,int y){Graphics2D p=(Graphics2D)g.create();p.setColor(AppTheme.SELECTED);p.fillOval(x,y,44,44);p.setColor(AppTheme.PRIMARY);p.fillOval(x+14,y+8,16,16);p.fillRoundRect(x+8,y+26,28,13,12,12);p.dispose();}
        };
    }
}
