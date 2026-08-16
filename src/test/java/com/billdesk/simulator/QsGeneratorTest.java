package com.billdesk.simulator;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.billdesk.simulator.crypto.ChecksumUtil;
import com.billdesk.simulator.crypto.CryptoUtil;

public class QsGeneratorTest {

    public static void main(String[] args) {


        String checksumKey = "union@123";
        String aesKey = "q4UOLnbuVc0mP8Jf634f1zCGVy2pf9lj";


        String data =
                "PGID=28026"
                + "&BillerID=TEST001"
                + "&BillerName=Test Merchant"
                + "&Amount=1253.50"
                + "&PGRef=TESTPG0001"
                + "&PayMode=P"
                + "&RU=http://localhost:8383/callback"
                + "&Auth=S"
                + "&DebitAccount=1234567890"
                + "&Bank1=UNIONBANK"
                + "&Bank2=TESTBANK"
                + "&CRN=INR";


        String checksum =
                ChecksumUtil.generateChecksum(data, checksumKey);

        System.out.println("========================================");
        System.out.println("CHECKSUM");
        System.out.println("========================================");
        System.out.println(checksum);



        String plainText =
                data + "&CheckSum=" + checksum;

        System.out.println();
        System.out.println("========================================");
        System.out.println("PLAIN TEXT BEFORE ENCRYPTION");
        System.out.println("========================================");
        System.out.println(plainText);


        String qs =
                CryptoUtil.encrypt(plainText, aesKey);

        System.out.println();
        System.out.println("========================================");
        System.out.println("QS");
        System.out.println("========================================");
        System.out.println(qs);


        String encodedQs =
                URLEncoder.encode(qs, StandardCharsets.UTF_8);

        System.out.println();
        System.out.println("========================================");
        System.out.println("URL ENCODED QS");
        System.out.println("========================================");
        System.out.println(encodedQs);


        String finalUrl =
                "http://localhost:8383/corp/SHPREQ"
                + "?PGID=28026"
                + "&QS="
                + encodedQs;

        System.out.println();
        System.out.println("========================================");
        System.out.println("FINAL SHPREQ URL");
        System.out.println("========================================");
        System.out.println(finalUrl);

        System.out.println();
        System.out.println("========================================");
        System.out.println("TEST DATA");
        System.out.println("========================================");
        System.out.println("PGID        : 28026");
        System.out.println("BillerID    : TEST001");
        System.out.println("BillerName  : Test Merchant");
        System.out.println("Amount      : 1253.50");
        System.out.println("PGRef       : TESTPG0001");
        System.out.println("PayMode     : P");
        System.out.println("Auth        : S");
        System.out.println("DebitAccount: 1234567890");
        System.out.println("========================================");
    }
}