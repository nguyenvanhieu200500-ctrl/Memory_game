package memorygame.client;

import memorygame.common.Protocol.FriendInvitation;
import java.util.*;

public final class FriendRequestNotificationsTest {
    public static void main(String[] args) {
        FriendRequestNotifications notifications=new FriendRequestNotifications();
        FriendInvitation one=new FriendInvitation("one","alice","",1);
        notifications.sync(List.of(one),Set.of());
        check(notifications.unreadCount()==1,"initial pending invitations should notify");
        notifications.markSeen();check(notifications.unreadCount()==0,"opening the list should clear the badge");
        notifications.sync(List.of(one),Set.of("one"));
        check(notifications.unreadCount()==0,"sync must not restore a seen badge");
        FriendInvitation two=new FriendInvitation("two","bob","",2);
        notifications.receive(two,true);check(notifications.unreadCount()==1,"new invitation badge");
        notifications.sync(List.of(one,two),Set.of("one"));
        check(notifications.unreadCount()==1,"sync should preserve unread while retaining seen entries");
        notifications.sync(List.of(one),Set.of("one","two"));
        check(notifications.unreadCount()==0,"resolved invitation badge should clear");
        notifications.reset();notifications.sync(List.of(one),Set.of());
        check(notifications.unreadCount()==1,"new account sync should notify about existing pending requests");
        System.out.println("FRIEND_REQUEST_NOTIFICATIONS_OK clearOnOpen pendingRetained newArrival syncAndResolution");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
