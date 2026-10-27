package memorygame.server;
public final class PasswordHashesTest {
    public static void main(String[] args) {
        String a=PasswordHashes.hash("Mật khẩu 🔐"),b=PasswordHashes.hash("Mật khẩu 🔐");
        if(a.equals(b)||!PasswordHashes.verify("Mật khẩu 🔐",a)||PasswordHashes.verify("wrong",a))throw new AssertionError("KDF salt/verification");
        if(PasswordHashes.verify("x","pbkdf2-sha256$9999999999$bad$bad")||PasswordHashes.verify("x","garbage"))throw new AssertionError("Malformed hash");
        if(!PasswordHashes.verify("password","5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8"))throw new AssertionError("Legacy verification");
        System.out.println("PASSWORD_HASHES_OK randomSalt unicode invalidFormats legacy");
    }
}
