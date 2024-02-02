package client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import exceptions.InputArgumentMismatchException;
import messages.KeyExchange;
import messages.TextMessage;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.PrintWriter;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static colors.ANSIColors.*;
import static util.Util.*;

public class SecureManager {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final PrintWriter out;
    private String secureMessageBuffer = "";
    private final Map<String, SecretKey> usernameToSessionKey = new HashMap<>();

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

    // -------------------------------   MESSAGE HANDLERS   ---------------------------------------------

    // STEP 1 SENDER SIDE
    public void handleSendSecure(String data) {
        try {
            TextMessage tm = textMessageFromCommand(data);
            // check if the mapping is present already, if not make one.
            SecretKey sessionKey = usernameToSessionKey.get(tm.username());
            // the session key is not set up
            if (sessionKey == null) {
                // save message for when the session key is set up
                secureMessageBuffer = tm.message();
                out.println("PUBLIC_KEY_REQ " + wrapInJson("username", tm.username()));
                return;
            }
            // the session key is set up, encrypt your message with it
            String encryptedMessage = aesEncrypt(tm.message(), sessionKey);
            out.println("SECURE " + mapper.writeValueAsString(new TextMessage(tm.username(), encryptedMessage)));
        } catch (InputArgumentMismatchException e) {
            System.err.println(e.getMessage());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------- MESSAGE RECEIVED HANDLER   --------------------------------------

    // STEP 2 | SK PRESENT | LAST STEP | RECEIVER SIDE
    public void handleReceiveSecure(String json) throws JsonProcessingException {
        // received a SECURE message
        // session key may or may not be set at this point
        // this client does not allow to send messages to be send when the mapping is empty though
        /*fixme: perhaps the receiving user might want to let the initiator
           know if they dont have an established SK, but imma just say "nah
           bro, no can do"*/
        TextMessage response = mapper.readValue(json, TextMessage.class);
        SecretKey sessionKey = usernameToSessionKey.get(response.username());
        if (sessionKey != null) {
            String decryptedMessage = aesDecrypt(response.message(), sessionKey);
            coloredPrint(ANSI_BLUE, "[" + response.username() + "] : " + decryptedMessage);
        } else
            coloredPrint(ANSI_RED, response.username() + " tried to send you a secure message but you can't read it. Encrypted without means to decrypt it");
    }

    // STEP 2 | SK NOT PRESENT | RECEIVER SIDE
    public void handleReceivePublicKeyReq(String json) throws JsonProcessingException {
        // request for a K+ received, sending it to the init
        out.println("PUBLIC_KEY_RES " +
                mapper.writeValueAsString(
                        new KeyExchange(getPropertyFromJson(json, "username"),
                                encodeKey(publicKey))
                )
        );
    }

    // STEP 3 | SENDER SIDE
    public void handleReceivePublicKeyRes(String json) throws JsonProcessingException {
        KeyExchange ke = mapper.readValue(json, KeyExchange.class);
        PublicKey pk = decodePublicKey(ke.key());
        SecretKey sessionKey = generateKey(256);
        usernameToSessionKey.put(ke.username(), sessionKey);
        String sessionKeyEncrypted = rsaEncrypt(sessionKey, pk);
        out.println("SESSION_KEY " + mapper.writeValueAsString(new KeyExchange(ke.username(), sessionKeyEncrypted)));
    }

    // STEP 4 | RECEIVER SIDE
    public void handleReceiveSessionKey(String json) throws JsonProcessingException {
        KeyExchange ke = mapper.readValue(json, KeyExchange.class);
        SecretKey sessionKey = rsaDecrypt(ke.key(), privateKey);
        usernameToSessionKey.put(ke.username(), sessionKey);
        out.println("SECURE_READY " + wrapInJson("username", ke.username()));
    }

    // STEP 5 | SENDER SIDE
    public void handleReceiveSecureReady(String json) throws JsonProcessingException {
        handleSendSecure(String.format("%s %s", getPropertyFromJson(json, "username"), secureMessageBuffer));
        secureMessageBuffer = "";
    }

    // --------------------------------   ENCRYPTION UTILS   -------------------------------------------

    private String aesEncrypt(String input, SecretKey key) {
        // I am using ECB (Electronic CodeBook), as all I want to do is to hide the contents
        // of the message from the server side. My concern is no necessarily security, but
        // rather privacy over admin supervision. Surely, if issues with messages getting
        // brute-forced arises, it would be smart to change to CBC and include the RSA
        // encrypted Init Vector along with the key itself. This is not a commercial project though,
        // so I don't care much
        // https://www.baeldung.com/java-aes-encryption-decryption
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] cipherText = cipher.doFinal(input.getBytes());
            return Base64.getEncoder()
                    .encodeToString(cipherText);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException |
                 InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        }
    }

    private String aesDecrypt(String cipherText, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] plainText = cipher.doFinal(Base64.getDecoder()
                    .decode(cipherText));
            return new String(plainText);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException |
                 InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        }
    }

    // Returns string representation of the encrypted SK already.
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

    private SecretKey rsaDecrypt(String encryptedKey, PrivateKey privateKey) {
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

    private PublicKey decodePublicKey(String key) {
        try {
            byte[] decodedKey = Base64.getDecoder().decode(key);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(decodedKey);
            return keyFactory.generatePublic(publicKeySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    private SecretKey generateKey(int n) { // bit size : 128, 192, 256
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(n);
            return keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
