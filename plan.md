# MediSlot Phase 3 — Implementation & Design Plan

## Scope and preserved baseline
Implement **Phase 3 only** in the existing MediSlot project. Do not redesign, replace, or regress Phase 1 or Phase 2. Preserve Java 17, Maven, embedded Jetty, JDBC/DAO (no ORM), app-owned email/password sessions, managed MySQL, local-only H2 test support, seeded synthetic users, role ownership checks, existing APIs, booking clash enforcement, doctor profiles/availability, patient favorites, and the current light-purple glass dashboards. The original Phase 1 and Phase 2 reports remain historical records; deliver `PHASE3_REPORT.md` using the same nine-section report format.

The Phase 2 prerequisites were checked before this plan: **(a)** doctors can propose a different appointment time and the patient (the other participant) can accept or decline; acceptance is explicit and preserves the original booking unless the new slot remains free; **(b)** patients can list/add/remove their own favorite doctors. Both are already implemented and tested; no repair or redesign is required.

Phase 3 scope from the request: consultation notes, diagnosis, treatment, patient history, billing, a printable bill rendered with JSP, and reports. Pharmacy, chatbot, queues, reminders, real payment processing, and unrelated feature redesign remain out of scope. This is a public synthetic demo: do not request or encourage real personal, patient, or medical information.

## Behavior and low-risk implementation assumptions
- **Consultation completion:** the assigned doctor may complete their own appointment only after its scheduled date and time have arrived in `Asia/Kolkata`. A single atomic operation stores the consultation note, diagnosis, and treatment, changes the appointment to `COMPLETED`, releases its active booking-slot claim, and creates one bill using the current doctor consultation fee. Repeat completion is rejected without creating a duplicate consultation or bill. Future appointments cannot be completed; patients cannot create or edit clinical notes.
- **Clinical fields:** require non-empty note (maximum 4,000 characters), diagnosis (maximum 1,200), and treatment (maximum 2,000). All values are validated server-side and HTML-escaped in browser/JSP rendering.
- **Patient history/privacy:** authenticated patients can read only their own completed consultation history and associated bill; doctors can access/record only appointments assigned to them; reports are restricted to ADMIN. Other patients' records are never exposed by an ID guess.
- **Billing:** issue one saved INR outpatient-consultation invoice per completed visit, snapshotting the doctor's displayed fee at completion. This demo issues bills only; it has no gateway, payment collection, payment confirmation, taxes, or claim that an invoice was paid. Clearly label the invoice as a synthetic demo bill and state that no payment is processed.
- **Printable JSP:** an authenticated `/bills/print.jsp?id={billId}` request is checked for patient-owner, assigned-doctor, or admin permission before forwarding to an internal `WEB-INF` JSP. Render a simple itemized outpatient consultation fee, bill number, visit date, doctor/specialty, amount/currency, demo notice, and print action; include print CSS and safe output escaping. A visitor cannot fetch another account's bill by changing the ID.
- **Reports:** provide an ADMIN-only date-filtered aggregate report (appointment count, completed consultation count, issued invoice count and amount, with specialty totals where available). Limit valid ranges to at most 366 days, reject invalid/inverted dates, and never include patient names, diagnoses, consultation notes, treatment, or other clinical detail in aggregate responses.
- **Demo records:** add a one-time, clearly synthetic past consultation/bill example for the seeded demo patient/doctor so history and printable invoice screens are demonstrable immediately. Guard the seed with an idempotent marker and a free-slot/date check; do not overwrite existing appointments, profiles, user edits, or history.

## Implementation and data flow
Continue the existing same-origin, relative-URL client and servlet/service/DAO architecture. Add patient History & Bills, doctor Consultations, and admin Reports sections/links inside the existing dashboard. Keep the current sidebar, spacing, glass cards, typography, violet actions, mobile stacking, Phase 1/2 content, and public-demo warning; only add fields, cards, actions, and a Phase 3 status label as needed.

Add a forward-only Flyway V3 migration in both `backend/src/main/resources/db/migration/mysql/` and `.../h2/` for consultations and bill records, with foreign keys, one-consultation/one-bill uniqueness and patient/doctor/date indexes. Leave existing Phase 1/2 tables/data intact. Implement transactional JDBC DAO operations that lock/validate the assigned appointment, create one consultation and bill, set completion state, and release the slot claim together. Add a `Phase3Service` for field/date/range validation and `Phase3Servlet` endpoints for consultation creation, patient history/bills, printable-bill data, and admin aggregate reports. Add a dedicated secured JSP-print servlet and a JSP template under `frontend/WEB-INF/jsp/`; add only the Jetty 11 JSP/JSTL dependencies actually required by the pinned embedded Jetty version. Preserve no-store behavior for authenticated clinical/billing responses.

### Project structure and responsibility
- `backend/src/main/java/edu/alliance/medislot/dao/` — existing DAOs plus JDBC persistence/transactions for consultations, bills, and aggregates.
- `backend/src/main/java/edu/alliance/medislot/service/Phase3Service.java` — validation and role/business rules for Phase 3; reuse existing appointment/user models and DAOs.
- `backend/src/main/java/edu/alliance/medislot/servlet/Phase3Servlet.java` and `BillPrintServlet.java` — same-origin JSON API and authorized JSP print request; extend Jetty startup/filter mappings additively.
- `backend/src/main/resources/db/migration/{mysql,h2}/V3__phase3_consultations_billing.sql` — dialect-specific additive schema.
- `frontend/index.html`, `frontend/js/api.js`, `frontend/js/home.js`, and `frontend/css/style.css` — role-aware dashboard cards/actions, patient history/bills, doctor consultation entry, admin summaries, and print styling.
- `frontend/WEB-INF/jsp/bill.jsp` — server-rendered, access-controlled printable invoice.
- `frontend/manus-routes.json` — retain `/` and `/login.html`; add the printable-bill route.
- `Dockerfile`, `backend/pom.xml`, `README.md`, `TODO.md`, and `PHASE3_REPORT.md` — runtime JSP packaging, project documentation, outcome tracking, and final report.

## Design system — preserve Phase 1/2 visual language
- **Design Movement:** contemporary soft-glass SaaS with calm healthcare-product clarity; continue the approved Phase 1/2 system rather than starting a redesign.
- **Core Principles:** reassuring hierarchy, role clarity, privacy by default, and restrained responsive utility.
- **Color Philosophy:** retain warm near-white, dark-plum ink, muted lilac-gray, pale-lavender surfaces, and violet `#7865C8` actions. Use semantic green for saved/issued confirmations and muted rose for validation errors only.
- **Layout Paradigm:** preserve the persistent sidebar plus spacious dashboard canvas; stack existing glass panels and forms on mobile. Printable invoices use a separate clean white, ink-friendly document layout.
- **Signature Elements:** rounded translucent white cards with fine borders/shadows; existing violet pills and role-aware navigation; subtle clinic-style invoice header and thin itemized-fee rule.
- **Interaction Philosophy:** familiar, plain-language forms; explicit saved/completed/issued states; no hidden posting or payment action; confirmation feedback distinguishes draft, completion, history, and printable invoice.
- **Animation:** retain only the current short hover/focus transitions and reduced-motion rules; keep clinical forms, reports, and the print view still.
- **Typography System:** preserve DM Sans for body/forms and Manrope for headings/wordmark with current system fallbacks; use readable tabular amounts/date alignment on the invoice.
- **Brand Essence:** “A calm, clear place to find care.” **Adjectives:** reassuring, considered, accessible.
- **Brand Voice:** calm and precise; “Visit complete. Your note and bill are ready.” / “Demo invoice only — no payment is processed.”
- **Wordmark & Logo:** retain the current MediSlot wordmark and simple M/appointment-slot mark; do not rebrand.
- **Signature Brand Color:** preserve violet `#7865C8` as the ownable brand color.

## Phase 3 product outcomes
1. Assigned doctors can create one consultation per eligible appointment with required note, diagnosis, and treatment; save atomically with completed status and the related INR bill.
2. Patients can review their own consultation history and related issued bills only; patients cannot create or edit clinical content or access another patient's records.
3. An authorized patient/assigned doctor/admin can open a guarded, printable JSP bill; browser print is supported and bill-ID changes do not bypass ownership.
4. ADMIN users can retrieve date-limited aggregate operational/billing reports that do not reveal patient identity or clinical content.
5. The Phase 3 dashboard adds the three roles' relevant sections without redesigning Phases 1–2; the API and UI remain functional through the same public demo.

## Verification and release
Before application-code edits, verify host-managed JavaScript/HTML/CSS diagnostics using `webdev.config` GET `runtime/post-edit`, registering supported frontend languages if missing. Run a clean Java 17 Maven build and route-manifest/JavaScript checks. Run API curl tests and the required browser check against isolated local H2, not the shared managed MySQL: doctor creation, note/diagnosis/treatment validation, future-visit rejection, idempotent completion, atomic bill amount/status, owner and role restrictions, patient history, report date filters/no-PII shape, JSP print rendering/escaping, and Phase 1/2 regressions (favorite doctors, counter-proposal/accept/decline, slot-clash behavior). Capture local patient, doctor, report, and print-view screenshots. Keep production verification read-only after publication; the approved additive Flyway migration and guarded synthetic seed execute at app startup. Commit all Phase 3 changes on canonical `main`, confirm the checkpoint, explicitly publish to `https://medislot-kh4pjyqg.manus.space`, then finalize `PHASE3_REPORT.md` with the same numbered report sections 1–9 and actual pass/fail evidence.
