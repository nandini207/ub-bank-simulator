package com.billdesk.simulator;

import com.billdesk.simulator.controller.SimulatorRestController;
import com.billdesk.simulator.model.SimulatorOutcome;
import com.billdesk.simulator.model.SimulatorSettings;
import com.billdesk.simulator.model.TransactionRecord;
import com.billdesk.simulator.model.TransactionStatus;
import com.billdesk.simulator.repository.TransactionRepository;
import com.billdesk.simulator.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class SimulatorRestControllerTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PaymentService paymentService;

    private SimulatorSettings simulatorSettings;
    private SimulatorRestController controller;

    @BeforeEach
    void setUp() {
        simulatorSettings = new SimulatorSettings();
        simulatorSettings.setDefaultOutcome(SimulatorOutcome.SUCCESS);
        simulatorSettings.setCallbackDelaySeconds(0);
        simulatorSettings.setDropCallback(false);
        simulatorSettings.setDuplicateCallback(false);
        simulatorSettings.setPendingCheckerDelaySeconds(10);
        simulatorSettings.setPendingFinalOutcome(SimulatorOutcome.SUCCESS);

        controller = new SimulatorRestController(
                transactionRepository, paymentService, simulatorSettings);
    }

 
    private TransactionRecord buildRecord(String pgRef, TransactionStatus status) {
        TransactionRecord r = new TransactionRecord();
        r.setPgId("28026");
        r.setPgRef(pgRef);
        r.setBillerId("123456");
        r.setBillerName("ABC Electricity");
        r.setAmount("1250.00");
        r.setPayMode("P");
        r.setAuth("S");
        r.setCrn("INR");
        r.setBank1("");
        r.setBank2("");
        r.setDebitAccount("");
        r.setStatus(status);
        r.setBrn(status != null ? "BRN123456" : "");
        r.setReason("");
        r.setResponseUrl("http://localhost:8383/api/callback/echo");
        return r;
    }


    @Test
    void createPaymentRequest_returns_200_with_pgRef() {
   
        Map<String, Object> body = new HashMap<>();
        body.put("pgId", "28026");

       
        ResponseEntity<Map<String, Object>> response = controller.createPaymentRequest(body);

        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("pgRef"));
        assertNotNull(response.getBody().get("pgRef"));

      
        verify(transactionRepository, times(1)).save(any(TransactionRecord.class));
    }

    @Test
    void createPaymentRequest_with_null_body_uses_defaults() {
        ResponseEntity<Map<String, Object>> response = controller.createPaymentRequest(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("28026", response.getBody().get("pgId"));
    }

 

    @Test
    void getPaymentRequest_returns_200_when_found() {
        TransactionRecord record = buildRecord("PGREF-ABC", null);
        when(transactionRepository.findByPgRef("PGREF-ABC")).thenReturn(record);

        ResponseEntity<Map<String, Object>> response = controller.getPaymentRequest("PGREF-ABC");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("PGREF-ABC", response.getBody().get("pgRef"));
        assertEquals("ABC Electricity", response.getBody().get("merchantName"));
    }

    @Test
    void getPaymentRequest_returns_404_when_not_found() {
        when(transactionRepository.findByPgRef("NO-SUCH")).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.getPaymentRequest("NO-SUCH");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }



    @Test
    void submitOutcome_SUCCESS_returns_200_and_calls_processPayOutcome() {
       
        TransactionRecord before  = buildRecord("PGREF-001", null);
        TransactionRecord after   = buildRecord("PGREF-001", TransactionStatus.S);
        when(transactionRepository.findByPgRef("PGREF-001"))
                .thenReturn(before)   
                .thenReturn(after);   

        Map<String, Object> body = new HashMap<>();
        body.put("pgRef",   "PGREF-001");
        body.put("outcome", "SUCCESS");
        body.put("reason",  "");

       
        ResponseEntity<Map<String, Object>> response = controller.submitOutcome(body);

        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SUCCESS", response.getBody().get("status")); 
        verify(paymentService, times(1))
                .processPayOutcome(eq("PGREF-001"), eq(SimulatorOutcome.SUCCESS), any(), eq(""));
    }

    @Test
    void submitOutcome_FAILURE_with_reason_returns_200() {
        TransactionRecord before = buildRecord("PGREF-002", null);
        TransactionRecord after  = buildRecord("PGREF-002", TransactionStatus.F);
        after.setReason("Insufficient balance");
        when(transactionRepository.findByPgRef("PGREF-002"))
                .thenReturn(before)
                .thenReturn(after);

        Map<String, Object> body = new HashMap<>();
        body.put("pgRef",   "PGREF-002");
        body.put("outcome", "FAILURE");
        body.put("reason",  "Insufficient balance");

        ResponseEntity<Map<String, Object>> response = controller.submitOutcome(body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("FAILURE", response.getBody().get("status"));
        assertEquals("Insufficient balance", response.getBody().get("reason"));
    }

    @Test
    void submitOutcome_returns_404_when_transaction_not_found() {
        when(transactionRepository.findByPgRef("MISSING")).thenReturn(null);

        Map<String, Object> body = new HashMap<>();
        body.put("pgRef",   "MISSING");
        body.put("outcome", "SUCCESS");

        ResponseEntity<Map<String, Object>> response = controller.submitOutcome(body);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
       
        verify(paymentService, never()).processPayOutcome(any(), any(), any(), any());
    }

    @Test
    void submitOutcome_returns_400_when_pgRef_is_null() {
        Map<String, Object> body = new HashMap<>();
        body.put("outcome", "SUCCESS");
       

        ResponseEntity<Map<String, Object>> response = controller.submitOutcome(body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void submitOutcome_returns_400_when_outcome_is_invalid() {
        TransactionRecord record = buildRecord("PGREF-003", null);
        when(transactionRepository.findByPgRef("PGREF-003")).thenReturn(record);

        Map<String, Object> body = new HashMap<>();
        body.put("pgRef",   "PGREF-003");
        body.put("outcome", "INVALID_OUTCOME");

        ResponseEntity<Map<String, Object>> response = controller.submitOutcome(body);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }


    @Test
    void getAllTransactions_returns_all_records() {
        List<TransactionRecord> records = List.of(
                buildRecord("PGREF-X", TransactionStatus.S),
                buildRecord("PGREF-Y", TransactionStatus.F),
                buildRecord("PGREF-Z", null)
        );
        when(transactionRepository.findAll()).thenReturn(records);

        ResponseEntity<List<Map<String, Object>>> response = controller.getAllTransactions();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().size());
    }

    @Test
    void getAllTransactions_returns_empty_list_when_no_transactions() {
        when(transactionRepository.findAll()).thenReturn(List.of());

        ResponseEntity<List<Map<String, Object>>> response = controller.getAllTransactions();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().size());
    }


    @Test
    void getSettings_returns_current_settings() {
        ResponseEntity<Map<String, Object>> response = controller.getSettings();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("SUCCESS", response.getBody().get("defaultOutcome"));
        assertEquals(0, response.getBody().get("callbackDelaySeconds"));
        assertEquals(false, response.getBody().get("dropCallback"));
    }

    @Test
    void updateSettings_changes_defaultOutcome() {
        Map<String, Object> body = new HashMap<>();
        body.put("defaultOutcome", "FAILURE");

        ResponseEntity<Map<String, Object>> response = controller.updateSettings(body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("FAILURE", response.getBody().get("defaultOutcome"));
        assertEquals(SimulatorOutcome.FAILURE, simulatorSettings.getDefaultOutcome());
    }

    @Test
    void updateSettings_changes_callbackDelaySeconds() {
        Map<String, Object> body = new HashMap<>();
        body.put("callbackDelaySeconds", 5);

        controller.updateSettings(body);

        assertEquals(5, simulatorSettings.getCallbackDelaySeconds());
    }

    @Test
    void updateSettings_changes_dropCallback_to_true() {
        Map<String, Object> body = new HashMap<>();
        body.put("dropCallback", true);

        controller.updateSettings(body);

        assertTrue(simulatorSettings.isDropCallback());
    }


    @Test
    void status_S_is_normalised_to_SUCCESS_in_response() {
        TransactionRecord record = buildRecord("PGREF-S", TransactionStatus.S);
        when(transactionRepository.findByPgRef("PGREF-S")).thenReturn(record);

        ResponseEntity<Map<String, Object>> response = controller.getPaymentRequest("PGREF-S");

        assertEquals("SUCCESS", response.getBody().get("status"));
    }

    @Test
    void status_F_is_normalised_to_FAILURE_in_response() {
        TransactionRecord record = buildRecord("PGREF-F", TransactionStatus.F);
        when(transactionRepository.findByPgRef("PGREF-F")).thenReturn(record);

        ResponseEntity<Map<String, Object>> response = controller.getPaymentRequest("PGREF-F");

        assertEquals("FAILURE", response.getBody().get("status"));
    }

    @Test
    void status_P_is_normalised_to_PENDING_in_response() {
        TransactionRecord record = buildRecord("PGREF-P", TransactionStatus.P);
        when(transactionRepository.findByPgRef("PGREF-P")).thenReturn(record);

        ResponseEntity<Map<String, Object>> response = controller.getPaymentRequest("PGREF-P");

        assertEquals("PENDING", response.getBody().get("status"));
    }

    @Test
    void status_C_is_normalised_to_CANCELLED_in_response() {
        TransactionRecord record = buildRecord("PGREF-C", TransactionStatus.C);
        when(transactionRepository.findByPgRef("PGREF-C")).thenReturn(record);

        ResponseEntity<Map<String, Object>> response = controller.getPaymentRequest("PGREF-C");

        assertEquals("CANCELLED", response.getBody().get("status"));
    }
}
