# PHASE REPORT 5 — Care Navigation, Queue and Reminders

## 1. Run command and URL (one line each)
- **Run:** `MEDISLOT_DB=h2 MEDISLOT_H2_PATH="$PWD/backend/data/medislot" MEDISLOT_SECURE_COOKIES=false PORT=3000 mvn -f backend/pom.xml clean verify exec:java` from the project root. For optional Grok calls, supply `XAI_API_KEY` only to the backend runtime environment.
- **URL:** Local Java 17 demo: http://localhost:3000/. No Phase 5 deployment was published.

> The runtime/compiler target has been restored to Java 17. The compatible Jackson Databind 2.18.11 security update was retained and the final direct-dependency CVE scan found no known CVEs requiring fixes.

## 2. Demo logins (patient, doctor, admin)
Password: `medislot123`.
- Patient: `patient1@medislot.local`
- Doctor: `aarav.doctor@medislot.local`
- Admin: `admin@medislot.local`

## 3. Files and folders created or extended
- `backend/src/main/java/edu/alliance/medislot/service/Phase45Service.java` — backend-only Grok request, friendly fallback and specialty keyword routing.
- `backend/src/main/java/edu/alliance/medislot/dao/Phase45Dao.java` — queue token, wait estimate, reminder and patient timeline JDBC operations.
- `backend/src/main/java/edu/alliance/medislot/servlet/Phase45Servlet.java` — role-protected assistant, specialty, queue, reminder and timeline routes.
- `backend/src/main/resources/db/migration/{h2,mysql}/V4__phase4_phase5.sql` — `queue_tokens` and `in_app_reminders` tables and indexes.
- `frontend/index.html`, `frontend/js/phase45.js`, `frontend/js/home.js` — care navigation, assistant, queue, reminders and timeline UI.
- `README.md`, `USER_MANUAL.md`, `API_ENDPOINTS.md`, `ARCHITECTURE.md` — setup, safety, API and architecture documentation.

## 4. API endpoints added (method, path, purpose, role)
- `POST /api/phase45/assistant` — signed-in user sends a question; server calls `https://api.x.ai/v1/chat/completions` using backend environment `XAI_API_KEY` and the `grok-3-mini` model. Timeout/provider errors or absent key use a friendly local fallback. UI/API responses include a non-diagnostic disclaimer.
- `POST /api/phase45/specialty-suggestion` — authenticated keyword-based navigation suggestion; not a diagnosis.
- `POST /api/phase45/queue` — PATIENT requests a unique token for their own booked appointment dated today.
- `GET /api/phase45/queue` — PATIENT may retrieve a token using `?id=...`; DOCTOR sees own waiting list.
- `PUT /api/phase45/queue/{tokenId}/status` — assigned DOCTOR sets `CALLED` or `SERVED`.
- `GET /api/phase45/reminders`, `PUT /api/phase45/reminders/{reminderId}/read` — PATIENT-only own reminders.
- `GET /api/phase45/timeline` — PATIENT-only timeline for own appointments and consultations.

## 5. DB tables added or changed
- V4 `queue_tokens`: unique token per appointment, patient/doctor ownership, waiting status and issue time.
- V4 `in_app_reminders`: appointment-linked patient reminder records with unique user/appointment/type key and read timestamp.
- The timeline is a patient-scoped projection over existing `appointments` and `consultations`, not a separate duplicate event store.
- Fresh H2 applied V1–V4; MySQL V4 was not tested against a live database.

## 6. Java concepts used
- **Backend-only integration:** Java `HttpURLConnection` posts JSON to xAI; API key is read from `System.getenv("XAI_API_KEY")` and never sent to the browser.
- **Resilience:** connection/read timeouts; absent key, error HTTP response or exception routes to local fallback.
- **Persistence:** prepared SQL and unique constraints enforce one token per appointment and idempotent reminder generation.
- **Authorization:** queue, reminders and timeline queries bind results to patient/doctor account IDs; role checks are repeated in the servlet.
- **Time estimate:** waiting people ahead × 10 minutes. This is a simple demo estimate, not a real-time clinic metric.

## 7. Tests done (pass/fail list, curl/API results)
- **PASS** — Java 17 `mvn -f backend/pom.xml clean verify`; final automated suite contains 4 tests (3 suggestion unit tests and 1 multi-workflow H2 DAO integration test), all passing.
- **PASS** — HTTP specialty suggestion `itchy rash` returned Dermatology with non-diagnostic disclaimer.
- **PASS** — with no configured `XAI_API_KEY`, HTTP assistant request returned `provider=local-fallback` and safety disclaimer.
- **PASS** — patient same-day booking → queue token returned `201` and token `Q####`; doctor list displayed it and `CALLED` update returned `200`.
- **PASS** — patient reminder list and mark-read API returned `200`; patient timeline returned `200` with events.
- **PASS** — doctor/admin/patient role flows and patient/admin/doctor cross-access checks were exercised by the end-to-end curl-compatible HTTP run.
- **NOT RUN** — live Grok provider call, browser automation, and live MySQL V4 migration.

## 8. Known bugs or gaps
- xAI connectivity/model response is **not claimed as tested** because no `XAI_API_KEY` was available. Only the backend code path and local fallback were validated. Check current xAI model availability and provision the key in a protected runtime secret store before enabling live calls.
- Keyword-based specialty mapping is intentionally simplistic and must not be relied on for diagnosis or triage. It contains emergency disclaimers but is not a medical decision support system.
- Queue estimate is a rough constant model (10 minutes per waiting token), not computed from clinic/doctor telemetry; tokens are supported only for booked appointments dated today.
- Reminders are generated idempotently when a patient reads the reminders endpoint; there is no background scheduler, push delivery, email or SMS.
- MySQL and production UI behavior were not verified or deployed as part of this task.

## 9. Future work
Only after academic review and appropriate privacy/security approval: validate live xAI integration with a non-sensitive synthetic prompt; add scheduled notification delivery; calibrate queue estimates against real operational data; add full browser E2E and MySQL migration tests. Do not use real patient information in this demo.
