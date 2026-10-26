package memorygame.test;

import memorygame.common.*;
import memorygame.common.Protocol.*;
import memorygame.server.GameServer;
import java.io.*;
import java.net.*;
import java.nio.file.*;

/** Isolated real-protocol checks with file storage; never touches production accounts. */
public final class ProfileTest {
    static final int PORT = 12481;
    static final class Client implements AutoCloseable {
        final Socket socket; final ObjectOutputStream out; final ObjectInputStream in;
        Client() throws Exception { socket = new Socket("127.0.0.1", PORT); socket.setSoTimeout(4000); out = new ObjectOutputStream(socket.getOutputStream()); out.flush(); in = new ObjectInputStream(socket.getInputStream()); }
        void send(Message m) throws Exception { out.reset(); out.writeObject(m); out.flush(); }
        Message await(Type type) throws Exception { for (int i=0;i<100;i++) {Message m=(Message)in.readObject();if(m.type()==type)return m;}throw new AssertionError("Missing "+type); }
        void login(String password) throws Exception { send(Message.of(Type.LOGIN).with("username","profile_test").with("password",password)); }
        public void close() throws Exception {socket.close();}
    }
    static Thread start(GameServer server) throws Exception {
        Thread t=new Thread(()->{try{server.start();}catch(Exception e){throw new RuntimeException(e);}});t.setDaemon(true);t.start();
        for(int i=0;i<40;i++){try(var s=new Socket("127.0.0.1",PORT)){return t;}catch(IOException e){Thread.sleep(50);}}
        throw new AssertionError("Server did not start");
    }
    static void success(Message m,boolean expected){if(m.bool("success")!=expected)throw new AssertionError(m.string("message"));}
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("profile-test-");
        var image=new java.awt.image.BufferedImage(40,40,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();g.setColor(java.awt.Color.RED);g.fillRect(0,0,40,40);g.dispose();
        Path png=dir.resolve("avatar.png");javax.imageio.ImageIO.write(image,"png",png.toFile());
        String avatar=AvatarImages.fromFile(png.toFile());
        GameServer server=new GameServer(PORT,dir);Thread t=start(server);
        try(Client c=new Client()) {
            c.send(Message.of(Type.PROFILE_REQUEST));c.await(Type.ERROR);
            c.send(Message.of(Type.REGISTER).with("username","profile_test").with("password","old123"));c.await(Type.REGISTER_OK);
            c.login("old123");c.await(Type.LOGIN_OK);
            c.send(Message.of(Type.PASSWORD_CHANGE).with("currentPassword","wrong").with("newPassword","new123"));success(c.await(Type.PROFILE_RESULT),false);
            c.send(Message.of(Type.PASSWORD_CHANGE).with("currentPassword","old123").with("newPassword","abc"));success(c.await(Type.PROFILE_RESULT),false);
            c.send(Message.of(Type.AVATAR_UPDATE).with("avatar","invalid"));success(c.await(Type.PROFILE_RESULT),false);
            c.send(Message.of(Type.AVATAR_UPDATE).with("avatar",avatar));success(c.await(Type.PROFILE_RESULT),true);
            Message profile=c.await(Type.PROFILE_UPDATE);if(!profile.string("avatar").equals(avatar))throw new AssertionError("Avatar update");
            c.send(Message.of(Type.PASSWORD_CHANGE).with("currentPassword","old123").with("newPassword","new123"));success(c.await(Type.PROFILE_RESULT),true);
        } finally {server.close();t.join(2000);}
        server=new GameServer(PORT,dir);t=start(server);
        try(Client c=new Client()) {
            c.login("old123");c.await(Type.ERROR);
            c.login("new123");Message login=c.await(Type.LOGIN_OK);
            if(!avatar.equals(login.string("avatar")))throw new AssertionError("Avatar not persisted");
            c.send(Message.of(Type.PROFILE_REQUEST));Message profile=c.await(Type.PROFILE_UPDATE);
            if(profile.integer("rating")!=0||profile.integer("games")!=0)throw new AssertionError("Stats altered");
            c.send(Message.of(Type.AVATAR_UPDATE).with("avatar",""));success(c.await(Type.PROFILE_RESULT),true);
            if(!c.await(Type.PROFILE_UPDATE).string("avatar").isEmpty())throw new AssertionError("Reset failed");
        } finally {server.close();t.join(2000);}
        System.out.println("PROFILE_OK authentication validation passwordChange avatarUpdate persistence reset statsUnchanged");
    }
}
