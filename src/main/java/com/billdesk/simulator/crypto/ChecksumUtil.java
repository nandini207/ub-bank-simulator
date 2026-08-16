package com.billdesk.simulator.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class ChecksumUtil {

    private static final String HMAC_SHA512 = "HmacSHA512";

    public static String generateChecksum(String data, String checksumKey) {
        try {
    
            Mac mac = Mac.getInstance(HMAC_SHA512);

   
            SecretKeySpec keySpec = new SecretKeySpec(
                    checksumKey.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA512
            );
            mac.init(keySpec);

         
            byte[] hashBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

     
            return convertBytesToHex(hashBytes);

        } catch (Exception e) {
            throw new RuntimeException("Checksum generation failed: " + e.getMessage(), e);
        }
    }

   
    public static boolean validateChecksum(String data, String checksumKey, String receivedChecksum) {
     
        String expectedChecksum = generateChecksum(data, checksumKey);

     
        return expectedChecksum.equalsIgnoreCase(receivedChecksum);
    }

    
    private static String convertBytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
          
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}
