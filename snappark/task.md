# SnapPark v2.0 — Build Roadmap & Task Tracker

> **If token limit is hit, resume from the FIRST `[ ]` task below.**
> Project root: `c:\Users\Raj Modi\Pictures\snappark\snappark`

---

## Phase 1: Backend Foundation
- [ ] 1.1 Create `model/Fine.java` — Fine data model
- [ ] 1.2 Update `model/ParkingSession.java` — add phoneNumber, sessionPin, exitPin, paymentStatus
- [ ] 1.3 Update `model/Transaction.java` — add fineAmount, exitPin, sessionPin, paymentMethod
- [ ] 1.4 Update `model/Vehicle.java` — ensure phoneNumber constructor works
- [ ] 1.5 Create `dao/FineDAO.java` — CRUD for fines
- [ ] 1.6 Update `dao/SessionDAO.java` — support new fields
- [ ] 1.7 Update `dao/TransactionDAO.java` — support new fields
- [ ] 1.8 Update `dao/DatabaseManager.java` — add fines table + migrate columns
- [ ] 1.9 Create `service/SurgePricingService.java` — dynamic pricing engine
- [ ] 1.10 Create `service/FineService.java` — fine logic + enforcement
- [ ] 1.11 Update `service/BillingService.java` — integrate surge + fines
- [ ] 1.12 Create `service/WebServer.java` — embedded HTTP server + REST API
- [ ] 1.13 Update `Main.java` — start WebServer on app launch

## Phase 2: Mobile Web Pages (the phone experience)
- [ ] 2.1 Create `resources/com/snappark/mobile/style.css` — premium dark mobile theme
- [ ] 2.2 Create `resources/com/snappark/mobile/app.js` — frontend logic (API calls, slot grid, wizard)
- [ ] 2.3 Create `resources/com/snappark/mobile/entry.html` — check-in wizard (3 steps)
- [ ] 2.4 Create `resources/com/snappark/mobile/exit.html` — check-out + UPI + PIN

## Phase 3: Kiosk Redesign (JavaFX screens)
- [ ] 3.1 Redesign `fxml/welcome.fxml` — unified entry QR + exit QR + PIN verify
- [ ] 3.2 Update `controller/KioskWelcomeController.java` — QR generation, PIN verify, live stats
- [ ] 3.3 Update `controller/ParkingGrid3DController.java` — add "Report Wrong Parking" action

## Phase 4: Integration & Polish
- [ ] 4.1 Compile & fix all errors: `mvn clean compile`
- [ ] 4.2 Test: Launch app, scan QR, complete entry flow on phone
- [ ] 4.3 Test: Complete exit flow with payment simulation
- [ ] 4.4 Test: Fine reporting + fine on exit bill
- [ ] 4.5 Test: PIN verification on kiosk

## Phase 5: Bonus Features (if time permits)
- [ ] 5.1 Sound effects (beep, chime, alert)
- [ ] 5.2 Barrier lift animation on successful exit
- [ ] 5.3 EV charging + Accessible slot types
- [ ] 5.4 Ticket history by phone number

---

## Quick Reference — Key Decisions
- **Unified kiosk screen**: YES (entry + exit + PIN on one screen)
- **Surge pricing**: Time-based + Occupancy-based
- **UPI payment**: Simulated (QR shown, "I Have Paid" button)
- **Fine types**: WRONG_PARKING (₹200), OVERTIME (₹100/hr)
- **EV/Accessible slots**: YES as bonus
- **Embedded server port**: 8080
- **Session PIN**: 4 digits
- **Exit PIN**: 6 digits
