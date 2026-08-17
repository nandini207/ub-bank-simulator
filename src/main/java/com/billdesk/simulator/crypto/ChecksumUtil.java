package com.billdesk.simulator.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class ChecksumUtil {

    /*
     * Union Bank OLT documentation:
     *
     * - SHA-512 is used for checksum.
     * - Checksum is generated using the data string and checksum key.
     * - The generated checksum is appended as CheckSum=value.
     *
     * Note:
     * The supplied documentation does not explicitly define a different
     * checksum-key combination formula. Therefore this implementation uses
     * the existing data + checksumKey input relationship while replacing
     * HMAC-SHA512 with plain SHA-512.
     */

    public static String generateChecksum(String data, String checksumKey) {
        try {
            String checksumInput = data + checksumKey;

            MessageDigest digest = MessageDigest.getInstance("SHA-512");

            byte[] hashBytes = digest.digest(
                    checksumInput.getBytes(StandardCharsets.UTF_8)
            );

            return convertBytesToHex(hashBytes);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Checksum generation failed: " + e.getMessage(),
                    e
            );
        }
    }

    public static boolean validateChecksum(
            String data,
            String checksumKey,
            String receivedChecksum) {

        String expectedChecksum =
                generateChecksum(data, checksumKey);

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