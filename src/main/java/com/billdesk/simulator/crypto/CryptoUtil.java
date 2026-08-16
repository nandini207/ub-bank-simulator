
package com.billdesk.simulator.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


public final class CryptoUtil {

    private static final String ALGORITHM = "AES";
    private static final String CIPHER_MODE = "AES/CBC/PKCS5Padding";

    private CryptoUtil() {
       
    }

    
    public static String encrypt(String plainText, String key) {
        try {
            validateKey(key);

            SecretKeySpec secretKey = buildSecretKey(key);
            IvParameterSpec iv = buildIV(key);

            Cipher cipher = Cipher.getInstance(CIPHER_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);

            byte[] encryptedBytes =
                    cipher.doFinal(
                            plainText.getBytes(StandardCharsets.UTF_8)
                    );

            return Base64.getEncoder().encodeToString(encryptedBytes);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Encryption failed: " + e.getMessage(),
                    e
            );
        }
    }

   
    public static String decrypt(String encryptedText, String key) {
        try {
            validateKey(key);

            if (encryptedText == null || encryptedText.isBlank()) {
                throw new IllegalArgumentException(
                        "Encrypted text is empty"
                );
            }

            
            String normalizedBase64 =
                    encryptedText
                            .trim()
                            .replace(' ', '+');

            
            normalizedBase64 =
                    normalizedBase64
                            .replace("\r", "")
                            .replace("\n", "")
                            .replace("\t", "");

            
            if (!normalizedBase64.matches(
                    "^[A-Za-z0-9+/]*={0,2}$")) {

                throw new IllegalArgumentException(
                        "QS contains invalid Base64 characters"
                );
            }

           
            int remainder = normalizedBase64.length() % 4;

            if (remainder == 2) {
                normalizedBase64 += "==";
            } else if (remainder == 3) {
                normalizedBase64 += "=";
            } else if (remainder == 1) {
                throw new IllegalArgumentException(
                        "Invalid Base64 length: "
                                + normalizedBase64.length()
                );
            }

            
            System.out.println(
                    "CryptoUtil.decrypt | originalLength="
                            + encryptedText.length()
                            + " | normalizedLength="
                            + normalizedBase64.length()
                            + " | spaces="
                            + countSpaces(encryptedText)
                            + " | pluses="
                            + countCharacter(normalizedBase64, '+')
            );

            

            SecretKeySpec secretKey = buildSecretKey(key);
            IvParameterSpec iv = buildIV(key);

            Cipher cipher = Cipher.getInstance(CIPHER_MODE);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    iv
            );

            byte[] decodedBytes =
                    Base64.getDecoder().decode(normalizedBase64);

            byte[] decryptedBytes =
                    cipher.doFinal(decodedBytes);

            return new String(
                    decryptedBytes,
                    StandardCharsets.UTF_8
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Decryption failed: " + e.getMessage(),
                    e
            );
        }
    }

   

    private static SecretKeySpec buildSecretKey(String key) {

        byte[] keyBytes =
                key.getBytes(StandardCharsets.UTF_8);

        
        byte[] key32Bytes = new byte[32];

        System.arraycopy(
                keyBytes,
                0,
                key32Bytes,
                0,
                Math.min(keyBytes.length, 32)
        );

        return new SecretKeySpec(
                key32Bytes,
                ALGORITHM
        );
    }

   

    private static IvParameterSpec buildIV(String key) {

        byte[] keyBytes =
                key.getBytes(StandardCharsets.UTF_8);

        byte[] ivBytes = new byte[16];

        System.arraycopy(
                keyBytes,
                0,
                ivBytes,
                0,
                Math.min(keyBytes.length, 16)
        );

        return new IvParameterSpec(ivBytes);
    }

    

    private static void validateKey(String key) {

        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Encryption key is empty"
            );
        }

        int keyLength =
                key.getBytes(StandardCharsets.UTF_8).length;

        if (keyLength != 32) {
            throw new IllegalArgumentException(
                    "AES-256 key must be exactly 32 bytes. "
                            + "Received: "
                            + keyLength
            );
        }
    }

    

    private static long countSpaces(String value) {

        return countCharacter(value, ' ');
    }

    private static long countCharacter(
            String value,
            char character) {

        return value.chars()
                .filter(c -> c == character)
                .count();
    }
}

