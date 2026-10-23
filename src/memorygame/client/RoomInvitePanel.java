package memorygame.client;

import memorygame.common.AppTheme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/** Friends and private conversations inside a waiting room. */
final class RoomInvitePanel extends JPanel {
    private final DefaultListModel<String> model = new DefaultListModel<>();
    private final JList<String> list = new JList<>(model);
    private final ChatNotificationButton chats;
    private final JLabel count = new JLabel();
    private final JLabel hint = new JLabel();
    private final JPanel content = new JPanel(new CardLayout());
    private final Set<String> friends = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Consumer<String> sendInvite;
    private final Consumer<String> whisper;
    private List<Map<String, Object>> players = List.of();
    private Set<String> members = Set.of();
    private String self = "";
    private boolean allowed;
    private String unavailable = "Chưa ở phòng chờ.";

    RoomInvitePanel(Consumer<String> sendInvite, Consumer<String> whisper, Runnable history) {
        super(new BorderLayout(0, 12));
        this.sendInvite = sendInvite; this.whisper = whisper;
        setBackground(AppTheme.SURFACE);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AppTheme.BORDER), new EmptyBorder(16, 16, 16, 16)));
        setPreferredSize(new Dimension(315, 0));
        JPanel heading = new JPanel(new BorderLayout(8, 6)); heading.setOpaque(false);
        JLabel title = new JLabel("Bạn bè"); title.setForeground(AppTheme.TEXT);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
        chats = new ChatNotificationButton(history);
        count.setForeground(AppTheme.MUTED); count.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        heading.add(title); heading.add(chats, BorderLayout.EAST); heading.add(count, BorderLayout.SOUTH); add(heading, BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); list.setFixedCellHeight(64);
        list.setBackground(AppTheme.SURFACE); list.setForeground(AppTheme.TEXT);
        list.setCellRenderer((l, name, index, selected, focus) -> {
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setBorder(new EmptyBorder(8, 10, 8, 6));
            row.setBackground(selected ? AppTheme.SELECTED : index % 2 == 0 ? AppTheme.INSET : AppTheme.SURFACE);
            JPanel info = new JPanel(new GridLayout(2, 1, 0, 3)); info.setOpaque(false);
            JLabel username = text(name, AppTheme.TEXT); username.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            info.add(username); info.add(text("● " + statusLabel(name), "OFFLINE".equals(status(name)) ? AppTheme.MUTED : AppTheme.SECONDARY));
            row.add(info);
            JButton plus = new JButton("");
            plus.setOpaque(false); plus.setContentAreaFilled(false);
            plus.setBorderPainted(false); plus.setFocusPainted(false);
            plus.setBorder(BorderFactory.createEmptyBorder());
            plus.setMargin(new Insets(0, 0, 0, 0));
            plus.setIcon(new Icon() {
                public int getIconWidth() { return 16; }
                public int getIconHeight() { return 16; }
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D p = (Graphics2D) g.create();
                    p.setColor(AppTheme.PRIMARY); p.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    p.drawLine(x+2, y+8, x+14, y+8); p.drawLine(x+8, y+2, x+8, y+14); p.dispose();
                }
            }); plus.setPreferredSize(new Dimension(38, 36));
            plus.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24)); plus.setEnabled(canInvite(name));
            plus.getAccessibleContext().setAccessibleName("Mời " + name + " vào phòng"); row.add(plus, BorderLayout.EAST);
            return row;
        });
        list.getAccessibleContext().setAccessibleName("Danh sách bạn bè trong phòng chờ");
        list.setToolTipText("Bấm tên để thì thầm · Bấm + để mời vào phòng");
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int index = list.locationToIndex(e.getPoint());
                if (index < 0) return;
                Rectangle bounds = list.getCellBounds(index, index);
                if (!bounds.contains(e.getPoint())) return;
                list.setSelectedIndex(index);
                String name = model.get(index);
                if (e.getX() >= bounds.x + bounds.width - 48) invite(name);
                else menuFor(name).show(list, bounds.x + 10, bounds.y + bounds.height);
            }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "whisperMenu");
        list.getActionMap().put("whisperMenu", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                int index = list.getSelectedIndex(); if (index < 0) return;
                Rectangle bounds = list.getCellBounds(index, index);
                menuFor(model.get(index)).show(list, bounds.x + 10, bounds.y + bounds.height);
            }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke("ADD"), "invite");
        list.getActionMap().put("invite", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { invite(list.getSelectedValue()); }
        });
        JScrollPane scroll = new JScrollPane(list); scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(AppTheme.SURFACE);
        JLabel empty = new JLabel("Chưa có bạn bè. Hãy kết bạn ở sảnh.", SwingConstants.CENTER); empty.setForeground(AppTheme.MUTED);
        content.setOpaque(false); content.add(scroll, "players"); content.add(empty, "empty"); add(content);
        hint.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12)); hint.setForeground(AppTheme.MUTED); add(hint, BorderLayout.SOUTH);
        rebuild();
    }
    private static JLabel text(String value, Color color) {
        JLabel label = new JLabel(); label.putClientProperty("html.disable", true); label.setText(value); label.setForeground(color); return label;
    }
    JPopupMenu menuFor(String name) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem item = new JMenuItem("Thì thầm"); item.setForeground(AppTheme.PRIMARY);
        item.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14)); item.setBorder(new EmptyBorder(10, 16, 10, 32));
        item.addActionListener(e -> whisper.accept(name)); menu.add(item); return menu;
    }
    private String status(String name) {
        return players.stream().filter(p -> name.equalsIgnoreCase(String.valueOf(p.get("username"))))
                .map(p -> String.valueOf(p.get("status"))).findFirst().orElse("OFFLINE");
    }
    private String statusLabel(String name) {
        if (members.stream().anyMatch(member -> member.equalsIgnoreCase(name))) return "Trong phòng này";
        return switch (status(name)) {
            case "IDLE" -> "Rảnh";
            case "QUEUE" -> "Đang tìm trận";
            case "ROOM" -> "Trong phòng";
            case "PLAYING" -> "Đang chơi";
            default -> "Ngoại tuyến";
        };
    }
    private boolean canInvite(String name) {
        return name != null && allowed && friends.contains(name) && !name.equalsIgnoreCase(self) && "IDLE".equals(status(name))
                && members.stream().noneMatch(member -> member.equalsIgnoreCase(name));
    }
    private void invite(String name) { if (canInvite(name)) sendInvite.accept(name); }
    void setUnreadMessages(int count) { chats.setUnread(count); }
    void updateFriends(Set<String> names) { friends.clear(); friends.addAll(names); rebuild(); }
    void updatePlayers(List<Map<String, Object>> players, String self) {
        this.players = List.copyOf(players); this.self = self; rebuild();
    }
    void setRoomContext(boolean allowed, String reason, Set<String> members) {
        this.allowed = allowed; this.unavailable = reason; this.members = Set.copyOf(members); rebuild();
    }
    private void rebuild() {
        String selected = list.getSelectedValue(); model.clear();
        friends.stream().filter(name -> !name.equalsIgnoreCase(self))
                .sorted(java.util.Comparator.comparing((String name) -> "OFFLINE".equals(status(name)))
                        .thenComparing(String.CASE_INSENSITIVE_ORDER)).forEach(model::addElement);
        if (selected != null && model.contains(selected)) list.setSelectedValue(selected, true);
        long online = friends.stream().filter(name -> !name.equalsIgnoreCase(self) && !"OFFLINE".equals(status(name))).count();
        count.setText(online + " online · " + model.size() + " bạn bè");
        ((CardLayout) content.getLayout()).show(content, model.isEmpty() ? "empty" : "players");
        hint.setText(!allowed ? unavailable : "Bấm tên để thì thầm · + để mời vào phòng"); hint.setToolTipText(hint.getText());
        list.repaint();
    }
}
