# Union Bank of India — OLT Payment Simulator
BillDesk Internal Testing Tool

---

## What This Does
Simulates the Union Bank of India internet banking endpoint.
BillDesk's connector sends requests here exactly like it would to the real bank.

---

## How to Build
```
mvn clean package -DskipTests
```
JAR will be at: `target/ub-bank-simulator-1.0.0.jar`

---

## How to Run
```
java -jar target/ub-bank-simulator-1.0.0.jar
```
Open browser: http://localhost:8080/control

---

## Endpoints
| Method | URL           | Purpose                        |
|--------|---------------|--------------------------------|
| GET    | /corp/SHPREQ  | Payment request from BillDesk  |
| GET    | /corp/SHPVER  | Status inquiry from BillDesk   |
| POST   | /corp/pay     | Fake login page form submit    |
| GET    | /control      | Tester control panel           |

---

## Project Structure
```
src/main/java/com/billdesk/simulator/
├── controller/         HTTP layer - receives requests, returns responses
│   ├── PaymentController.java
│   └── ControlPanelController.java
├── service/            Business logic layer
│   └── PaymentService.java
├── repository/         Data storage layer (in-memory)
│   └── TransactionRepository.java
├── model/              Data classes and enums
│   ├── TransactionRecord.java
│   ├── TransactionStatus.java  (S, F, P, C)
│   ├── PayMode.java            (P, V)
│   ├── SimulatorOutcome.java   (SUCCESS, FAILURE, PENDING, CANCEL)
│   └── SimulatorSettings.java
├── crypto/             Encryption and checksum
│   ├── CryptoUtil.java         (AES-256/CBC/PKCS5Padding)
│   └── ChecksumUtil.java       (HmacSHA512)
└── config/
    └── SimulatorConfig.java    (reads application.properties)
```

---

## UAT Keys (from PDF page 5)
- Encryption Key: `q4UOLnbuVc0mP8Jf634f1zCGVy2pf9lj`
- Checksum Key: `union@123`
- Request field separator: `&` (ampersand)
- Response field separator: `~` (tilde)

---

## Run Tests
```
mvn test
```
