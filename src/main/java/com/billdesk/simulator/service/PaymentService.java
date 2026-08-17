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

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final String REQUEST_SEPARATOR  = "&";
    private static final String RESPONSE_SEPARATOR = "~";

    private final SimulatorConfig        config;
    private final TransactionRepository  transactionRepository;
    private final HttpClient             httpClient;

    public PaymentService(SimulatorConfig config,
                          TransactionRepository transactionRepository) {
        this.config               = config;
        this.transactionRepository = transactionRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // =========================================================================
    // 1. PARSE PAYMENT REQUEST  (called from GET /corp/SHPREQ)
    //    Merchant sends payment request to bank.
    //    Bank decrypts, validates checksum, saves transaction.
    //
    //    LOG 1 — [BANK] Payment request received from merchant
    // =========================================================================
    public TransactionRecord parsePaymentRequest(String encryptedQs) {

        if (encryptedQs == null || encryptedQs.isBlank()) {
            throw new IllegalArgumentException("SHPREQ QS parameter is empty");
        }

        String base64Qs    = encryptedQs.trim().replace(' ', '+');
        String decryptedData = CryptoUtil.decrypt(base64Qs, config.getEncryptionKey());

        Map<String, String> fields = parseFieldsByAmpersand(decryptedData);

        // Checksum validation
        String receivedChecksum = fields.get("CheckSum");
        if (receivedChecksum != null) {
            String dataWithoutChecksum = removeFieldFromString(decryptedData, "CheckSum", REQUEST_SEPARATOR);
            boolean valid = ChecksumUtil.validateChecksum(dataWithoutChecksum, config.getChecksumKey(), receivedChecksum);
            if (!valid) {
                log.warn("[BANK] Checksum FAILED | pgRef={}", fields.get("PGRef"));
            }
        }

        TransactionRecord record = new TransactionRecord();
        record.setPgId(fields.getOrDefault("PGID", ""));
        record.setBillerId(fields.getOrDefault("BillerID", ""));
        record.setBillerName(fields.getOrDefault("BillerName",
                fields.getOrDefault("Biller Name", "")));
        record.setAmount(fields.getOrDefault("Amount", ""));
        record.setPgRef(fields.getOrDefault("PGRef", ""));
        record.setPayMode(fields.getOrDefault("PayMode", "P"));
        record.setResponseUrl(fields.getOrDefault("RU", ""));
        record.setAuth(fields.getOrDefault("Auth", "S"));
        record.setDebitAccount(fields.getOrDefault("DebitAccount", ""));
        record.setBank1(fields.getOrDefault("Bank1", ""));
        record.setBank2(fields.getOrDefault("Bank2", ""));
        record.setCrn(fields.getOrDefault("CRN", "INR"));
        record.setStatus(null);

        transactionRepository.save(record);

        // ── LOG 1 ─────────────────────────────────────────────────────────────
        log.info("[BANK] Payment request received from merchant" +
                 " | pgRef={} | amount={} | billerId={} | payMode={}",
                record.getPgRef(), record.getAmount(),
                record.getBillerId(), record.getPayMode());

        return record;
    }

    // =========================================================================
    // 2. PROCESS PAY OUTCOME  (called when tester selects outcome in UI)
    //    Bank processes outcome, updates DB, fires payment S2S callback.
    //    After callback is confirmed delivered → verification flow starts.
    // =========================================================================
    public void processPayOutcome(String pgRef,
                                  SimulatorOutcome outcome,
                                  com.billdesk.simulator.model.SimulatorSettings settings,
                                  String failureReason) {

        TransactionRecord record = transactionRepository.findByPgRef(pgRef);
        if (record == null) {
            log.error("[BANK] processPayOutcome — no transaction found for pgRef={}", pgRef);
            return;
        }

        if (outcome == SimulatorOutcome.CANCEL) {
            handleCancelOutcome(record, settings);
            return;
        }

        String brn = "BRN" + System.currentTimeMillis();

        TransactionStatus status;
        String reason;

        if (outcome == SimulatorOutcome.SUCCESS) {
            status = TransactionStatus.S;
            reason = "";
        } else if (outcome == SimulatorOutcome.FAILURE) {
            status = TransactionStatus.F;
            reason = (failureReason != null && !failureReason.isBlank())
                    ? failureReason.trim() : "insufficient balance";
        } else {
            // PENDING
            status = TransactionStatus.P;
            reason = "";
        }

        transactionRepository.updateStatusAndBrn(pgRef, status, brn, reason);

        String responseString = buildPaymentResponseString(record, brn, status, reason);

        if (outcome == SimulatorOutcome.PENDING) {
            sendPendingFlow(record, responseString, settings);
        } else {
            sendCallbackAsync(record.getResponseUrl(), responseString, settings, false, pgRef);
        }
    }

    // =========================================================================
    // 3. PROCESS VERIFICATION REQUEST  (called from GET /corp/SHPVER)
    //    Merchant sends SHPVER to bank asking "confirm this transaction status".
    //    Bank looks up transaction, builds Annexure-4 response, returns it
    //    synchronously in the HTTP response body.
    //
    //    This handles the REAL /corp/SHPVER endpoint only.
    //    The auto-simulation path uses simulateVerificationFlow() below.
    // =========================================================================
    public String processVerificationRequest(String encryptedQs) {

        if (encryptedQs == null || encryptedQs.isBlank()) {
            throw new IllegalArgumentException("SHPVER QS parameter is empty");
        }

        String base64Qs     = encryptedQs.trim().replace(' ', '+');
        String decryptedData = CryptoUtil.decrypt(base64Qs, config.getEncryptionKey());

        Map<String, String> fields = parseFieldsByAmpersand(decryptedData);
        String pgRef = fields.getOrDefault("PGRef", "");

        TransactionRecord record = transactionRepository.findByPgRef(pgRef);
        if (record == null) {
            log.warn("[BANK] SHPVER — no transaction found for pgRef={}", pgRef);
            return null;
        }

        TransactionStatus status = record.getStatus() != null ? record.getStatus() : TransactionStatus.P;
        String brn = record.getBrn() != null ? record.getBrn() : "";

        String responseString = buildVerificationResponseString(record, brn, status);
        String withChecksum   = appendChecksum(responseString);
        String encrypted      = CryptoUtil.encrypt(withChecksum, config.getEncryptionKey());
        String urlEncoded     = URLEncoder.encode(encrypted, StandardCharsets.UTF_8);

        transactionRepository.updateVerificationStatus(pgRef, status);

        return "QS=" + urlEncoded;
    }

    // =========================================================================
    // SIMULATE VERIFICATION FLOW
    //    Called automatically AFTER the payment callback is delivered.
    //    Simulates the complete bank-side verification cycle:
    //
    //    LOG 3 — [BANK] Verification request received from merchant
    //    LOG 4 — [BANK] Verification callback sent to merchant
    //    LOG 5 — [BANK] Final status updated
    // =========================================================================
    public void simulateVerificationFlow(String pgRef) {

        TransactionRecord record = transactionRepository.findByPgRef(pgRef);
        if (record == null) {
            log.warn("[BANK] simulateVerificationFlow — no transaction found for pgRef={}", pgRef);
            return;
        }

        TransactionStatus verificationStatus = record.getStatus();
        if (verificationStatus == null) {
            log.warn("[BANK] simulateVerificationFlow — status null for pgRef={}", pgRef);
            return;
        }

        String brn = record.getBrn() != null ? record.getBrn() : "";

        // ── LOG 3 ─────────────────────────────────────────────────────────────
        log.info("[BANK] Verification request received from merchant" +
                 " | pgRef={} | brn={} | payMode=V", pgRef, brn);

        // Build Annexure-4 SHPVER response
        String responseString = buildVerificationResponseString(record, brn, verificationStatus);
        String withChecksum   = appendChecksum(responseString);
        String encrypted      = CryptoUtil.encrypt(withChecksum, config.getEncryptionKey());
        String urlEncoded     = URLEncoder.encode(encrypted, StandardCharsets.UTF_8);
        String callbackBody   = "QS=" + urlEncoded;

        // Save verification status + calculate match flag
        transactionRepository.updateVerificationStatus(pgRef, verificationStatus);
        TransactionRecord updated = transactionRepository.findByPgRef(pgRef);
        Boolean matches = updated.getVerificationStatusMatchesPayment();

        // ── LOG 4 ─────────────────────────────────────────────────────────────
        log.info("[BANK] Verification callback sent to merchant" +
                 " | pgRef={} | status={} | url={}",
                pgRef, verificationStatus, record.getResponseUrl());
        sendHttpPost(record.getResponseUrl(), callbackBody);

        // ── LOG 5 ─────────────────────────────────────────────────────────────
        if (Boolean.TRUE.equals(matches)) {
            log.info("[BANK] Final status updated" +
                     " | pgRef={} | paymentStatus={} | verificationStatus={} | match=true",
                    pgRef, updated.getStatus(), verificationStatus);
        } else {
            log.info("[BANK] Final status updated" +
                     " | pgRef={} | paymentStatus={} | verificationStatus={} | match=false | MISMATCH",
                    pgRef, updated.getStatus(), verificationStatus);
        }
    }

    /** Backward-compat alias */
    public void triggerAutoVerification(String pgRef) {
        simulateVerificationFlow(pgRef);
    }

    // =========================================================================
    // PRIVATE — build response strings
    // =========================================================================

    private String buildPaymentResponseString(TransactionRecord r, String brn,
                                              TransactionStatus status, String reason) {
        return "PGID="     + r.getPgId()    + RESPONSE_SEPARATOR
             + "BillerID=" + r.getBillerId() + RESPONSE_SEPARATOR
             + "Amount="   + r.getAmount()   + RESPONSE_SEPARATOR
             + "PGRef="    + r.getPgRef()    + RESPONSE_SEPARATOR
             + "PayMode="  + r.getPayMode()  + RESPONSE_SEPARATOR
             + "Auth="     + r.getAuth()     + RESPONSE_SEPARATOR
             + "Bank1="    + r.getBank1()    + RESPONSE_SEPARATOR
             + "Bank2="    + r.getBank2()    + RESPONSE_SEPARATOR
             + "BRN="      + brn             + RESPONSE_SEPARATOR
             + "Status="   + status.name()   + RESPONSE_SEPARATOR
             + "CRN="      + r.getCrn()      + RESPONSE_SEPARATOR
             + "Reason="   + reason;
    }

    private String buildVerificationResponseString(TransactionRecord r, String brn,
                                                   TransactionStatus status) {
        return "PGID="     + r.getPgId()    + RESPONSE_SEPARATOR
             + "BillerID=" + r.getBillerId() + RESPONSE_SEPARATOR
             + "Amount="   + r.getAmount()   + RESPONSE_SEPARATOR
             + "PGRef="    + r.getPgRef()    + RESPONSE_SEPARATOR
             + "PayMode="  + r.getPayMode()  + RESPONSE_SEPARATOR
             + "Bank1="    + r.getBank1()    + RESPONSE_SEPARATOR
             + "Bank2="    + r.getBank2()    + RESPONSE_SEPARATOR
             + "BRN="      + brn             + RESPONSE_SEPARATOR
             + "Status="   + status.name();
    }

    private String buildCancelResponseString(TransactionRecord r) {
        return "PGRef="  + r.getPgRef()       + RESPONSE_SEPARATOR
             + "Status=" + TransactionStatus.F.name() + RESPONSE_SEPARATOR
             + "Reason=Transaction Cancelled";
    }

    // =========================================================================
    // PRIVATE — CANCEL
    // =========================================================================
    private void handleCancelOutcome(TransactionRecord record,
                                     com.billdesk.simulator.model.SimulatorSettings settings) {
        transactionRepository.updateStatusAndBrn(
                record.getPgRef(), TransactionStatus.F, "", "Transaction Cancelled");
        String cancelResponse = buildCancelResponseString(record);
        sendCallbackAsync(record.getResponseUrl(), cancelResponse, settings, false, record.getPgRef());
    }

    // =========================================================================
    // PRIVATE — sendCallbackAsync
    //    Sends payment S2S callback in a background thread.
    //    After delivery, kicks off the verification flow automatically.
    //
    //    LOG 2 fires inside here.
    // =========================================================================
    private void sendCallbackAsync(String responseUrl, String responseString,
                                   com.billdesk.simulator.model.SimulatorSettings settings,
                                   boolean isSecondCallback, String pgRef) {

        Thread t = new Thread(() -> {

            if (settings.isDropCallback()) {
                log.debug("[BANK] DROP mode — callback suppressed | pgRef={}", pgRef);
                return;
            }

            if (settings.getCallbackDelaySeconds() > 0) {
                log.debug("[BANK] DELAY mode — waiting {}s | pgRef={}",
                        settings.getCallbackDelaySeconds(), pgRef);
                waitSeconds(settings.getCallbackDelaySeconds());
            }

            String body = "QS=" + URLEncoder.encode(
                    CryptoUtil.encrypt(appendChecksum(responseString), config.getEncryptionKey()),
                    StandardCharsets.UTF_8);

            // ── LOG 2 ─────────────────────────────────────────────────────────
            log.info("[BANK] Payment callback sent to merchant" +
                     " | pgRef={} | url={}", pgRef, responseUrl);
            sendHttpPost(responseUrl, body);

            if (settings.isDuplicateCallback() && !isSecondCallback) {
                log.debug("[BANK] DUPLICATE mode — sending duplicate callback | pgRef={}", pgRef);
                waitSeconds(2);
                sendHttpPost(responseUrl, body);
            }

            // After payment callback is delivered, simulate the merchant
            // sending a SHPVER verification request back to the bank.
            simulateVerificationFlow(pgRef);

        });

        t.setDaemon(true);
        t.start();
    }

    // =========================================================================
    // PRIVATE — sendPendingFlow  (Corporate Maker-Checker)
    // =========================================================================
    private void sendPendingFlow(TransactionRecord record, String pendingResponseString,
                                 com.billdesk.simulator.model.SimulatorSettings settings) {

        Thread t = new Thread(() -> {

            // Send Status=P callback (Maker done, pending checker)
            String pendingBody = "QS=" + URLEncoder.encode(
                    CryptoUtil.encrypt(appendChecksum(pendingResponseString), config.getEncryptionKey()),
                    StandardCharsets.UTF_8);

            // ── LOG 2a (pending) ──────────────────────────────────────────────
            log.info("[BANK] Payment callback sent to merchant" +
                     " | pgRef={} | status=P (pending checker) | url={}",
                    record.getPgRef(), record.getResponseUrl());
            sendHttpPost(record.getResponseUrl(), pendingBody);

            // Wait for checker to authorize
            int delay = settings.getPendingCheckerDelaySeconds();
            log.debug("[BANK] Waiting {}s for checker authorization | pgRef={}", delay, record.getPgRef());
            waitSeconds(delay);

            // Determine final outcome after checker
            SimulatorOutcome finalOutcome  = settings.getPendingFinalOutcome();
            TransactionStatus finalStatus  = (finalOutcome == SimulatorOutcome.SUCCESS)
                    ? TransactionStatus.S : TransactionStatus.F;
            String finalReason = (finalStatus == TransactionStatus.F) ? "Checker rejected" : "";
            String finalBrn    = "BRN" + System.currentTimeMillis();

            transactionRepository.updateStatusAndBrn(
                    record.getPgRef(), finalStatus, finalBrn, finalReason);

            String finalResponse = buildPaymentResponseString(record, finalBrn, finalStatus, finalReason);
            String finalBody = "QS=" + URLEncoder.encode(
                    CryptoUtil.encrypt(appendChecksum(finalResponse), config.getEncryptionKey()),
                    StandardCharsets.UTF_8);

            // ── LOG 2b (checker final) ────────────────────────────────────────
            log.info("[BANK] Payment callback sent to merchant" +
                     " | pgRef={} | status={} (checker final) | url={}",
                    record.getPgRef(), finalStatus, record.getResponseUrl());
            sendHttpPost(record.getResponseUrl(), finalBody);

            // Verification after checker final callback
            simulateVerificationFlow(record.getPgRef());

        });

        t.setDaemon(true);
        t.start();
    }

    // =========================================================================
    // PRIVATE — HTTP + utilities
    // =========================================================================
    private void sendHttpPost(String url, String body) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.debug("[BANK] HTTP POST response: {} | url={}", resp.statusCode(), url);
        } catch (IOException | InterruptedException e) {
            log.error("[BANK] HTTP POST failed | url={} | error={}", url, e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    private String appendChecksum(String data) {
        return data + RESPONSE_SEPARATOR + "CheckSum="
                + ChecksumUtil.generateChecksum(data, config.getChecksumKey());
    }

    private Map<String, String> parseFieldsByAmpersand(String data) {
        Map<String, String> map = new HashMap<>();
        for (String pair : data.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) map.put(pair.substring(0, i).trim(), pair.substring(i + 1).trim());
        }
        return map;
    }

    private String removeFieldFromString(String data, String fieldName, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String part : data.split("\\" + sep)) {
            if (!part.trim().startsWith(fieldName + "=")) {
                if (sb.length() > 0) sb.append(sep);
                sb.append(part);
            }
        }
        return sb.toString();
    }

    private void waitSeconds(int seconds) {
        try { Thread.sleep(seconds * 1000L); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private long countCharacter(String value, char character) {
        return value.chars().filter(c -> c == character).count();
    }
}