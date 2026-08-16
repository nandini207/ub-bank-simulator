package com.billdesk.simulator.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CallbackController {

    private static final Logger log =
            LoggerFactory.getLogger(CallbackController.class);

    @PostMapping("/callback")
    public ResponseEntity<String> handleCallback(
            @RequestParam("QS") String encryptedQs) {

        log.debug(
                "CALLBACK received | QS length={}",
                encryptedQs != null ? encryptedQs.length() : 0
        );

        log.debug("CALLBACK received successfully");

        return ResponseEntity.ok("CALLBACK RECEIVED SUCCESSFULLY");
    }
}
