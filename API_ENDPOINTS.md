# MediSlot API Endpoints

All API endpoints require the same-origin `MediSlotSession` cookie except login, registration, and public health. Authenticated clinical, billing, prescription, cart, and user-specific responses use `Cache-Control: no-store`. Request/response bodies are JSON unless the path is a JSP print view.

## Authentication and health

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | Public | Start a role session |
| POST | `/api/auth/register` | Public | Register a patient account |
| POST | `/api/auth/logout` | Authenticated | Invalidate the session |
| GET | `/api/auth/me` | Authenticated | Read current account |
| GET | `/_app/health` | Public | Readiness status |

## Doctors, appointments, availability (Phases 1–2)

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| GET | `/api/doctors` | Authenticated | List doctors |
| GET | `/api/doctors/{doctorId}` | Authenticated | Read doctor detail |
| GET | `/api/doctors/{doctorId}/slots?date=YYYY-MM-DD` | Authenticated | Read available starts |
| GET, PUT | `/api/doctor/profile` | DOCTOR | Read/update own profile |
| GET | `/api/doctor/availability` | DOCTOR | List own weekly windows |
| PUT, DELETE | `/api/doctor/availability/{weekday}` | DOCTOR | Set/remove own weekly window |
| GET, POST | `/api/appointments` | PATIENT/DOCTOR for reads; PATIENT for booking | Read permitted schedule or book |
| POST, DELETE | `/api/appointments/{appointmentId}/reschedule` or `/api/appointments/{appointmentId}` | PATIENT | Reschedule/cancel own future booking |
| GET | `/api/time-requests` | Assigned PATIENT/DOCTOR | Read own participant requests |
| POST | `/api/appointments/{appointmentId}/time-requests` | Assigned PATIENT/DOCTOR | Propose a time |
| POST | `/api/time-requests/{requestId}/decision` | Other assigned participant | Accept/decline proposal |
| GET | `/api/patient/favorites` | PATIENT | Read own favorites |
| POST, DELETE | `/api/patient/favorites/{doctorId}` | PATIENT | Add/remove own favorite |

## Consultations, history, prescriptions, invoices, reports (Phase 3)

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| POST | `/api/phase3/consultations` | DOCTOR | Complete an eligible assigned visit; requires note, diagnosis and treatment; creates the visit bill atomically |
| GET | `/api/phase3/history` | PATIENT | Read own clinical history and invoices |
| GET | `/api/phase3/reports?from=YYYY-MM-DD&to=YYYY-MM-DD` | ADMIN | Aggregate counts/amounts, at most 366 inclusive days; no patient or clinical details |
| POST | `/api/phase45/prescriptions` | DOCTOR | Add a prescription to an assigned completed visit; JSON: `appointmentId`, optional `instructions`, `items[]` with `medicineId` or `medicineName`, `dosage`, `quantity` |
| GET | `/api/phase45/prescriptions` | PATIENT/DOCTOR/ADMIN | Read prescriptions visible to the current owner/assigned doctor/admin |
| GET | `/bills/print.jsp?id={billId}` | Patient owner/assigned doctor/ADMIN | Render authorized, printable demo invoice |
| GET | `/prescriptions/print.jsp?id={prescriptionId}` | Patient owner/assigned doctor/ADMIN | Render authorized, printable demo prescription |

## Pharmacy catalog and orders (Phase 4)

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| GET | `/api/phase45/catalog?q={text}` | Authenticated | Search in-stock catalog entries |
| GET | `/api/phase45/cart` | PATIENT | Read own cart, live unit prices, stock and calculated totals |
| POST | `/api/phase45/cart/items` | PATIENT | Add `{ "medicineId": number, "quantity": number }`; validates current stock |
| DELETE | `/api/phase45/cart/items/{medicineId}` | PATIENT | Remove own cart line |
| POST | `/api/phase45/cart/checkout` | PATIENT | Mock checkout; atomically locks/revalidates prices/stock, decrements stock and creates an order; no payment |
| GET | `/api/phase45/orders` | PATIENT/ADMIN | Patient sees own orders; ADMIN sees order queue/details |
| PUT | `/api/phase45/orders/{orderId}/status` | ADMIN | Move `PLACED → PACKED → READY → COMPLETED`, or cancel an eligible order; cancellation restocks atomically |
| POST | `/api/phase45/prescriptions/{prescriptionId}/cart` | PATIENT owner | Atomically add available catalog items from own prescription to cart |

## Assistant, specialty suggestions, queue, reminders and timeline (Phase 5)

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| POST | `/api/phase45/assistant` | Authenticated | JSON `{ "question": "...", "includeContext": false }`; `includeContext` defaults false. When true, share only this session user's minimized operational summary; backend calls Groq with server-only `GROQ_API_KEY` when available, otherwise local fallback. |
| POST | `/api/phase45/specialty-suggestion` | Authenticated | Return a keyword-based non-diagnostic specialty navigation suggestion |
| POST | `/api/phase45/queue` | PATIENT | Request a queue token for own booked appointment scheduled today |
| GET | `/api/phase45/queue` | PATIENT/DOCTOR | Doctor lists own waiting queue; patient may read token with `?id={tokenId}` |
| PUT | `/api/phase45/queue/{tokenId}/status` | Assigned DOCTOR | Mark `CALLED` or `SERVED` |
| GET | `/api/phase45/reminders` | PATIENT | Generate idempotent appointment reminders and list own reminders |
| PUT | `/api/phase45/reminders/{reminderId}/read` | PATIENT owner | Mark own reminder read |
| GET | `/api/phase45/timeline` | PATIENT | Read own appointment and consultation timeline |

## Error conventions

- `400` invalid input/date/quantity/status transition.
- `401` missing or expired session at authentication-protected routes.
- `403` authenticated but role is not allowed.
- `404` missing or non-visible owner-scoped record (including another user's prescription/bill).
- `409` conflicting booking/visit transition where defined by the existing Phase 1–3 API.
