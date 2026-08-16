
package com.billdesk.simulator.service;

import com.billdesk.simulator.config.SimulatorConfig;
import com.billdesk.simulator.crypto.ChecksumUtil;
import com.billdesk.simulator.crypto.CryptoUtil;
import com.billdesk.simulator.model.SimulatorOutcome;
import com.billdesk.simulator.model.TransactionRecord;
import com.billdesk.simulator.model.TransactionStatus;
import com.billdesk.simulator.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;


@Service
public class PaymentService {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentService.class);

    private static final String REQUEST_SEPARATOR = "&";

    private static final String RESPONSE_SEPARATOR = "~";

    private final SimulatorConfig config;
    private final TransactionRepository transactionRepository;
    private final HttpClient httpClient;

    public PaymentService(
            SimulatorConfig config,
            TransactionRepository transactionRepository) {

        this.config = config;
        this.transactionRepository = transactionRepository;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public TransactionRecord parsePaymentRequest(String encryptedQs) {

        if (encryptedQs == null || encryptedQs.isBlank()) {
            throw new IllegalArgumentException(
                    "SHPREQ QS parameter is empty"
            );
        }

        log.debug(
                "SHPREQ raw QS received | length={} | spaces={}",
                encryptedQs.length(),
                countCharacter(encryptedQs, ' ')
        );

        String base64Qs = encryptedQs
                .trim()
                .replace(' ', '+');

        log.debug(
                "SHPREQ Base64 QS prepared | length={} | pluses={}",
                base64Qs.length(),
                countCharacter(base64Qs, '+')
        );


        String decryptedData = CryptoUtil.decrypt(
                base64Qs,
                config.getEncryptionKey()
        );

        log.debug(
                "SHPREQ decrypted data: {}",
                decryptedData
        );


        Map<String, String> fields =
                parseFieldsByAmpersand(decryptedData);



        String receivedChecksum = fields.get("CheckSum");

        if (receivedChecksum != null) {

            String dataWithoutChecksum =
                    removeFieldFromString(
                            decryptedData,
                            "CheckSum",
                            REQUEST_SEPARATOR
                    );

            boolean checksumValid =
                    ChecksumUtil.validateChecksum(
                            dataWithoutChecksum,
                            config.getChecksumKey(),
                            receivedChecksum
                    );

            if (!checksumValid) {

                log.warn(
                        "Checksum validation FAILED for PGRef={}",
                        fields.get("PGRef")
                );


            } else {

                log.debug(
                        "Checksum validation PASSED for PGRef={}",
                        fields.get("PGRef")
                );
            }
        } else {

            log.warn(
                    "SHPREQ request does not contain CheckSum | PGRef={}",
                    fields.get("PGRef")
            );
        }


        TransactionRecord record = new TransactionRecord();

        record.setPgId(
                fields.getOrDefault("PGID", "")
        );

        record.setBillerId(
                fields.getOrDefault("BillerID", "")
        );

        record.setBillerName(
                fields.getOrDefault(
                        "BillerName",
                        fields.getOrDefault("Biller Name", "")
                )
        );

        record.setAmount(
                fields.getOrDefault("Amount", "")
        );

        record.setPgRef(
                fields.getOrDefault("PGRef", "")
        );

        record.setPayMode(
                fields.getOrDefault("PayMode", "P")
        );

        record.setResponseUrl(
                fields.getOrDefault("RU", "")
        );

        record.setAuth(
                fields.getOrDefault("Auth", "S")
        );

        record.setDebitAccount(
                fields.getOrDefault("DebitAccount", "")
        );

        record.setBank1(
                fields.getOrDefault("Bank1", "")
        );

        record.setBank2(
                fields.getOrDefault("Bank2", "")
        );

        record.setCrn(
                fields.getOrDefault("CRN", "INR")
        );

        record.setStatus(null);


        transactionRepository.save(record);

        log.debug(
                "Transaction saved: {}",
                record
        );

        return record;
    }


    public void processPayOutcome(
            String pgRef,
            SimulatorOutcome outcome,
            com.billdesk.simulator.model.SimulatorSettings settings, 
            String failureReason) {

        TransactionRecord record =
                transactionRepository.findByPgRef(pgRef);

        if (record == null) {

            log.error(
                    "No transaction found for pgRef={}",
                    pgRef
            );

            return;
        }



        if (outcome == SimulatorOutcome.CANCEL) {

            handleCancelOutcome(record, settings);

            return;
        }


        String brn =
                "BRN" + System.currentTimeMillis();

 

        TransactionStatus status;
        String reason;

        if (outcome == SimulatorOutcome.SUCCESS) {

            status = TransactionStatus.S;
            reason = "";

        } else if (outcome == SimulatorOutcome.FAILURE) {
            status = TransactionStatus.F;
            reason = (failureReason != null && !failureReason.isBlank())
                    ? failureReason.trim()
                    : "insufficient balance"; 

        } else {

          
            status = TransactionStatus.P;
            reason = "";
        }


        transactionRepository.updateStatusAndBrn(
                pgRef,
                status,
                brn,
                reason
        );


        String responseString =
                buildPaymentResponseString(
                        record,
                        brn,
                        status,
                        reason
                );


        if (outcome == SimulatorOutcome.PENDING) {

            sendPendingFlow(
                    record,
                    responseString,
                    settings
            );

        } else {

            sendCallbackAsync(
                    record.getResponseUrl(),
                    responseString,
                    settings,
                    false
            );
        }
    }

    public String processVerificationRequest(
            String encryptedQs) {

        if (encryptedQs == null || encryptedQs.isBlank()) {

            throw new IllegalArgumentException(
                    "SHPVER QS parameter is empty"
            );
        }

        log.debug(
                "SHPVER raw QS received | length={} | spaces={}",
                encryptedQs.length(),
                countCharacter(encryptedQs, ' ')
        );

        String base64Qs = encryptedQs
                .trim()
                .replace(' ', '+');

        log.debug(
                "SHPVER Base64 QS prepared | length={} | pluses={}",
                base64Qs.length(),
                countCharacter(base64Qs, '+')
        );



        String decryptedData =
                CryptoUtil.decrypt(
                        base64Qs,
                        config.getEncryptionKey()
                );

        log.debug(
                "SHPVER decrypted data: {}",
                decryptedData
        );



        Map<String, String> fields =
                parseFieldsByAmpersand(decryptedData);

        String pgRef =
                fields.getOrDefault("PGRef", "");


        TransactionRecord record =
                transactionRepository.findByPgRef(pgRef);

        if (record == null) {

            log.warn(
                    "SHPVER: no transaction found for PGRef={}",
                    pgRef
            );

            return null;
        }


        TransactionStatus status =
                record.getStatus();

        if (status == null) {

            status = TransactionStatus.P;
        }


        String brn =
                record.getBrn() != null
                        ? record.getBrn()
                        : "";


        String responseString =
                buildVerificationResponseString(
                        record,
                        brn,
                        status
                );



        String withChecksum =
                appendChecksum(responseString);



        String encrypted =
                CryptoUtil.encrypt(
                        withChecksum,
                        config.getEncryptionKey()
                );


        String urlEncoded =
                URLEncoder.encode(
                        encrypted,
                        StandardCharsets.UTF_8
                );

        log.debug(
                "SHPVER response status={} for PGRef={}",
                status,
                pgRef
        );
     // ── Save verification status and compare with payment status ──
        transactionRepository.updateVerificationStatus(pgRef, status);

        TransactionRecord updatedRecord = transactionRepository.findByPgRef(pgRef);
        Boolean matches = updatedRecord.getVerificationStatusMatchesPayment();

        if (Boolean.TRUE.equals(matches)) {
            log.info("✅ SHPVER STATUS MATCH | pgRef={} | paymentStatus={} | verificationStatus={} | MATCH=true",
                    pgRef, updatedRecord.getStatus(), status);
        } else {
            log.warn("❌ SHPVER STATUS MISMATCH | pgRef={} | paymentStatus={} | verificationStatus={} | MATCH=false",
                    pgRef, updatedRecord.getStatus(), status);
        }

        return "QS=" + urlEncoded;
    }


    private String buildPaymentResponseString(
            TransactionRecord record,
            String brn,
            TransactionStatus status,
            String reason) {

        return "PGID=" + record.getPgId()
                + RESPONSE_SEPARATOR

                + "BillerID=" + record.getBillerId()
                + RESPONSE_SEPARATOR

                + "Amount=" + record.getAmount()
                + RESPONSE_SEPARATOR

                + "PGRef=" + record.getPgRef()
                + RESPONSE_SEPARATOR

                + "PayMode=" + record.getPayMode()
                + RESPONSE_SEPARATOR

                + "Auth=" + record.getAuth()
                + RESPONSE_SEPARATOR

                + "Bank1=" + record.getBank1()
                + RESPONSE_SEPARATOR

                + "Bank2=" + record.getBank2()
                + RESPONSE_SEPARATOR

                + "BRN=" + brn
                + RESPONSE_SEPARATOR

                + "Status=" + status.name()
                + RESPONSE_SEPARATOR

                + "CRN=" + record.getCrn()
                + RESPONSE_SEPARATOR

                + "Reason=" + reason;
    }

 

    private String buildVerificationResponseString(
            TransactionRecord record,
            String brn,
            TransactionStatus status) {

        return "PGID=" + record.getPgId()
                + RESPONSE_SEPARATOR

                + "BillerID=" + record.getBillerId()
                + RESPONSE_SEPARATOR

                + "Amount=" + record.getAmount()
                + RESPONSE_SEPARATOR

                + "PGRef=" + record.getPgRef()
                + RESPONSE_SEPARATOR

                + "PayMode=" + record.getPayMode()
                + RESPONSE_SEPARATOR

                + "Bank1=" + record.getBank1()
                + RESPONSE_SEPARATOR

                + "Bank2=" + record.getBank2()
                + RESPONSE_SEPARATOR

                + "BRN=" + brn
                + RESPONSE_SEPARATOR

                + "Status=" + status.name();
    }



    private String buildCancelResponseString(
            TransactionRecord record) {

        return "PGRef=" + record.getPgRef()
                + RESPONSE_SEPARATOR

                + "Status=" + TransactionStatus.F.name()
                + RESPONSE_SEPARATOR

                + "Reason=Transaction Cancelled";
    }


    private void handleCancelOutcome(
            TransactionRecord record,
            com.billdesk.simulator.model.SimulatorSettings settings) {

        transactionRepository.updateStatusAndBrn(
                record.getPgRef(), TransactionStatus.F, "", "Transaction Cancelled"
        );

        String cancelResponse =
                buildCancelResponseString(record);

        log.debug(
                "Cancel response built for PGRef={}",
                record.getPgRef()
        );

        sendCallbackAsync(
                record.getResponseUrl(),
                cancelResponse,
                settings,
                false
        );
    }



    private void sendCallbackAsync(
            String responseUrl,
            String responseString,
            com.billdesk.simulator.model.SimulatorSettings settings,
            boolean isSecondCallback) {

        Thread callbackThread = new Thread(() -> {



            if (settings.isDropCallback()) {

                log.debug(
                        "DROP mode ON: not sending callback to {}",
                        responseUrl
                );

                return;
            }



            if (settings.getCallbackDelaySeconds() > 0) {

                log.debug(
                        "DELAY mode: waiting {} seconds before callback",
                        settings.getCallbackDelaySeconds()
                );

                waitSeconds(
                        settings.getCallbackDelaySeconds()
                );
            }

       

            String withChecksum =
                    appendChecksum(responseString);

     

            String encrypted =
                    CryptoUtil.encrypt(
                            withChecksum,
                            config.getEncryptionKey()
                    );

     
            String urlEncoded =
                    URLEncoder.encode(
                            encrypted,
                            StandardCharsets.UTF_8
                    );

            String body =
                    "QS=" + urlEncoded;

     

            sendHttpPost(
                    responseUrl,
                    body
            );

       

            if (settings.isDuplicateCallback()
                    && !isSecondCallback) {

                log.debug(
                        "DUPLICATE mode ON: sending second callback in 2 seconds"
                );

                waitSeconds(2);

                sendHttpPost(
                        responseUrl,
                        body
                );
            }

        });

        callbackThread.setDaemon(true);
        callbackThread.start();
    }

 

    private void sendPendingFlow(
            TransactionRecord record,
            String pendingResponseString,
            com.billdesk.simulator.model.SimulatorSettings settings) {

        Thread pendingThread = new Thread(() -> {


            String withChecksum =
                    appendChecksum(
                            pendingResponseString
                    );

            String encrypted =
                    CryptoUtil.encrypt(
                            withChecksum,
                            config.getEncryptionKey()
                    );

            String urlEncoded =
                    URLEncoder.encode(
                            encrypted,
                            StandardCharsets.UTF_8
                    );

            sendHttpPost(
                    record.getResponseUrl(),
                    "QS=" + urlEncoded
            );

            log.debug(
                    "Pending: sent Status=P for PGRef={}",
                    record.getPgRef()
            );

   

            int checkerDelay =
                    settings.getPendingCheckerDelaySeconds();

            log.debug(
                    "Pending: waiting {} seconds for Checker authorization",
                    checkerDelay
            );

            waitSeconds(checkerDelay);


            SimulatorOutcome finalOutcome =
                    settings.getPendingFinalOutcome();

            TransactionStatus finalStatus =
                    (finalOutcome == SimulatorOutcome.SUCCESS)
                            ? TransactionStatus.S
                            : TransactionStatus.F;

            String finalReason =
                    (finalStatus == TransactionStatus.F)
                            ? "Checker rejected"
                            : "";


            String finalBrn =
                    "BRN" + System.currentTimeMillis();


            transactionRepository.updateStatusAndBrn(
                    record.getPgRef(),
                    finalStatus,
                    finalBrn,
                    finalReason
            );


            String finalResponseString =
                    buildPaymentResponseString(
                            record,
                            finalBrn,
                            finalStatus,
                            finalReason
                    );

            String finalWithChecksum =
                    appendChecksum(
                            finalResponseString
                    );

            String finalEncrypted =
                    CryptoUtil.encrypt(
                            finalWithChecksum,
                            config.getEncryptionKey()
                    );

            String finalUrlEncoded =
                    URLEncoder.encode(
                            finalEncrypted,
                            StandardCharsets.UTF_8
                    );


            sendHttpPost(
                    record.getResponseUrl(),
                    "QS=" + finalUrlEncoded
            );

            log.debug(
                    "Pending: sent final status={} for PGRef={}",
                    finalStatus,
                    record.getPgRef()
            );

        });

        pendingThread.setDaemon(true);
        pendingThread.start();
    }


    private void sendHttpPost(
            String url,
            String body) {

        try {

            log.debug(
                    "S2S POST to: {} | body length: {}",
                    url,
                    body.length()
            );

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header(
                                    "Content-Type",
                                    "application/x-www-form-urlencoded"
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(body)
                            )
                            .timeout(
                                    Duration.ofSeconds(15)
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            log.debug(
                    "S2S POST response: HTTP {}",
                    response.statusCode()
            );

        } catch (IOException | InterruptedException e) {

            log.error(
                    "S2S POST to {} failed: {}",
                    url,
                    e.getMessage()
            );

            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }


    private String appendChecksum(
            String responseString) {

        String checksum =
                ChecksumUtil.generateChecksum(
                        responseString,
                        config.getChecksumKey()
                );

        return responseString
                + RESPONSE_SEPARATOR
                + "CheckSum="
                + checksum;
    }



    private Map<String, String> parseFieldsByAmpersand(
            String data) {

        Map<String, String> fieldMap =
                new HashMap<>();

        String[] pairs =
                data.split("&");

        for (String pair : pairs) {

            int equalsIndex =
                    pair.indexOf('=');

            if (equalsIndex > 0) {

                String key =
                        pair.substring(
                                0,
                                equalsIndex
                        ).trim();

                String value =
                        pair.substring(
                                equalsIndex + 1
                        ).trim();

                fieldMap.put(
                        key,
                        value
                );
            }
        }

        return fieldMap;
    }



    private String removeFieldFromString(
            String data,
            String fieldName,
            String separator) {

        String[] parts =
                data.split("\\" + separator);

        StringBuilder result =
                new StringBuilder();

        for (String part : parts) {

            if (!part.trim()
                    .startsWith(fieldName + "=")) {

                if (result.length() > 0) {
                    result.append(separator);
                }

                result.append(part);
            }
        }

        return result.toString();
    }


    private void waitSeconds(int seconds) {

        try {

            Thread.sleep(
                    seconds * 1000L
            );

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
        }
    }


    private long countCharacter(
            String value,
            char character) {

        return value.chars()
                .filter(c -> c == character)
             .count();
    }


}

