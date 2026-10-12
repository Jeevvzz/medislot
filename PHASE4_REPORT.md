# PHASE REPORT 4 — Demo Pharmacy and Orders

## 1. Run command and URL (one line each)
- **Run:** `MEDISLOT_DB=h2 MEDISLOT_H2_PATH="$PWD/backend/data/medislot" MEDISLOT_SECURE_COOKIES=false PORT=3000 mvn -f backend/pom.xml clean verify exec:java` from the project root.
- **URL:** Local Java 17 demo: http://localhost:3000/. The Phase 4 changes were not deployed to the historical public demo.

> Runtime target is restored to Java 17; Jackson Databind 2.18.11 is retained from the previous dependency security fix.

## 2. Demo logins (patient, doctor, admin)
Password: `medislot123`.
- Patient: `patient1@medislot.local`
- Doctor: `aarav.doctor@medislot.local`
- Admin: `admin@medislot.local`

## 3. Files and folders created or extended
- `backend/src/main/resources/db/migration/{h2,mysql}/V4__phase4_phase5.sql` — catalog cart/order persistence schema additions.
- `backend/src/main/java/edu/alliance/medislot/dao/Phase45Dao.java` — catalog, cart, order lines, lock-and-check checkout, status transitions and restock-on-cancel.
- `backend/src/main/java/edu/alliance/medislot/service/Phase45Service.java` — quantity/id validation.
- `backend/src/main/java/edu/alliance/medislot/servlet/Phase45Servlet.java` — role-protected catalog/cart/checkout/order APIs.
- `frontend/index.html`, `frontend/js/phase45.js`, `frontend/js/home.js` — patient pharmacy UI, search, cart, checkout/order history and admin order queue.
- `API_ENDPOINTS.md`, `USER_MANUAL.md`, `ARCHITECTURE.md` — endpoint and usage documentation.

## 4. API endpoints added (method, path, purpose, role)
- `GET /api/phase45/catalog?q={text}` — authenticated catalog search, available stock only.
- `GET /api/phase45/cart`, `POST /api/phase45/cart/items`, `DELETE /api/phase45/cart/items/{medicineId}` — patient-owned cart.
- `POST /api/phase45/cart/checkout` — patient mock checkout; database prices are authoritative, stock is locked/revalidated, inventory decrement/order persistence are transactional.
- `GET /api/phase45/orders` — PATIENT sees own orders; ADMIN sees all.
- `PUT /api/phase45/orders/{orderId}/status` — ADMIN-only `PLACED → PACKED → READY → COMPLETED` or eligible cancellation. Cancellation returns inventory atomically.
- `POST /api/phase45/prescriptions/{prescriptionId}/cart` — PATIENT owner adds available prescribed catalog items atomically; free-text items are intentionally excluded from cart.

## 5. DB tables added or changed
- Existing V1 `medicines` inventory table is now used by working catalog/search and stock validation.
- V4 `patient_carts`, `cart_items`, `medicine_orders`, and `medicine_order_items` store patient carts and immutable checkout price snapshots.
- H2 and MySQL migration scripts exist. Fresh H2 Flyway migration V4 ran successfully; no live MySQL migration was run.

## 6. Java concepts used
- **Prepared statements/JDBC DAO:** search and all cart/order persistence use parameterized JDBC queries.
- **Transactions and locks:** checkout reads and locks selected medicine rows, recomputes totals from database price, checks available quantities, decrements stock and persists order/lines as one transaction.
- **State machine:** admins may only progress valid order states. Duplicate/reverse transitions are rejected; cancellation re-stocks once.
- **Role/ownership checks:** patients can only address their own cart/orders; admin-only status changes.

## 7. Tests done (pass/fail list, curl/API results)
- **PASS** — Java 17 `mvn -f backend/pom.xml clean verify` with 4/4 automated tests passing.
- **PASS** — live catalog search returned Paracetamol with server-returned price/stock; adding two units yielded ₹70 at the stored ₹35 unit price.
- **PASS** — live mock checkout returned a persisted `PLACED` order with item snapshot, and cart became empty.
- **PASS** — admin order list/status API and patient/admin authorization checked via HTTP. `PLACED → PACKED` returned 200; invalid direct `PACKED → COMPLETED` returned 400.
- **PASS** — JUnit H2 integration test verifies checkout total and line items, rejects stock-over-limit add, and validates valid/invalid status transitions. Cancellation stock restoration is covered by the test suite after the final build.
- **PASS** — prescription-to-cart API returned 200 for the owner and persisted cart contents.

## 8. Known bugs or gaps
- Checkout is explicitly mock-only: no payment gateway, payment collection, real fulfillment, shipping, or dispensing.
- Free-text prescribed medicines are printable but are not purchasable from the inventory cart. Only catalog-linked items are transferred.
- Catalog has synthetic seeded inventory and no admin catalog CRUD UI; no real pharmacy integration exists.
- MySQL DDL was not executed against a live MySQL service in this local task; the V4 MySQL script is included but H2 was the tested engine.

## 9. What Phase 5 does
Phase 5 supplies optional backend-only Grok navigation assistance with a safe local fallback, specialty suggestions, same-day queue tokens/rough wait estimates, patient reminders and a patient-owned timeline. See `PHASE5_REPORT.md` for live verification and limitations.
