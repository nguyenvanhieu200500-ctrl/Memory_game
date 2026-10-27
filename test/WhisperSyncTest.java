package memorygame.test;

import memorygame.common.Protocol.*;
import memorygame.server.GameServer;
import java.nio.file.*;
import java.util.*;

public final class WhisperSyncTest {
    static Map<String, List<ChatEntry>> login(ProfileTest.Client c, String name, boolean register) throws Exception {
        if (register) {
            c.send(Message.of(Type.REGISTER).with("username", name).with("password", "secret123"));
            c.await(Type.REGISTER_OK);
        }
        c.send(Message.of(Type.LOGIN).with("username", name).with("password", "secret123"));
        c.await(Type.LOGIN_OK);
        return c.await(Type.WHISPER_HISTORY).value("conversations");
    }
    public static void main(String[] args) throws Exception {
        Path data = Files.createTempDirectory("whisper-sync-");
        GameServer server = new GameServer(ProfileTest.PORT, data);
        Thread thread = ProfileTest.start(server);
        try (var a = new ProfileTest.Client(); var b = new ProfileTest.Client(); var outsider = new ProfileTest.Client()) {
            a.send(Message.of(Type.WHISPER_SEND).with("username", "sync_b").with("text", "forbidden"));
            a.await(Type.ERROR);
            login(a, "sync_a", true); login(b, "sync_b", true);
            for (int i = 0; i < 103; i++) {
                a.send(Message.of(Type.WHISPER_SEND).with("username", "sync_b").with("text", "Xin chào 🌟 " + i));
                ChatEntry sent = a.await(Type.WHISPER_MESSAGE).value("entry");
                if (!sent.equals(b.await(Type.WHISPER_MESSAGE).value("entry"))) throw new AssertionError("delivery");
            }
            if (!login(outsider, "sync_c", true).isEmpty()) throw new AssertionError("privacy");
        } finally { server.close(); thread.join(2000); }
        server = new GameServer(ProfileTest.PORT, data); thread = ProfileTest.start(server);
        try (var a = new ProfileTest.Client(); var b = new ProfileTest.Client(); var outsider = new ProfileTest.Client()) {
            var first = login(a, "sync_a", false).get("sync_b");
            var second = login(b, "sync_b", false).get("sync_a");
            if (first.size() != 100 || !first.equals(second) || !first.get(0).text().equals("Xin chào 🌟 3"))
                throw new AssertionError("restart/fresh-client/retention");
            if (!login(outsider, "sync_c", false).isEmpty()) throw new AssertionError("privacy after restart");
            long before=0;int count=0;
            do {
                a.send(Message.of(Type.WHISPER_PAGE_REQUEST).with("username","sync_b").with("before",before));
                Message page=a.await(Type.WHISPER_PAGE);
                count+=page.<List<ChatEntry>>value("messages").size();before=page.longValue("before");
                a.send(Message.of(Type.WHISPER_READ).with("username","sync_b").with("through",page.longValue("newest")));
                if(!page.bool("hasMore"))break;
            }while(count<200);
            if(count!=103)throw new AssertionError("Wire pagination");
            outsider.send(Message.of(Type.WHISPER_PAGE_REQUEST).with("username","sync_b").with("sender","sync_a"));
            if(!outsider.await(Type.WHISPER_PAGE).<List<ChatEntry>>value("messages").isEmpty())throw new AssertionError("Forged history owner");
        } finally { server.close(); thread.join(2000); }
        System.out.println("WHISPER_SYNC_OK delivery restart freshClient privacy retention unicode");
    }
}
