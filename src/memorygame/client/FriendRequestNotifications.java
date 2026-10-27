package memorygame.client;

import memorygame.common.Protocol.FriendInvitation;
import java.util.*;

/** Tracks unread notification badges independently from still-pending invitations. */
final class FriendRequestNotifications {
    private final Set<String> unreadIds=new HashSet<>();
    private boolean initialSync=true;

    void receive(FriendInvitation invitation,boolean newlyAdded) {
        if(newlyAdded) unreadIds.add(invitation.requestId());
    }
    void sync(Collection<FriendInvitation> invitations,Set<String> previousIds) {
        Set<String> current=new HashSet<>();
        for(FriendInvitation invitation:invitations) {
            String id=invitation.requestId(); current.add(id);
            if(initialSync || !previousIds.contains(id)) unreadIds.add(id);
        }
        unreadIds.retainAll(current);
        initialSync=false;
    }
    void markSeen() { unreadIds.clear(); }
    void reset() { unreadIds.clear(); initialSync=true; }
    int unreadCount() { return unreadIds.size(); }
}
