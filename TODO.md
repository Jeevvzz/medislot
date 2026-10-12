# MediSlot Phase 1 — managed deployment ToDo

## [x] Durable managed database with Java/JDBC retained
- Preserve Java 17, Maven, embedded Jetty, JDBC/DAO access, and no ORM.
- Replace production H2 file persistence with the Manus-managed MySQL database through runtime `DATABASE_URL`; retain H2 only for explicit local/test use. If the production configuration is missing or invalid, fail clearly and never silently fall back to H2.
- Create versioned, repeatable-safe schema migrations and idempotent seed logic. Restarts/deployments retain user changes instead of resetting them.
- Provision the public demo with the existing synthetic seeded accounts/data; do not import prior smoke-test registrations. The first managed startup may apply the required schema migration and idempotent seed; keep ad hoc registration, destructive, and arbitrary data-write tests on isolated local H2, not shared managed MySQL.

## [x] Phase 1 authentication, roles, and API
- Preserve the object-oriented `User`/`Patient`/`Doctor`/`Admin` hierarchy; role-based PATIENT, DOCTOR, and ADMIN flows; existing email/password login, patient registration, logout, server-side sessions, and PBKDF2 password hashing. Do not switch to Manus OAuth.
- Preserve authenticated `GET /api/doctors` and the current Phase 1 API surface. Keep unauthorized access rejected and dashboard navigation role-specific.
- Keep `Secure`, `HttpOnly`, and `SameSite=None` HTTPS Preview session-cookie attributes.

## [x] Phase 1 interface and demo safety
- Preserve the vanilla HTML/CSS/JavaScript frontend, existing light-purple glass styling, responsive layout, and accessible focus/state treatment.
- Show a prominent demo-only warning: visitors must not enter real personal, patient, or medical information.
- Do not implement Phase 2 or later workflows, including booking/availability, consultation, billing, pharmacy, chatbot, queues, reminders, or reports.

## [x] Container and Webdev deployment configuration
- Package and run the Java application as a container, bind Jetty on `0.0.0.0` using platform-provided `PORT` (default 3000), and expose an unauthenticated health endpoint suitable for deployment readiness.
- Synchronize the approved Java/Maven application, vanilla frontend, deployment configuration, project logo metadata, and updated documentation into the initialized Manus Webdev project. Keep database credentials runtime-only.
- Retain the platform-default hosting policy; do not enable always-on hosting unless required by the platform.

## [x] Canonical checkpoint and public release
- Save the verified version as a checkpoint on canonical `main`; never force-push or rewrite history. Published code version: `ed788f4bea28be2734f4e02b3b77df0bfec93a1b`.
- Explicitly publish the Phase 1 demo publicly under the user's approval and confirm success. Permanent public URL: https://medislot-kh4pjyqg.manus.space

# Phase 2 — approved outcomes

## [x] Doctor profiles and availability
- Keep the existing doctor identity, specialty, experience, fee, rating, and avatar data; let a doctor update a short profile summary and manage their recurring weekly availability.
- Seed synthetic demonstration availability Monday through Friday, 09:00–17:00 in `Asia/Kolkata`. Use 30-minute slots; expose only slot starts fully within a doctor's declared window. Patients can read doctor details and available starts; doctors can read and update their own availability.

## [x] Patient favorites
- Let authenticated patients list, add, and remove their own favorite doctors; favorites persist per patient. Enforce ownership on every operation.

## [x] Bookings, doctor schedule, and time proposals
- Let a patient read their own bookings, book an available slot, reschedule their own future booking, and cancel their own future booking. A doctor can read their own upcoming schedule.
- Let the assigned patient or doctor propose an alternate available time; only the other party can accept or decline. Allow at most one pending proposal per appointment. An accepted proposal changes the appointment only when the slot remains free; otherwise preserve the original appointment and return the friendly slot-conflict response.

## [x] Concurrency-safe slot booking
- A doctor, calendar date, and slot start can have at most one active appointment, including when requests race. Enforce this in the database transaction/unique active-slot claim; do not rely on a preflight availability check alone.
- When a competing booking loses, return HTTP 409 with exactly: “That time was just booked. Please choose another available time.” Cancellation releases the active slot; a failed reschedule or time-proposal acceptance leaves the original booking intact.

## [x] Phase 2 interface, verification, report, and same-site publication
- Add Phase 2 screens and controls to the existing dashboard using its existing lavender-glass components and responsive styling. Do not redesign Phase 1 or alter its login/session, existing APIs, public-demo warning, or branding; do not implement Phase 3+ features.
- Run Java 17/Maven and frontend checks in the Sandbox; test all booking/clash writes on isolated local H2 and use read-only checks on shared managed MySQL. Browser-check the Sandbox UI, then publish the verified canonical-main Phase 2 version to the same permanent public URL: https://medislot-kh4pjyqg.manus.space.
- Deliver `PHASE2_REPORT.md` using the exact same nine-section “PHASE REPORT 1–9” format as `PHASE1_REPORT.md`, with actual pass/fail evidence and the permanent URL.

## Phase 2 completion evidence
- Canonical Phase 2 implementation commit `f875063d069e0795e797581a5fd53ab891d2442d` was pushed to `main` and published successfully at https://medislot-kh4pjyqg.manus.space.
- Isolated H2 API and concurrency tests passed; browser checks verified the patient conflict message and doctor time-request response controls. Production checks were read-only and confirmed the deployed MySQL-backed Phase 2 reads.
- Final nine-section report: `PHASE2_REPORT.md`.
- Phase 2 prerequisite reconfirmed for Phase 3: doctor-originated alternate-time requests can be accepted/declined by the patient; patient favorites support list/add/remove with ownership.

# Phase 3 — current outcomes

## [x] Doctor consultation and completed visit
- Let only the doctor assigned to an eligible appointment save one consultation note (at most 4,000 characters), diagnosis (at most 1,200 characters), and treatment (at most 2,000 characters) after the scheduled appointment date and time have arrived in Asia/Kolkata; each field is required and validated on the server.
- Save consultation, appointment `COMPLETED` status, active slot-claim release, and one associated bill atomically. Reject a future visit, a visit not assigned to that doctor, or repeat completion without altering existing data.

## [x] Patient history and record ownership
- Let a signed-in patient read their own completed visit history, note, diagnosis, treatment, and related bill; never return another patient's history or clinical content by changing an ID. Patients cannot create or update doctor-authored clinical records.
- Let doctors access/record only their assigned appointments. Keep role-based sessions, Phase 1/2 endpoints, and no-store handling for authenticated history and billing responses.

## [x] Billing and printable JSP invoice
- Create one durable INR outpatient-consultation bill per completed visit, snapshotting the doctor's fee at completion. Clearly identify it as a synthetic demo invoice and state that no payment is processed; do not add payment collection or payment-confirmation functionality.
- Provide a printable `/bills/print.jsp?id={billId}` view rendered by JSP with bill number, visit date, doctor/specialty, consultation-fee line, amount/currency, demo notice, print control, and print-friendly styling. Only the patient owner, assigned doctor, or ADMIN may access it; escaping and server-side ID authorization are mandatory.

## [x] ADMIN aggregate reports
- Provide an ADMIN-only, date-filtered operational/billing report with appointment count, completed consultation count, issued invoice count/amount, and specialty totals where available. Validate dates and limit a report range to 366 days.
- Return only aggregates: no patient names, diagnosis, notes, treatment, or other clinical content.

## [x] Phase 3 implementation and local verification
- Existing role-aware doctor Consultation, patient History & Bills, and ADMIN Reports remain in the original glass UI. Added doctor prescription entry, patient prescription access/cart link, owner-authorized printable prescription JSP, and API documentation.
- Flyway V3 consultation/billing and V4 prescription schema exist for H2 and MySQL. H2 test database seeded synthetic records; V4 does not overwrite existing data.
- Java 17 Maven `clean verify` passed. JUnit covers transactional completion/bill creation, duplicate rejection, ownership, and related Phase 4/5 persistence; live HTTP tests covered role guards, owner bill/prescription JSP responses, report access and prescription creation.
- `PHASE3_REPORT.md` records local results. No Git repository was present; no branch/commit/publish/browser automation was performed.

## [x] Phase 4 implementation and local verification
- Added catalog search, patient cart, transactional stock/price validation and mock checkout, durable order items, admin-only forward order statuses/cancellation restock, and prescription-to-cart for catalog products.
- Added H2/MySQL V4 commerce tables and role-aware patient/admin dashboard sections.
- Java 17 JUnit and HTTP checks passed for catalog, persisted cart, checkout total/snapshot, cart clearing, order transitions, wrong-role access and prescription linkage.
- `PHASE4_REPORT.md` documents mock-only payment and untested live MySQL deployment limits.

## [x] Phase 5 implementation and local verification
- Added backend-only `XAI_API_KEY` xAI Grok integration with timeout/error fallback, conservative specialty suggestions, same-day queue tokens/rough waits and doctor queue status, patient reminders and health timeline.
- Added H2/MySQL V4 queue/reminder tables, role-aware dashboard sections, API endpoint documentation, user manual, and Mermaid architecture diagram.
- Local fallback/suggestion, queue lifecycle, reminders, timeline, ownership and role checks passed. Live xAI call, browser E2E, production deployment and MySQL V4 migration were not run.
- `PHASE5_REPORT.md` documents observed results and remaining gaps.

## [ ] Release verification and distribution
- Verify V4 against a MySQL instance; run browser E2E and (if a protected `XAI_API_KEY` is provisioned) a synthetic Grok request.
- No production release was published. Package the local Java 17 source/docs into `MediSlot_Phase3-5_Final.zip` after final validation.
