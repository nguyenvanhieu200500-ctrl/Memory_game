package memorygame.server;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** JDK password KDF; versioned records allow gradual upgrades without password resets. */
final class PasswordHashes {
    static final int ITERATIONS=600_000;
    private static final SecureRandom RANDOM=new SecureRandom();
    static String hash(String password) {
        byte[] salt=new byte[16];RANDOM.nextBytes(salt);
        return "pbkdf2-sha256$"+ITERATIONS+"$"+Base64.getEncoder().encodeToString(salt)+"$"
            +Base64.getEncoder().encodeToString(derive(password,salt,ITERATIONS));
    }
    static boolean legacy(String encoded) { return encoded!=null && encoded.matches("[a-fA-F0-9]{64}"); }
    static boolean verify(String password,String encoded) {
        if(encoded==null)return false;
        try {
            if(legacy(encoded))return MessageDigest.isEqual(HexFormat.of().parseHex(encoded),
                MessageDigest.getInstance("SHA-256").digest(password.getBytes(StandardCharsets.UTF_8)));
            String[] fields=encoded.split("\\$",-1);
            if(fields.length!=4 || !fields[0].equals("pbkdf2-sha256"))return false;
            int iterations=Integer.parseInt(fields[1]);
            if(iterations<ITERATIONS || iterations>2_000_000)return false;
            byte[] salt=Base64.getDecoder().decode(fields[2]), expected=Base64.getDecoder().decode(fields[3]);
            return salt.length==16 && expected.length==32 && MessageDigest.isEqual(expected,derive(password,salt,iterations));
        }catch(Exception invalid){return false;}
    }
    private static byte[] derive(String password,byte[] salt,int iterations) {
        PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,iterations,256);
        try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        catch(GeneralSecurityException e){throw new IllegalStateException("Password KDF unavailable",e);}
        finally{spec.clearPassword();}
    }
}
