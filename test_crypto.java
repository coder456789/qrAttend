import java.util.Base64;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.SecureRandom;

public class test_crypto {
    public static void main(String[] args) throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256, new SecureRandom());
        SecretKey key = kg.generateKey();
        String b64 = Base64.getEncoder().encodeToString(key.getEncoded());
        System.out.println("Key: " + b64);
        System.out.println("Length: " + b64.length());
    }
}
