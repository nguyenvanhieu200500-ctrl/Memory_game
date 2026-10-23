package memorygame.client;
import memorygame.common.Protocol.Message;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

public final class CreateRoomPanelTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicReference<Message> sent = new AtomicReference<>();
            CreateRoomPanel panel = new CreateRoomPanel("Minh Anh", CardTheme.FRUIT, 4, () -> {}, sent::set);
            panel.setSize(panel.getPreferredSize()); layout(panel);
            BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics(); panel.printAll(g); g.dispose();
            if (args.length > 0) try { javax.imageio.ImageIO.write(image, "png", new java.io.File(args[0])); } catch (Exception e) { throw new RuntimeException(e); }
            JTextField name = find(panel, JTextField.class); name.setText(" ");
            button(panel, "Xác nhận tạo phòng").doClick();
            if (sent.get() != null) throw new AssertionError("Empty name accepted");
            name.setText("Phòng thử nghiệm"); button(panel, "3 người").doClick(); button(panel, "Khó · 8×8").doClick();
            find(panel, JComboBox.class).setSelectedItem(CardTheme.ANIMALS);
            button(panel, "Xác nhận tạo phòng").doClick();
            Message message = sent.get();
            if (message == null || message.integer("capacity") != 3 || message.integer("boardSize") != 8
                    || !message.string("cardTheme").equals("ANIMALS") || !message.string("roomName").equals("Phòng thử nghiệm")) throw new AssertionError("Form mapping failed");
            AtomicReference<Message> invitation = new AtomicReference<>();
            CreateRoomPanel invitePanel = new CreateRoomPanel("Host", CardTheme.VEHICLES, 6, "Guest", () -> {}, invitation::set);
            button(invitePanel, "4 người").doClick();
            button(invitePanel, "Tạo phòng và gửi lời mời").doClick();
            Message invite = invitation.get();
            if (invite == null || !"Guest".equals(invite.string("inviteUsername")) || invite.integer("capacity") != 4
                    || invite.integer("boardSize") != 6 || !"VEHICLES".equals(invite.string("cardTheme")))
                throw new AssertionError("Invitation setup payload");
            AtomicReference<Message> cancelled = new AtomicReference<>();
            CreateRoomPanel cancelledPanel = new CreateRoomPanel("Host", CardTheme.FRUIT, 4, "Guest", () -> {}, cancelled::set);
            button(cancelledPanel, "Hủy").doClick();
            if (cancelled.get() != null) throw new AssertionError("Cancel sent invitation");
        });
        System.out.println("CREATE_ROOM_PANEL_OK validation options payload");
    }
    private static void layout(Container c) { c.doLayout(); for(Component child:c.getComponents()) if(child instanceof Container nested) layout(nested); }
    private static AbstractButton button(Container c, String text) {
        for(Component child:c.getComponents()) {
            if(child instanceof AbstractButton b && b.getText().equals(text)) return b;
            if(child instanceof Container nested) { AbstractButton found = button(nested,text); if(found!=null)return found; }
        } return null;
    }
    private static <T> T find(Container c, Class<T> type) {
        for(Component child:c.getComponents()) {
            if(type.isInstance(child)) return type.cast(child);
            if(child instanceof Container nested) { T found=find(nested,type); if(found!=null)return found; }
        } return null;
    }
}
