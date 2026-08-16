package com.billdesk.simulator.repository;

import com.billdesk.simulator.model.TransactionRecord;
import com.billdesk.simulator.model.TransactionStatus;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class TransactionRepository {

    // Key = PGRef, Value = TransactionRecord
    private final ConcurrentHashMap<String, TransactionRecord> transactionMap = new ConcurrentHashMap<>();

    /**
     * Saves a new transaction.
     * Called when SHPREQ or POST /api/payment/requests comes in.
     */
    public void save(TransactionRecord record) {
        transactionMap.put(record.getPgRef(), record);
    }

    /**
     * Finds a transaction by pgRef.
     */
    public TransactionRecord findByPgRef(String pgRef) {
        return transactionMap.get(pgRef);
    }

    /**
     * Updates payment status, BRN, and reason after payment is processed.
     * Called by processPayOutcome() — this is the S2S callback status.
     */
    public void updateStatusAndBrn(String pgRef, TransactionStatus status, String brn, String reason) {
        TransactionRecord record = transactionMap.get(pgRef);
        if (record != null) {
            record.setStatus(status);
            record.setBrn(brn);
            record.setReason(reason);
        }
    }

    /**
     * NEW: Updates the verification status after SHPVER is called.
     * Also compares it with the payment status and stores the match result.
     *
     * This is called from processVerificationRequest() after the
     * verification response is built — so we can track whether
     * payment status and verification status are the same.
     *
     * @param pgRef              - the transaction reference
     * @param verificationStatus - the status returned in SHPVER response
     */
    public void updateVerificationStatus(String pgRef, TransactionStatus verificationStatus) {
        TransactionRecord record = transactionMap.get(pgRef);
        if (record != null) {
            record.setVerificationStatus(verificationStatus);

            // Compare payment status vs verification status
            // Both should be the same in the normal flow.
            // If they differ, something went wrong (e.g. tampered callback,
            // race condition, or a bug in the simulator itself).
            TransactionStatus paymentStatus = record.getStatus();

            if (paymentStatus == null) {
                // Payment not yet processed — verification happened too early
                record.setVerificationStatusMatchesPayment(false);
            } else {
                boolean matches = paymentStatus == verificationStatus;
                record.setVerificationStatusMatchesPayment(matches);
            }
        }
    }

    /**
     * Returns all transactions in the current session.
     */
    public List<TransactionRecord> findAll() {
        return new ArrayList<>(transactionMap.values());
    }

    /**
     * Returns total count of transactions.
     */
    public int count() {
        return transactionMap.size();
    }
}