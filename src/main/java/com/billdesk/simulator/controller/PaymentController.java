package com.billdesk.simulator.controller;

import com.billdesk.simulator.model.SimulatorOutcome;
import com.billdesk.simulator.model.SimulatorSettings;
import com.billdesk.simulator.model.TransactionRecord;
import com.billdesk.simulator.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/corp")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    private final SimulatorSettings simulatorSettings;

    public PaymentController(PaymentService paymentService, SimulatorSettings simulatorSettings) {
        this.paymentService = paymentService;
        this.simulatorSettings = simulatorSettings;
    }


    @GetMapping("/SHPREQ")
    public String handlePaymentRequest(
            @RequestParam("PGID") String pgId,
            @RequestParam("QS") String encryptedQs,
            Model model) {

        log.debug("SHPREQ received | PGID={}", pgId);

        try {
        	encryptedQs = encryptedQs.replace(" ", "+");
            TransactionRecord record = paymentService.parsePaymentRequest(encryptedQs);
            
            model.addAttribute("pgRef", record.getPgRef());
            model.addAttribute("amount", record.getAmount());
            model.addAttribute("billerName", record.getBillerName());
            model.addAttribute("billerId", record.getBillerId());
            model.addAttribute("crn", record.getCrn());

            return "login";

        } catch (Exception e) {
            log.error("SHPREQ failed: {}", e.getMessage(), e);
            return "error";
        }
    }


    @GetMapping("/SHPVER")
    @ResponseBody
    public ResponseEntity<String> handleVerificationRequest(
            @RequestParam("PGID") String pgId,
            @RequestParam("QS") String encryptedQs) {

        log.debug("SHPVER received | PGID={}", pgId);

        try {

            String encryptedResponse = paymentService.processVerificationRequest(encryptedQs);

            if (encryptedResponse == null) {
 
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(encryptedResponse);

        } catch (Exception e) {
            log.error("SHPVER failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }


    @PostMapping("/pay")
    public ResponseEntity<String> handlePaySubmission(
            @RequestParam("pgRef") String pgRef,
            @RequestParam("outcome") String outcomeString,
    		@RequestParam(value = "reason", required = false) String reason){

        log.debug("Pay clicked | pgRef={} | outcome={} | reason={}", pgRef, outcomeString, reason);

        try {

            SimulatorOutcome outcome = SimulatorOutcome.valueOf(outcomeString.toUpperCase());

      
            paymentService.processPayOutcome(pgRef, outcome, simulatorSettings, reason);

            return ResponseEntity.ok("Payment outcome processed: " + outcome);

        } catch (IllegalArgumentException e) {
            log.error("Invalid outcome: {}", outcomeString);
            return ResponseEntity.badRequest().body("Invalid outcome: " + outcomeString);
        } catch (Exception e) {
            log.error("Pay failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
}