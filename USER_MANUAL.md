# MediSlot User Manual

> Academic demonstration only. Use synthetic demo accounts and data. Never enter real personal, patient, or medical information. Prescriptions, consultation records, assistant text, invoices, orders, queue estimates and reminders are demonstrations—not professional healthcare or commerce services.

## Start the application

Prerequisites: Java 25 LTS and Maven. From the project root:

```sh
MEDISLOT_DB=h2 MEDISLOT_H2_PATH="$PWD/backend/data/medislot" MEDISLOT_SECURE_COOKIES=false PORT=3000 mvn -f backend/pom.xml clean verify exec:java
```

Open http://localhost:3000/login.html. Local H2 mode is an explicitly selected persistent file database. For plain local HTTP only, the command sets `MEDISLOT_SECURE_COOKIES=false`. Remove that override behind HTTPS so secure cookies are used. The default page URL is http://localhost:3000/.

## Demo accounts

Password for each synthetic account: `medislot123`.

- Patient: `patient1@medislot.local`
- Doctor: `aarav.doctor@medislot.local`
- Admin: `admin@medislot.local`

## Patient tasks

1. Sign in and browse doctor profiles; use appointment availability to book or manage eligible appointments.
2. Use **History & bills** to review your own completed consultation and invoice. Open the bill print view from the invoice link.
3. Use **Pharmacy** to search the demo catalog, add in-stock items to the cart and place a mock order. The backend calculates current price and verifies stock; checkout does not collect payment.
4. View your prescriptions and use the print link. Prescribed catalog items can be copied into your cart; free-text prescription lines are for demonstration and cannot be ordered from catalog inventory.
5. In **Care tools**, request a non-diagnostic specialty navigation suggestion, read the assistant disclaimer, request a token for a booked appointment scheduled today, mark reminders read and browse your health timeline.
6. Queue wait estimates are simple queue-count estimates, not a clinic's live measurements.

## Doctor tasks

1. Review your assigned schedule and complete an eligible past/current visit with a note, diagnosis and treatment.
2. The completion operation saves the consultation, appointment state, released booking claim and demo INR bill atomically.
3. Select a completed assigned appointment in **Prescriptions**, add an in-stock catalog medicine and dosage, and save. Print authorization permits only the patient owner, assigned doctor or admin.
4. See today's waiting queue and move a token from `WAITING` to `CALLED` or `SERVED`.

## Admin tasks

1. View date-filtered aggregate appointment/consultation/billing reports; the report excludes patient-level and clinical data.
2. Manage mock pharmacy order status in order: `PLACED → PACKED → READY → COMPLETED`, or cancel an eligible order. Cancellation restores catalog inventory. No payment or fulfillment is implemented.

## Optional Groq configuration

Set `GROQ_API_KEY` in the **backend runtime environment** before launching the app; the Java service calls Groq's OpenAI-compatible chat completions API. Never put the credential in frontend assets, a URL, logs, or a committed file. Without a key (or if Groq times out or returns an error), the app uses its local navigation and safety fallback. The floating assistant is enabled for signed-in users and provides shortcuts appropriate to their role.

The assistant sends each typed message to Groq when Groq is configured. The optional “Include my minimized MediSlot summary” checkbox is off by default. When checked, server-side queries are scoped to the current session user and share only operational booking, favorite, time-request, mock-order, reminder, and activity summaries; diagnoses, consultation notes, treatment, prescription content, and medicine names are excluded. Use synthetic demo data only. Assistant output is not a diagnosis, prescription, or medical advice; use local emergency services for urgent symptoms.

The light/dark theme toggle works on sign-in, dashboard, bill, and prescription pages; the preference persists in local browser storage.

## Data and API documentation

- Endpoint catalog and role requirements: [API_ENDPOINTS.md](API_ENDPOINTS.md)
- Component and persistence layout: [ARCHITECTURE.md](ARCHITECTURE.md)
- Build/test and feature evidence: [PHASE3_REPORT.md](PHASE3_REPORT.md), [PHASE4_REPORT.md](PHASE4_REPORT.md), and [PHASE5_REPORT.md](PHASE5_REPORT.md)
