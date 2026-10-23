package memorygame.client;
import memorygame.common.Protocol.Message;
import memorygame.common.Protocol.Type;
import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class InvitationPanelTest {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Message message = Message.of(Type.INVITE_RECEIVED).with("from","An").with("roomName","Phòng của An")
                    .with("boardSize",6).with("capacity",3).with("playerCount",1).with("cardTheme","FOOD").with("expiresIn",10);
            AtomicLong clock = new AtomicLong(); AtomicInteger replies = new AtomicInteger(), closes = new AtomicInteger();
            InvitationPanel expired = new InvitationPanel(message, answer -> replies.incrementAndGet(), closes::incrementAndGet, clock::get);
            clock.set(9_999_000_000L); expired.tick();
            if(closes.get()!=0)throw new AssertionError("Closed early");
            clock.set(10_000_000_000L); expired.tick(); expired.respond(true);
            if(closes.get()!=1 || replies.get()!=0)throw new AssertionError("Expiry must close silently once");
            for(boolean choice:new boolean[]{true,false}) {
                InvitationPanel panel = new InvitationPanel(message, answer -> {
                    if(answer!=choice)throw new AssertionError("Wrong reply"); replies.incrementAndGet();
                }, closes::incrementAndGet, clock::get);
                panel.respond(choice); panel.respond(!choice); panel.tick();
            }
            if(replies.get()!=2 || closes.get()!=3)throw new AssertionError("Duplicate response");
        });
        System.out.println("INVITATION_PANEL_OK accept decline silentExpiry oneShot");
    }
}
