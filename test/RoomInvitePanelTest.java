package memorygame.client;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

public final class RoomInvitePanelTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicReference<String> invited = new AtomicReference<>(), whispered = new AtomicReference<>();
            AtomicInteger history = new AtomicInteger();
            RoomInvitePanel panel = new RoomInvitePanel(invited::set, whispered::set, history::incrementAndGet);
            panel.updatePlayers(List.of(row("self", "IDLE"), row("member", "IDLE"), row("free", "IDLE"),
                    row("busy", "PLAYING"), row("stranger", "IDLE")), "self");
            panel.setRoomContext(true, "", Set.of("self", "member"));
            JList<?> list = find(panel, JList.class);
            if (list.getModel().getSize() != 0) throw new AssertionError("Non-friends visible");
            panel.updateFriends(Set.of("self", "member", "FREE", "busy", "offline"));
            if (list.getModel().getSize() != 4) throw new AssertionError("Friends missing or self visible");
            @SuppressWarnings("unchecked") JList<String> renderedList = (JList<String>) list;
            Component rendered = renderedList.getCellRenderer().getListCellRendererComponent(renderedList, "FREE", 0, false, false);
            JButton plus = find((Container) rendered, JButton.class);
            if (plus == null || !plus.getText().isEmpty() || plus.getIcon() == null || plus.getMargin().left != 0)
                throw new AssertionError("Plus must use an unclipped icon, not ellipsized text");
            list.setSelectedValue("FREE", true);
            list.getActionMap().get("invite").actionPerformed(new ActionEvent(list, 0, "invite"));
            if (!"FREE".equals(invited.get())) throw new AssertionError("Invite recipient / case insensitive presence");
            invited.set(null); panel.setRoomContext(false, "Phòng đã đủ người.", Set.of("self", "member"));
            list.getActionMap().get("invite").actionPerformed(new ActionEvent(list, 0, "invite"));
            if (invited.get() != null) throw new AssertionError("Full room allowed invitation");
            panel.setRoomContext(true, "", Set.of("self", "member"));
            for (String name : List.of("member", "busy", "offline")) {
                list.setSelectedValue(name, true);
                list.getActionMap().get("invite").actionPerformed(new ActionEvent(list, 0, "invite"));
                if (invited.get() != null) throw new AssertionError("Invalid invitation: " + name);
                JPopupMenu menu = panel.menuFor(name);
                if (menu.getComponentCount() != 1 || !(menu.getComponent(0) instanceof JMenuItem item)
                        || !item.getText().equals("Thì thầm")) throw new AssertionError("Menu must only contain whisper");
                ((JMenuItem) menu.getComponent(0)).doClick();
                if (!name.equals(whispered.get())) throw new AssertionError("Wrong whisper peer");
            }
            // A plus click invites only the row under the pointer; blank space does nothing.
            list.setSize(280, 400); list.setSelectedValue("FREE", true);
            Rectangle bounds = list.getCellBounds(list.getSelectedIndex(), list.getSelectedIndex());
            list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_CLICKED, 0, 0, 260, bounds.y + 20, 1, false, MouseEvent.BUTTON1));
            if (!"FREE".equals(invited.get())) throw new AssertionError("Plus click missed recipient");
            invited.set(null);
            list.dispatchEvent(new MouseEvent(list, MouseEvent.MOUSE_CLICKED, 0, 0, 260, 390, 1, false, MouseEvent.BUTTON1));
            if (invited.get() != null) throw new AssertionError("Blank space sent invitation");
            panel.updatePlayers(List.of(row("free", "ROOM")), "self");
            list.setSelectedValue("FREE", true);
            list.getActionMap().get("invite").actionPerformed(new ActionEvent(list, 0, "invite"));
            if (invited.get() != null) throw new AssertionError("Stale presence allowed invitation");
            find(panel, JButton.class).doClick();
            if (history.get() != 1) throw new AssertionError("History action");
            panel.updateFriends(Set.of());
            if (list.getModel().getSize() != 0) throw new AssertionError("Account reset leaked friends");
        });
        System.out.println("ROOM_INVITE_PANEL_OK friendsOnly presence capacity membership whisperOnly plusClick keyboard history reset");
    }
    private static Map<String,Object> row(String name,String status) { return Map.of("username",name,"status",status); }
    private static <T> T find(Container parent,Class<T> type) {
        for (Component child : parent.getComponents()) {
            if(type.isInstance(child)) return type.cast(child);
            if(child instanceof Container nested) { T found=find(nested,type); if(found!=null)return found; }
        } return null;
    }
}
