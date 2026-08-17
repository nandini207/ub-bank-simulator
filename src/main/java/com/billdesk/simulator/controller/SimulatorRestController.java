package com.billdesk.simulator.controller;

import com.billdesk.simulator.model.SimulatorOutcome;
import com.billdesk.simulator.model.SimulatorSettings;
import com.billdesk.simulator.model.TransactionRecord;
import com.billdesk.simulator.model.TransactionStatus;
import com.billdesk.simulator.repository.TransactionRepository;
import com.billdesk.simulator.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class SimulatorRestController {

    private static final Logger log = LoggerFactory.getLogger(SimulatorRestController.class);

    private final TransactionRepository transactionRepository;
    private final PaymentService paymentService;
    private final SimulatorSettings simulatorSettings;
    private final com.billdesk.simulator.config.SimulatorConfig config;

    public SimulatorRestController(TransactionRepository transactionRepository,PaymentService paymentService,SimulatorSettings simulatorSettings,com.billdesk.simulator.config.SimulatorConfig config) {
				this.transactionRepository = transactionRepository;
				this.paymentService = paymentService;
				this.simulatorSettings = simulatorSettings;
				this.config = config;
	}

    // ---------------------------------------------------------------
    // POST /api/payment/requests
    // ---------------------------------------------------------------
    @PostMapping("/payment/requests")
    public ResponseEntity<Map<String, Object>> createPaymentRequest(
            @RequestBody(required = false) Map<String, Object> body) {

        String pgId = (body != null && body.get("pgId") != null)
                ? body.get("pgId").toString() : "28026";

        // If frontend sends a specific pgRef, use it — otherwise generate one
        String pgRef = (body != null && body.get("pgRef") != null && !body.get("pgRef").toString().isBlank())
                ? body.get("pgRef").toString()
                : "PGREF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // ✅ DUPLICATE CHECK: reject if pgRef already exists in memory
        TransactionRecord existing = transactionRepository.findByPgRef(pgRef);
        if (existing != null) {
            log.debug("POST /api/payment/requests | DUPLICATE pgRef={} | rejected", pgRef);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error",   "Duplicate pgRef");
            error.put("message", "A transaction with pgRef '" + pgRef + "' already exists. Each payment must have a unique pgRef.");
            error.put("pgRef",   pgRef);
            error.put("existingStatus", normaliseStatus(existing.getStatus()));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        String now = Instant.now().toString();

        TransactionRecord record = new TransactionRecord();
        record.setPgId(pgId);
        record.setBillerId("123456");
        record.setBillerName("ABC Electricity");
        record.setAmount("1250.00");
        record.setPgRef(pgRef);
        record.setPayMode("P");
        record.setAuth("S");
        record.setDebitAccount("");
        record.setBank1("");
        record.setBank2("");
        record.setCrn("INR");
        record.setStatus(null);
        record.setResponseUrl("http://localhost:8383/api/callback/echo");

        transactionRepository.save(record);

        log.debug("POST /api/payment/requests | pgRef={} | responseUrl={}", pgRef, record.getResponseUrl());

        return ResponseEntity.ok(toMap(record, now, now));
    }

    // ---------------------------------------------------------------
    // POST /api/callback/echo
    // ---------------------------------------------------------------
    @PostMapping("/callback/echo")
    public ResponseEntity<String> callbackEcho(
            @RequestParam(required = false) String QS,
            @RequestBody(required = false) String body) {

        log.debug("==============================================");
        log.debug("✅ S2S CALLBACK RECEIVED at /api/callback/echo");
        log.debug("   QS param length = {}", QS != null ? QS.length() : 0);
        log.debug("   Body            = {}", body);
        log.debug("==============================================");
        return ResponseEntity.ok("OK");
    }

    // ---------------------------------------------------------------
    // GET /api/payment/requests/{pgRef}
    // ---------------------------------------------------------------
    @GetMapping("/payment/requests/{pgRef}")
    public ResponseEntity<Map<String, Object>> getPaymentRequest(@PathVariable String pgRef) {
        TransactionRecord record = transactionRepository.findByPgRef(pgRef);
        if (record == null) {
            log.debug("GET /api/payment/requests/{} - NOT FOUND", pgRef);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toMap(record, null, null));
    }

    // ---------------------------------------------------------------
    // POST /api/payment/outcome
    // ---------------------------------------------------------------
    @PostMapping("/payment/outcome")
    public ResponseEntity<Map<String, Object>> submitOutcome(
            @RequestBody Map<String, Object> body) {

        String pgRef   = (String) body.get("pgRef");
        String outcome = (String) body.get("outcome");
        String reason  = (String) body.getOrDefault("reason", "");

        log.debug("POST /api/payment/outcome | pgRef={} | outcome={} | reason={}", pgRef, outcome, reason);

        if (pgRef == null || pgRef.isBlank() || outcome == null || outcome.isBlank()) {
            log.debug("POST /api/payment/outcome - pgRef or outcome is missing");
            return ResponseEntity.badRequest().build();
        }

        TransactionRecord record = transactionRepository.findByPgRef(pgRef);
        if (record == null) {
            log.error("POST /api/payment/outcome - NO TRANSACTION FOUND for pgRef={}", pgRef);
            return ResponseEntity.notFound().build();
        }

        // ✅ DUPLICATE CHECK: reject if this transaction was already processed
        if (record.getStatus() != null) {
            log.debug("POST /api/payment/outcome | ALREADY PROCESSED pgRef={} | status={}", pgRef, record.getStatus());
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error",   "Already processed");
            error.put("message", "Transaction '" + pgRef + "' was already processed with status " + normaliseStatus(record.getStatus()) + ". Create a new payment request to try again.");
            error.put("pgRef",   pgRef);
            error.put("status",  normaliseStatus(record.getStatus()));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        SimulatorOutcome simulatorOutcome;
        try {
            simulatorOutcome = SimulatorOutcome.valueOf(outcome.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("POST /api/payment/outcome - invalid outcome value: {}", outcome);
            return ResponseEntity.badRequest().build();
        }

        paymentService.processPayOutcome(pgRef, simulatorOutcome, simulatorSettings, reason);

        if (!reason.isBlank() && simulatorOutcome == SimulatorOutcome.FAILURE) {
            transactionRepository.updateStatusAndBrn(
                    pgRef,
                    TransactionStatus.F,
                    record.getBrn() != null ? record.getBrn() : "",
                    reason
            );
        }

        TransactionRecord updated = transactionRepository.findByPgRef(pgRef);
        log.debug("POST /api/payment/outcome - DONE | pgRef={} | status={} | brn={}", pgRef, updated.getStatus(), updated.getBrn());
        return ResponseEntity.ok(toMap(updated, null, Instant.now().toString()));
    }

    // ---------------------------------------------------------------
    // GET /api/transactions
    // ---------------------------------------------------------------
    @GetMapping("/transactions")
    public ResponseEntity<List<Map<String, Object>>> getAllTransactions() {
        List<Map<String, Object>> list = transactionRepository.findAll()
                .stream()
                .map(r -> toMap(r, null, null))
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // ---------------------------------------------------------------
    // GET /api/settings
    // ---------------------------------------------------------------
    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        return ResponseEntity.ok(settingsToMap());
    }

    // ---------------------------------------------------------------
    // PUT /api/settings
    // ---------------------------------------------------------------
    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(
            @RequestBody Map<String, Object> body) {

        if (body.containsKey("defaultOutcome")) {
            try {
                simulatorSettings.setDefaultOutcome(
                        SimulatorOutcome.valueOf(body.get("defaultOutcome").toString().toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }
        if (body.containsKey("callbackDelaySeconds")) {
            simulatorSettings.setCallbackDelaySeconds(
                    ((Number) body.get("callbackDelaySeconds")).intValue());
        }
        if (body.containsKey("dropCallback")) {
            simulatorSettings.setDropCallback((Boolean) body.get("dropCallback"));
        }
        if (body.containsKey("duplicateCallback")) {
            simulatorSettings.setDuplicateCallback((Boolean) body.get("duplicateCallback"));
        }
        if (body.containsKey("pendingCheckerDelaySeconds")) {
            simulatorSettings.setPendingCheckerDelaySeconds(
                    ((Number) body.get("pendingCheckerDelaySeconds")).intValue());
        }
        if (body.containsKey("pendingFinalOutcome")) {
            try {
                simulatorSettings.setPendingFinalOutcome(
                        SimulatorOutcome.valueOf(body.get("pendingFinalOutcome").toString().toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }

        return ResponseEntity.ok(settingsToMap());
    }

    // ---------------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------------
    private Map<String, Object> toMap(TransactionRecord r, String createdAt, String updatedAt) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pgId",          r.getPgId());
        map.put("pgRef",         r.getPgRef());
        map.put("merchantName",  r.getBillerName());
        map.put("billerName",    r.getBillerName());
        map.put("billerId",      r.getBillerId());
        map.put("amount",        r.getAmount());
        map.put("crn",           r.getCrn());
        map.put("paymentMode",   r.getPayMode());
        map.put("authorization", r.getAuth());
        map.put("brn",           r.getBrn() != null ? r.getBrn() : "");
        map.put("status",        normaliseStatus(r.getStatus()));
        map.put("verificationStatus",
                r.getVerificationStatus() != null
                        ? normaliseStatus(r.getVerificationStatus())
                        : "NOT_VERIFIED_YET");

        map.put("verificationStatusMatchesPayment",
                r.getVerificationStatusMatchesPayment() != null
                        ? r.getVerificationStatusMatchesPayment()
                        : "NOT_VERIFIED_YET");

        map.put("statusMatchMessage", buildMatchMessage(r));
        map.put("reason",        r.getReason() != null ? r.getReason() : "");
        map.put("createdAt",     createdAt != null ? createdAt : Instant.now().toString());
        map.put("updatedAt",     updatedAt != null ? updatedAt : Instant.now().toString());
        return map;
    }

    private String normaliseStatus(TransactionStatus status) {
        if (status == null) return "PENDING";
        switch (status) {
            case S: return "SUCCESS";
            case F: return "FAILURE";
            case P: return "PENDING";
            case C: return "CANCELLED";
            default: return status.name();
        }
    }

    private Map<String, Object> settingsToMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pgId",                       "28026");
        map.put("defaultOutcome",             simulatorSettings.getDefaultOutcome().name());
        map.put("callbackDelaySeconds",       simulatorSettings.getCallbackDelaySeconds());
        map.put("dropCallback",               simulatorSettings.isDropCallback());
        map.put("duplicateCallback",          simulatorSettings.isDuplicateCallback());
        map.put("pendingCheckerDelaySeconds", simulatorSettings.getPendingCheckerDelaySeconds());
        map.put("pendingFinalOutcome",        simulatorSettings.getPendingFinalOutcome().name());
        return map;
    }
    
    private String buildMatchMessage(TransactionRecord r) {
        if (r.getVerificationStatus() == null) {
            return "Verification not yet called for this transaction.";
        }
        if (Boolean.TRUE.equals(r.getVerificationStatusMatchesPayment())) {
            return "✅ Payment status (" + normaliseStatus(r.getStatus()) +
                   ") matches Verification status (" +
                   normaliseStatus(r.getVerificationStatus()) + ").";
        }
        return "❌ MISMATCH — Payment status (" + normaliseStatus(r.getStatus()) +
               ") does NOT match Verification status (" +
               normaliseStatus(r.getVerificationStatus()) + ").";
    }
 // ---------------------------------------------------------------
 // POST /api/payment/verify/{pgRef}
 // Called by React "Verify Payment" button.
 // Triggers SHPVER internally and returns the match result.
 // ---------------------------------------------------------------
 @PostMapping("/payment/verify/{pgRef}")
 public ResponseEntity<Map<String, Object>> verifyPayment(@PathVariable String pgRef) {

     log.debug("POST /api/payment/verify/{} called", pgRef);

     TransactionRecord record = transactionRepository.findByPgRef(pgRef);
     if (record == null) {
         log.debug("POST /api/payment/verify/{} - NOT FOUND", pgRef);
         return ResponseEntity.notFound().build();
     }

     if (record.getStatus() == null) {
         log.debug("POST /api/payment/verify/{} - payment not yet processed", pgRef);
         Map<String, Object> error = new LinkedHashMap<>();
         error.put("error",   "Payment not processed yet");
         error.put("message", "Cannot verify a transaction that has not been paid yet.");
         return ResponseEntity.badRequest().body(error);
     }

     // Call processVerificationRequest() — same method SHPVER uses.
     // This will trigger the match logic and log the result in console.
     try {
         // Build a simple verification request string and encrypt it
         // exactly like a real merchant would send to /corp/SHPVER
         String verifyRequestString =
                 "PGID="     + record.getPgId()    + "&" +
                 "BillerID=" + record.getBillerId() + "&" +
                 "Amount="   + record.getAmount()   + "&" +
                 "PGRef="    + record.getPgRef()    + "&" +
                 "PayMode=V"                        + "&" +
                 "Bank1="                           + "&" +
                 "Bank2="                           + "&" +
                 "BRN="      + (record.getBrn() != null ? record.getBrn() : "");

         // Append checksum
         String checksum = com.billdesk.simulator.crypto.ChecksumUtil
                 .generateChecksum(verifyRequestString, config.getChecksumKey());
         String withChecksum = verifyRequestString + "&CheckSum=" + checksum;

         // Encrypt
         String encrypted = com.billdesk.simulator.crypto.CryptoUtil
                 .encrypt(withChecksum, config.getEncryptionKey());

         // Pass to processVerificationRequest — this triggers the match logic
         paymentService.processVerificationRequest(encrypted);

     } catch (Exception e) {
         log.error("POST /api/payment/verify/{} - error: {}", pgRef, e.getMessage());
         return ResponseEntity.status(500).build();
     }

     // Return updated record with match result
     TransactionRecord updated = transactionRepository.findByPgRef(pgRef);
     log.debug("POST /api/payment/verify/{} - done | match={}", pgRef, updated.getVerificationStatusMatchesPayment());
     return ResponseEntity.ok(toMap(updated, null, Instant.now().toString()));
 }
}