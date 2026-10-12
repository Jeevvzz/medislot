# PHASE REPORT 2

## 1. Run command and URL (one line each)
- **Run:** `MEDISLOT_DB=h2 MEDISLOT_SECURE_COOKIES=false PORT=3003 mvn -f backend/pom.xml clean compile exec:java` for the isolated Sandbox/H2 test instance; Java 17 Maven clean package passed.
- **URL:** Public demo: https://medislot-kh4pjyqg.manus.space (Phase 2 is published and verified); isolated browser test: https://3003-ikqsz0g20goreox22a1dg-ebd0f72a.sg2.manus.computer/.
- **Canonical release:** Commit `f875063d069e0795e797581a5fd53ab891d2442d` on `main`; published successfully as version `f875063d069e0795e797581a5fd53ab891d2442d` at the same public URL.

## 2. Demo logins (patient, doctor, admin)
All are synthetic demo accounts; password: `medislot123`.
- Patient: `patient1@medislot.local`
- Doctor: `aarav.doctor@medislot.local`
- Admin: `admin@medislot.local`

## 3. Files and folders created (short tree)
```text
backend/src/main/java/edu/alliance/medislot/
  dao/AppointmentDao.java                 # transactional booking and slot claims
  dao/AvailabilityDao.java                # weekly availability and favorites
  service/Phase2Service.java              # Phase 2 rules, ownership and validation
  servlet/Phase2Servlet.java              # role-protected Phase 2 API
backend/src/main/resources/db/migration/
  h2/V2__phase2_profiles_availability_bookings.sql
  mysql/V2__phase2_profiles_availability_bookings.sql
frontend/
  index.html                               # additive patient/doctor Phase 2 sections
  css/style.css                            # existing lavender-glass system extended
  js/api.js, js/home.js                   # same-origin Phase 2 client flows
root: README.md, plan.md, TODO.md, PHASE2_REPORT.md
```

## 4. API endpoints added (method, path, purpose, role)
- `GET /api/doctors/{doctorId}` — doctor profile details — authenticated.
- `GET /api/doctors/{doctorId}/slots?date=YYYY-MM-DD` — available starts for a doctor/date — authenticated.
- `GET /api/doctor/profile`, `PUT /api/doctor/profile` — read/update the signed-in doctor's profile summary and languages — DOCTOR.
- `GET /api/doctor/availability`, `PUT /api/doctor/availability/{weekday}`, `DELETE /api/doctor/availability/{weekday}` — read, set, or remove the signed-in doctor's recurring weekly window — DOCTOR.
- `GET /api/patient/favorites`, `POST /api/patient/favorites/{doctorId}`, `DELETE /api/patient/favorites/{doctorId}` — list/add/remove the signed-in patient's favorites — PATIENT.
- `GET /api/appointments`, `POST /api/appointments`, `POST /api/appointments/{appointmentId}/reschedule`, `DELETE /api/appointments/{appointmentId}` — read, book, reschedule, or cancel appointments — patient-owned write operations; authenticated patient/doctor participant reads.
- `GET /api/time-requests`, `POST /api/appointments/{appointmentId}/time-requests`, `POST /api/time-requests/{requestId}/decision` — read, propose, accept, or decline appointment time requests — assigned PATIENT or DOCTOR participant; only the other participant may decide.
- Phase 1 login, registration, logout, current-user, and authenticated `GET /api/doctors` endpoints remain in place.

## 5. DB tables added or changed
- `doctor_profiles`: additive `bio` and `languages` columns.
- `appointments`: additive `duration_minutes` (30-minute default) and `updated_at` columns.
- `doctor_availability`: recurring weekly windows, slot duration, and active state.
- `favorite_doctors`: patient-owned doctor favorites.
- `appointment_slot_claims`: unique doctor/date/start-time claim for each active appointment; seeded from existing non-cancelled appointments during migration.
- `appointment_time_requests`: proposals, proposer role, pending/accepted/declined/conflict status, and timestamps.
- Both MySQL and isolated-H2 Flyway V2 migrations are additive; idempotent Phase 2 availability seeding preserves existing user changes and does not reset the demo database.

## 6. Java concepts used (OOP, collections, streams, JDBC, exceptions) with file names
- **OOP/inheritance:** existing `model/User.java`, `Patient.java`, `Doctor.java`, and `Admin.java` role hierarchy is retained.
- **Collections/streams:** doctor listing and ordering in `dao/DoctorDao.java`; role/ownership-aware response data in `service/Phase2Service.java`.
- **JDBC/DAO and transactions:** `dao/AppointmentDao.java`, `dao/AvailabilityDao.java`, `dao/Database.java`; booking, cancellation, reschedule, and proposal acceptance maintain transactional slot claims.
- **Exceptions/HTTP mapping:** `exception/ConflictException.java`, `exception/ValidationException.java`, `service/Phase2Service.java`, and `servlet/Phase2Servlet.java`; unique-slot races become HTTP 409 with the required friendly message.

## 7. Tests done (pass/fail list, curl results)
- **PASS** — Java 17 Maven clean package (`BUILD SUCCESS`). The shade plugin reports non-fatal duplicate `LICENSE`/`NOTICE` resources from dependencies.
- **PASS** — `node --check` on all `frontend/js/*.js`; `frontend/manus-routes.json` parses and declares `/` and `/login.html`; `git diff --check` is clean.
- **PASS** — isolated local-H2 curl suite: patient, second patient, doctor, and admin login/role checks; authenticated doctor list and profile reads; doctor profile update; weekly availability add/read/remove and slot listing; favorites add/list/remove; patient/doctor proposals and acceptance; direct reschedule; cancellation; registration and logout; anonymous doctor-list rejection (401); wrong-role cancellation rejection (403).
- **PASS** — duplicate pending time proposal rejected with HTTP 409. Doctor decline persisted as `DECLINED`, and the original appointment stayed at its original time.
- **PASS** — concurrency clash on the same doctor/date/start-time: exactly one request returned HTTP 201; the competing patient received HTTP 409 with code `SLOT_TAKEN` and exact message: “That time was just booked. Please choose another available time.”
- **PASS** — patient browser check on the isolated Sandbox test server showed the friendly stale-slot conflict message.
- **PASS** — doctor browser check showed the doctor's schedule and a pending request with **Accept time**/**Decline** controls; exercising **Decline** updated the request to `DECLINED`.
- **PASS** — screenshots captured from the isolated test instance: [patient booking conflict](/home/ubuntu/screenshots/3003-ikqsz0g20goreox_2026-10-11_15-36-51_5196.webp), [doctor pending response controls](/home/ubuntu/screenshots/3003-ikqsz0g20goreox_2026-10-11_15-41-38_8879.webp), [doctor declined result](/home/ubuntu/screenshots/3003-ikqsz0g20goreox_2026-10-11_15-41-52_6370.webp).
- **PASS** — published release reports `published` for version `f875063d069e0795e797581a5fd53ab891d2442d` at the same permanent URL.
- **PASS** — read-only production curl checks: public home/login/health/route manifest returned HTTP 200; patient, doctor, and admin demo logins succeeded; HTTPS cookies included `HttpOnly`, `Secure`, and `SameSite=None`; managed-MySQL-backed doctor profiles, weekly availability, available slots, and patient appointment reads returned valid data. No production booking or other business writes were performed.

## 8. Known bugs or gaps
- No known blocking Phase 2 defects from the Sandbox and published-site checks. The build's shaded JAR emits non-fatal duplicate `LICENSE`/`NOTICE` resource warnings from dependencies.
- This is a synthetic public demo, not a production healthcare system. Never enter real personal, patient, or medical information.
- Availability uses the approved `Asia/Kolkata` schedule assumption, 30-minute starts, and recurring weekly windows; one-off closures/holiday calendars and later-phase workflows are not included.
- Managed MySQL is shared between Preview and publication; all booking, clash, and other business-write tests were kept on isolated local H2.

## 9. What Phase 3 will do
Phase 3 has **not** been started. Consultation records, billing/payments, pharmacy, chatbot, queues, reminders, and reports remain out of scope and will be considered only if requested.
