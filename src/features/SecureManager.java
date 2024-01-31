package features;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import messages.KeyExchange;
import messages.TextMessage;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.PrintWriter;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static colors.ANSIColors.ANSI_MAGENTA;
import static colors.ANSIColors.coloredPrint;
import static util.Util.textMessageFromCommand;

public class SecureManager {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final PrintWriter out;
    private String secureMessageBuffer = "";
    private final Map<String, Identifier> usernameToIdentifier = new HashMap<>();

    // The constructor creates a pair unique per manager on init
    public SecureManager(PrintWriter out) {
        this.out = out;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            publicKey = pair.getPublic();
            privateKey = pair.getPrivate();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // -------------------------------   SECURE MESSAGE HANDLERS   ---------------------------------------------

    public void handleSendSecure(String data) throws JsonProcessingException {
        TextMessage tm = textMessageFromCommand(data);
        Identifier identifier = usernameToIdentifier.get(tm.username());
        if (identifier == null || identifier.getSessionKey() == null) {
            secureMessageBuffer = tm.message();
            usernameToIdentifier.putIfAbsent(tm.username(), new Identifier());
            out.println("PUBLIC_KEY_REQ " + mapper.writeValueAsString(new KeyExchange(tm.username(), encodeKey(publicKey))));
            return;
        }
        String encryptedMessage = aesEncrypt(tm.message(), identifier.sessionKey, generateIv());
        out.println("SECURE " + mapper.writeValueAsString(new TextMessage(tm.username(), encryptedMessage)));
    }

    public void handleReceiveSecure(String json) throws JsonProcessingException {
        TextMessage response = mapper.readValue(json, TextMessage.class);
        String decryptedMessage = aesDecrypt(response.message(),
                usernameToIdentifier.get(response.username()).sessionKey,
                generateIv()); // todo: generating IV in here might be detrimental
        coloredPrint(ANSI_MAGENTA, "[" + response.username() + "] : " + decryptedMessage);
    }

    public void handlePublicKeyReq(String json) {
        try {
            KeyExchange ke = mapper.readValue(json, KeyExchange.class);
            Identifier identifier = usernameToIdentifier.get(ke.username());
            if (identifier == null || identifier.getPublicKey() == null) {
                usernameToIdentifier.putIfAbsent(ke.username(), new Identifier(decodePublicKey(ke)));
                out.println("PUBLIC_KEY_RES " + new KeyExchange(ke.username(), encodeKey(publicKey)));
            }
            // todo: maybe a handler for if there is an already existing public key association

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void handlePublicKeyRes(String json) {
        try {
            KeyExchange ke = mapper.readValue(json, KeyExchange.class);
            Identifier identifier = usernameToIdentifier.get(ke.username());
            if (identifier == null || identifier.getPublicKey() == null) {
                usernameToIdentifier.putIfAbsent(ke.username(), new Identifier());
                SecretKey sessionKey = generateKey(256);
                usernameToIdentifier.get(ke.username()).setSessionKey(sessionKey); //todo: I don't know if this is a reference to mapped object or just the value
                String encryptedSessionKey = rsaEncrypt(sessionKey, ke.key());
                out.println("SESSION_KEY " + new KeyExchange("", encryptedSessionKey));/*todo: here encrypted SK*/
            }
        } catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException |
                 BadPaddingException | InvalidKeyException | JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public void handleSessionKey(String json) {

    }

    public void handleSecureReady(String json) {

    }

    // -----------------------------------   ENCRYPTION UTILS   ------------------------------------------------

    private String aesEncrypt(String input, SecretKey key, IvParameterSpec iv) {
        // honestly, I got so lost with all different AES encryption types I just chose the same as Baeldung uses.
        // It seems that every encryption type has a unique use case, and some are actually encoding with sprinkles on top (?) (ECB)
        // I'll surely take a better look some time later, but Im too washed to try dig into it now
        // https://www.baeldung.com/java-aes-encryption-decryption
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);
            byte[] cipherText = cipher.doFinal(input.getBytes());
            return Base64.getEncoder()
                    .encodeToString(cipherText);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidAlgorithmParameterException |
                 InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        }
    }

    private String aesDecrypt(String cipherText, SecretKey key, IvParameterSpec iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, iv);
            byte[] plainText = cipher.doFinal(Base64.getDecoder()
                    .decode(cipherText));
            return new String(plainText);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidAlgorithmParameterException |
                 InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        }
    }

    private String rsaEncrypt(SecretKey secretKey, PublicKey publicKey) {
        try {
            Cipher encryptCipher = Cipher.getInstance("RSA");
            encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedKey = encryptCipher.doFinal(secretKey.getEncoded());
            return Base64.getEncoder().encodeToString(encryptedKey);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException |
                 InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        }
    }

    private SecretKey rsaDectypt(String encryptedKey, PrivateKey privateKey) {
        try {
            Cipher decryptCipher = Cipher.getInstance("RSA");
            decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedKeyBytes = decryptCipher.doFinal(Base64.getDecoder().decode(encryptedKey));
            return new SecretKeySpec(decryptedKeyBytes, "AES");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException |
                 InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        }
    }

    private String encodeKey(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private PublicKey decodePublicKey(KeyExchange keyExchange) {
        try {
            byte[] decodedKey = Base64.getDecoder().decode(keyExchange.key());
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(decodedKey);
            return keyFactory.generatePublic(publicKeySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    private static SecretKey generateKey(int n) { // bit size : 128, 192, 256
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(n);
            return keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static IvParameterSpec generateIv() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return new IvParameterSpec(iv);
    }

    private static class Identifier {
        private PublicKey publicKey;
        private SecretKey sessionKey;

        public Identifier() {
            this.publicKey = null;
            this.sessionKey = null;
        }

        public Identifier(PublicKey publicKey) {
            this.publicKey = publicKey;
            this.sessionKey = null;
        }

        public PublicKey getPublicKey() {
            return publicKey;
        }

        public SecretKey getSessionKey() {
            return sessionKey;
        }

        public void setPublicKey(PublicKey publicKey) {
            this.publicKey = publicKey;
        }

        public void setSessionKey(SecretKey sessionKey) {
            this.sessionKey = sessionKey;
        }
    }

}
