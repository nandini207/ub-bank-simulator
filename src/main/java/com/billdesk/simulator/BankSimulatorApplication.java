package com.billdesk.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main class - starts the Spring Boot application.
 *
 * How to run:
 *   java -jar ub-bank-simulator-1.0.0.jar
 *
 * Then open: http://localhost:8080/control  (tester control panel)
 */
@SpringBootApplication
public class BankSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankSimulatorApplication.class, args);
    }
}
