package com.billdesk.simulator;

import com.billdesk.simulator.model.TransactionRecord;
import com.billdesk.simulator.model.TransactionStatus;
import com.billdesk.simulator.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


class TransactionRepositoryTest {

    private TransactionRepository repository;

 
    @BeforeEach
    void setUp() {
        repository = new TransactionRepository();
    }

  
    private TransactionRecord buildRecord(String pgRef) {
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
        r.setResponseUrl("http://localhost:8383/api/callback/echo");
        return r;
    }

  
    @Test
    void test_save_and_findByPgRef_returns_same_record() {
        TransactionRecord record = buildRecord("PGREF-001");
        repository.save(record);

        TransactionRecord found = repository.findByPgRef("PGREF-001");

        assertNotNull(found);
        assertEquals("PGREF-001", found.getPgRef());
        assertEquals("ABC Electricity", found.getBillerName());
        assertEquals("1250.00", found.getAmount());
    }

 
    @Test
    void test_findByPgRef_returns_null_when_not_found() {
        TransactionRecord found = repository.findByPgRef("DOES-NOT-EXIST");

        assertNull(found);
    }

   
    @Test
    void test_updateStatusAndBrn_updates_status_brn_reason() {
        repository.save(buildRecord("PGREF-002"));

        repository.updateStatusAndBrn("PGREF-002", TransactionStatus.S, "BRN999", "");

        TransactionRecord updated = repository.findByPgRef("PGREF-002");
        assertEquals(TransactionStatus.S, updated.getStatus());
        assertEquals("BRN999", updated.getBrn());
        assertEquals("", updated.getReason());
    }

 
    @Test
    void test_updateStatusAndBrn_stores_failure_reason() {
        repository.save(buildRecord("PGREF-003"));

        repository.updateStatusAndBrn("PGREF-003", TransactionStatus.F, "BRN000", "Insufficient balance");

        TransactionRecord updated = repository.findByPgRef("PGREF-003");
        assertEquals(TransactionStatus.F, updated.getStatus());
        assertEquals("Insufficient balance", updated.getReason());
    }

   
    @Test
    void test_findAll_returns_all_saved_records() {
        repository.save(buildRecord("PGREF-A"));
        repository.save(buildRecord("PGREF-B"));
        repository.save(buildRecord("PGREF-C"));

        List<TransactionRecord> all = repository.findAll();

        assertEquals(3, all.size());
    }

   
    @Test
    void test_count_returns_correct_number() {
        assertEquals(0, repository.count());

        repository.save(buildRecord("PGREF-X"));
        assertEquals(1, repository.count());

        repository.save(buildRecord("PGREF-Y"));
        assertEquals(2, repository.count());
    }

  
    @Test
    void test_saving_same_pgRef_twice_overwrites() {
        TransactionRecord first = buildRecord("PGREF-DUP");
        first.setBillerName("First Merchant");
        repository.save(first);

        TransactionRecord second = buildRecord("PGREF-DUP");
        second.setBillerName("Second Merchant");
        repository.save(second);

        TransactionRecord found = repository.findByPgRef("PGREF-DUP");
        assertEquals("Second Merchant", found.getBillerName());
        assertEquals(1, repository.count()); 
    }

  
    @Test
    void test_updateStatusAndBrn_on_missing_pgRef_does_nothing() {
      
        assertDoesNotThrow(() ->
            repository.updateStatusAndBrn("NO-SUCH-REF", TransactionStatus.S, "BRN123", "")
        );
    }
}