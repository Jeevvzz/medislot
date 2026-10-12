# MediSlot Java 25 Academic Demo — Phases 1–5

MediSlot is a **public university-project demonstration only**. Never enter real personal, patient, or medical information. It uses Java 25 LTS, Maven, embedded Jetty/Servlets/JSP, JDBC/DAO, Flyway and a persistent local H2 file database (with a MySQL deployment adapter). It retains the existing login/session roles, doctor profiles, availability, favorites, booking and time-request workflows, with a responsive purple dimensional-glass interface and persistent light/dark themes. Phases 3–5 add demo-only consultations, prescriptions, billing, a mock medicine storefront, care navigation, the Groq-powered optional assistant with a local fallback, queue tokens, reminders and a patient timeline.

**Earlier public demo:** https://medislot-kh4pjyqg.manus.space (the hosted site remains the earlier Phase 2 release; local Phase 3–5 changes in this workspace have not been published).

Invoices and medicine orders are synthetic. No payment method, gateway, payment confirmation, medicine dispensing, insurance claim, or real clinical workflow is implemented. Prescription and assistant output is not medical advice.

## Local development (isolated H2 only)

Prerequisites: Java 25 LTS and Maven. From this repository root:

```sh
MEDISLOT_DB=h2 MEDISLOT_H2_PATH=/tmp/medislot-phase3 MEDISLOT_SECURE_COOKIES=false PORT=3000 mvn -f backend/pom.xml clean compile exec:java
```

Open `http://localhost:3000/login.html`. `MEDISLOT_DB=h2` explicitly selects a local file database; `MEDISLOT_H2_PATH` optionally sets the database file prefix. Set `MEDISLOT_SECURE_COOKIES=false` only for plain-HTTP local testing. Never point local write/ownership tests at the hosted managed database.

### Optional Groq assistant

The assistant works without Groq using a local navigation/safety fallback. To enable Groq, create/provision a Groq API key and inject it as `GROQ_API_KEY` into the **Java backend process** before starting Maven. In a terminal, load it through your secure secret manager or shell prompt and export the variable, then run the command above. For a zsh interactive prompt, for example: `read -s 'GROQ_API_KEY?Optional Groq key: '; export GROQ_API_KEY` (press Enter without a value to use fallback). Do not put a real key in HTML, JavaScript, a URL, a checked-in file, or a command committed to shell history. The backend sends the user's message to Groq when a key is configured. Sharing MediSlot activity is a separate, unchecked-by-default per-message option; it sends only a minimized summary of that signed-in account's operational activity and omits diagnoses, consultation notes, treatment, prescription contents, patient identifiers, and medicine names. If the key is absent, the request fails, or Groq is unavailable, a local fallback remains available.

The assistant provides navigation and reminders only; it does not diagnose, prescribe, or replace a clinician. Do not enter real health or identifying information. Groq is an external service, so use synthetic demo messages only. The optional key name is listed in [.env.example](.env.example), which intentionally contains no credential. This app does not automatically load `.env`; configure the backend environment via the deployment secret store or shell. Theme preference is stored locally in the browser.

## Managed runtime

The hosted container uses the runtime-provided `DATABASE_URL` with MySQL Connector/J and Flyway; it does not silently fall back to H2. Migrations V1–V4 are additive. Database credentials remain runtime-only. Hosted session cookies remain `Secure; HttpOnly; SameSite=None` for the HTTPS Preview iframe. JSP invoice and prescription pages are rendered by the embedded Jetty/Jasper runtime.

## Demo accounts

All demo accounts use password `medislot123`:

- Patient: `patient1@medislot.local`
- Doctor: `aarav.doctor@medislot.local`
- Admin: `admin@medislot.local`

The seeded patient has a clearly labelled synthetic sample history and invoice. Other seeded appointments remain available for booking/scheduling demos.

## API inventory

### Phase 1

- `POST /api/auth/login` — authenticate and start a session (public)
- `POST /api/auth/register` — create a patient account (public)
- `POST /api/auth/logout` — invalidate the current session (authenticated)
- `GET /api/auth/me` — current account (authenticated)
- `GET /api/doctors` — list seeded doctors (authenticated)
- `GET /_app/health` — minimal deployment readiness response (public)

### Phase 2

- `GET /api/doctors/{doctorId}` and `GET /api/doctors/{doctorId}/slots?date=YYYY-MM-DD` — doctor profile and available starts
- `GET|PUT /api/doctor/profile` and `GET|PUT|DELETE /api/doctor/availability/{weekday}` — doctor-owned profile and weekly availability
- `GET /api/patient/favorites`, `POST /api/patient/favorites/{doctorId}`, `DELETE /api/patient/favorites/{doctorId}` — patient-owned favorites
- `GET|POST /api/appointments`, `POST /api/appointments/{appointmentId}/reschedule`, `DELETE /api/appointments/{appointmentId}` — patient bookings
- `GET /api/time-requests`, `POST /api/appointments/{appointmentId}/time-requests`, `POST /api/time-requests/{requestId}/decision` — patient/doctor time proposals and decisions

Appointment starts are 30-minute intervals within a doctor's declared weekday window (demo availability Monday–Friday, 09:00–17:00 Asia/Kolkata). A doctor/start-time can have only one active booking; if another patient claims a displayed slot first, the second request gets HTTP 409: “That time was just booked. Please choose another available time.”

### Phase 3

- `POST /api/phase3/consultations` — assigned doctor completes an already-started booked visit with required note, diagnosis, and treatment; the same transaction completes the appointment, releases its active slot claim, and issues one INR demo bill.
- `GET /api/phase3/history` — patient-owned consultation history, diagnosis/treatment, and related bill summaries (patient role only).
- `GET /api/phase3/reports?from=YYYY-MM-DD&to=YYYY-MM-DD` — ADMIN-only aggregate appointment/consultation/invoice report; range is limited to 366 inclusive days and clinical or patient-level content is not returned.
- `GET /bills/print.jsp?id={billId}` — printable, owner-authorized JSP invoice (patient owner, assigned doctor, or ADMIN); uses browser print and is labelled demo-only.
- `POST /api/phase45/prescriptions` — assigned DOCTOR records a prescription against a completed assigned appointment (`appointmentId`, instructions, and an `items` array containing catalog `medicineId` or text name, dosage and quantity).
- `GET /api/phase45/prescriptions` — PATIENT gets only their own prescriptions; DOCTOR gets only their own prescriptions; ADMIN can review all.
- `GET /prescriptions/print.jsp?id={prescriptionId}` — owner/assigned-doctor/admin-authorized printable demo JSP.

### Phase 4

- `GET /api/phase45/catalog?q={query}` — signed-in catalog search; only stocked items are returned.
- `GET /api/phase45/cart`, `POST /api/phase45/cart/items`, `DELETE /api/phase45/cart/items/{medicineId}` — PATIENT-only cart operations.
- `POST /api/phase45/cart/checkout` — PATIENT-only mock checkout. Server reads current catalog prices, locks stock, decrements quantities and persists the order atomically; no payment is taken.
- `GET /api/phase45/orders` — PATIENT sees their orders, ADMIN sees all order records and line items.
- `PUT /api/phase45/orders/{orderId}/status` — ADMIN-only sequential `PLACED → PACKED → READY → COMPLETED` status or cancellation.
- `POST /api/phase45/prescriptions/{prescriptionId}/cart` — PATIENT atomically adds available catalog items on their prescription to their cart; clinician-entered free-text items are not auto-added.

### Phase 5

- `POST /api/phase45/assistant` — authenticated user asks a short navigation question; backend calls Groq using server-only `GROQ_API_KEY` when present, otherwise uses the safe local fallback. `includeContext` is optional and defaults to false; when true, only the signed-in user's minimized operational summary is used. Responses contain role-limited navigation shortcuts and a healthcare safety disclaimer.
- `POST /api/phase45/specialty-suggestion` — conservative keyword-to-specialty navigation hint; it does not diagnose.
- `POST /api/phase45/queue` — PATIENT requests a unique token for their booked appointment scheduled today.
- `GET /api/phase45/queue` — DOCTOR sees their waiting tokens and approximate wait; `PUT /api/phase45/queue/{tokenId}/status` marks a waiting token `CALLED` or `SERVED`.
- `GET /api/phase45/reminders`, `PUT /api/phase45/reminders/{reminderId}/read` — PATIENT in-app appointment reminders; created idempotently for their booked appointments.
- `GET /api/phase45/timeline` — PATIENT-only event history for their own appointments and visits.

The test suite uses a separate temporary H2 file configured through test-only JVM properties. Application local data remains an H2 **file** database.

## Scope and safety

The public warning is shown throughout the app: use only the synthetic sample accounts; do not enter real personal, patient, or medical information. The storefront, order statuses, prescription, reminders, assistant, queue and reports are demonstration features only. Without `GROQ_API_KEY`, the assistant uses its local fallback; a live Groq response requires a configured key and provider availability.
