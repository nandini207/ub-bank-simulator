package com.billdesk.simulator.model;

/**
 * Holds the complete data for one payment transaction.
 *
 * Fields from REQUEST (Annexure-1, PDF page 9):
 *   pgId, billerId, billerName, amount, pgRef, payMode,
 *   responseUrl, auth, debitAccount, bank1, bank2, crn
 *
 * Fields from RESPONSE (Annexure-2, PDF page 10):
 *   brn, status, reason
 *
 * NEW: verificationStatus
 *   Stores the status returned during SHPVER (verification call).
 *   Kept separate from payment status so we can compare both
 *   and detect any mismatch between S2S callback and SHPVER response.
 */
public class TransactionRecord {

    // ---------- Fields from PAYMENT REQUEST (Annexure-1) ----------

    private String pgId;
    private String billerId;
    private String billerName;
    private String amount;
    private String pgRef;
    private String payMode;
    private String responseUrl;
    private String auth;
    private String debitAccount;
    private String bank1;
    private String bank2;
    private String crn;

    // ---------- Fields generated for PAYMENT RESPONSE (Annexure-2) ----------

    // BRN - Bank Reference Number generated during payment
    private String brn;

    // status - set during S2S callback (payment outcome)
    // Values: S=Success, F=Failure, P=Pending, C=Cancel
    private TransactionStatus status;

    // Reason for failure
    private String reason;

    // ---------- NEW: Verification Status ----------

    // verificationStatus - set during SHPVER (verification call)
    // This is stored separately so we can compare it with payment status.
    //
    // In a real integration:
    //   - status          = what the bank told merchant via S2S callback
    //   - verificationStatus = what the bank says when merchant asks directly (SHPVER)
    //   Both should match. If they don't, something went wrong.
    private TransactionStatus verificationStatus;

    // verificationStatusMatchesPayment - result of the comparison
    // true  = both match (normal case)
    // false = mismatch detected (needs investigation)
    // null  = verification not yet done
    private Boolean verificationStatusMatchesPayment;

    // ---------- Constructors ----------

    public TransactionRecord() {}

    // ---------- Getters and Setters ----------

    public String getPgId() { return pgId; }
    public void setPgId(String pgId) { this.pgId = pgId; }

    public String getBillerId() { return billerId; }
    public void setBillerId(String billerId) { this.billerId = billerId; }

    public String getBillerName() { return billerName; }
    public void setBillerName(String billerName) { this.billerName = billerName; }

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }

    public String getPgRef() { return pgRef; }
    public void setPgRef(String pgRef) { this.pgRef = pgRef; }

    public String getPayMode() { return payMode; }
    public void setPayMode(String payMode) { this.payMode = payMode; }

    public String getResponseUrl() { return responseUrl; }
    public void setResponseUrl(String responseUrl) { this.responseUrl = responseUrl; }

    public String getAuth() { return auth; }
    public void setAuth(String auth) { this.auth = auth; }

    public String getDebitAccount() { return debitAccount; }
    public void setDebitAccount(String debitAccount) { this.debitAccount = debitAccount; }

    public String getBank1() { return bank1; }
    public void setBank1(String bank1) { this.bank1 = bank1; }

    public String getBank2() { return bank2; }
    public void setBank2(String bank2) { this.bank2 = bank2; }

    public String getCrn() { return crn; }
    public void setCrn(String crn) { this.crn = crn; }

    public String getBrn() { return brn; }
    public void setBrn(String brn) { this.brn = brn; }

    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public TransactionStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(TransactionStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public Boolean getVerificationStatusMatchesPayment() { return verificationStatusMatchesPayment; }
    public void setVerificationStatusMatchesPayment(Boolean verificationStatusMatchesPayment) {
        this.verificationStatusMatchesPayment = verificationStatusMatchesPayment;
    }

    @Override
    public String toString() {
        return "TransactionRecord{" +
               "pgRef='" + pgRef + "'" +
               ", status=" + status +
               ", verificationStatus=" + verificationStatus +
               ", match=" + verificationStatusMatchesPayment +
               ", brn='" + brn + "'" +
               "}";
    }
}