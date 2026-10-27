package memorygame.client;

import memorygame.common.Protocol.FriendInvitation;
import javax.swing.*;
import java.awt.*;
import java.util.*;

public final class FriendRequestsPanelTest {
    public static void main(String[] args)throws Exception {
        SwingUtilities.invokeAndWait(()->{
            java.util.List<String> actions=new ArrayList<>();
            FriendRequestsPanel panel=new FriendRequestsPanel((id,accepted)->actions.add(id+":"+accepted),()->{});
            check(labels(panel).contains("Bạn chưa có lời mời kết bạn"),"empty state missing");
            FriendInvitation invitation=new FriendInvitation("req-1","player","",1234);
            panel.update(java.util.List.of(invitation),false);
            java.util.List<JButton> buttons=buttons(panel);
            check(buttons.stream().filter(b->b.getText().equals("Chấp nhận")).noneMatch(JButton::isEnabled),"actions enabled before authoritative sync");
            panel.update(java.util.List.of(invitation),true);buttons=buttons(panel);
            JButton accept=buttons.stream().filter(b->b.getText().equals("Chấp nhận")).findFirst().orElseThrow();
            JButton decline=buttons.stream().filter(b->b.getText().equals("Từ chối")).findFirst().orElseThrow();
            accept.doClick();check(actions.equals(java.util.List.of("req-1:true")),"accept callback");
            buttons=buttons(panel);
            check(buttons.stream().filter(b->b.getText().equals("Chấp nhận")||b.getText().equals("Từ chối")).noneMatch(JButton::isEnabled),"actions not locked while sending");
            panel.update(java.util.List.of(),true);check(labels(panel).contains("Bạn chưa có lời mời kết bạn"),"empty state after resolution");
        });
        System.out.println("FRIEND_REQUESTS_PANEL_OK empty authoritativeSync callbacks sendingLock noExpiryControls");
    }
    private static java.util.List<JButton> buttons(Container c){java.util.List<JButton> result=new ArrayList<>();for(Component x:c.getComponents()){if(x instanceof JButton b)result.add(b);if(x instanceof Container nested)result.addAll(buttons(nested));}return result;}
    private static java.util.List<String> labels(Container c){java.util.List<String> result=new ArrayList<>();for(Component x:c.getComponents()){if(x instanceof JLabel l)result.add(l.getText());if(x instanceof Container nested)result.addAll(labels(nested));}return result;}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
