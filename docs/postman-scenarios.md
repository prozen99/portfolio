### Postman Test Guide — TimeDeal Pro (Scenarios 1–5)

Base URL
- Local: `http://localhost:8080`

Prerequisites
- Start the application: it uses your local MySQL per `application.properties`.
- Prepare seed data in DB (Users/Items/Coupons). You can insert via SQL or create API(s) if you added them. Below, we provide example payloads assuming minimal required records exist.

Notes
- Error responses follow a common schema:
```json
{
  "timestamp": "2026-01-14T00:00:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "재고가 부족합니다.",
  "path": "/api/orders"
}
```
- When testing payments: the default `VirtualPaymentGateway` randomly fails at ~10% probability. For deterministic local tests, retry once if you hit a random failure.

---

Scenario 1 — High Concurrency Stock Deduction
Goal: Only up to stock size orders succeed; excess requests must fail with 409 and stock must not go negative.

Minimal Setup (SQL example)
```sql
-- user
insert into users(id, email, name, created_at, updated_at) values (1, 'load@test.com', 'LoadUser', now(), now());
-- category
insert into category(id, name, created_at, updated_at) values (10, 'Root', now(), now());
-- item with stock 10
insert into item(id, name, price, stock, status, category_id, created_at, updated_at)
values (100, 'Flash Item', 1000, 10, 'AVAILABLE', 10, now(), now());
```

Postman Runner Tips
- Use Collection Runner with 100 iterations calling `POST /api/orders` below. Only ~10 should return 201.

Request
POST `/api/orders`
```json
{
  "userId": 1,
  "itemId": 100,
  "quantity": 1,
  "clientPayAmount": 1000
}
```
Expected Responses
- Success: 201 Created
```json
{"orderId": 123, "orderStatus": "PAID", "paymentStatus": "SUCCESS"}
```
- Failure (oversell): 409 Conflict with message like "재고가 부족합니다."

---

Scenario 2 — Discount Strategy (Fixed/Rate)
Goal: Verify correct discount application based on coupon type.

Setup (SQL example)
```sql
-- coupons
insert into coupon(id, name, type, discount_value, created_at, updated_at)
values (201, 'Fixed1000', 'FIXED', 1000, now(), now());
insert into coupon(id, name, type, discount_value, created_at, updated_at)
values (202, 'Rate10', 'RATE', 10, now(), now());
-- issue to user 1
insert into user_coupon(id, user_id, coupon_id, used, created_at, updated_at)
values (301, 1, 201, false, now(), now());
insert into user_coupon(id, user_id, coupon_id, used, created_at, updated_at)
values (302, 1, 202, false, now(), now());
-- item 10,000 KRW
insert into item(id, name, price, stock, status, category_id, created_at, updated_at)
values (101, 'Discount Item', 10000, 5, 'AVAILABLE', 10, now(), now());
```

Request (Fixed 1000)
POST `/api/orders`
```json
{ "userId": 1, "itemId": 101, "quantity": 1, "userCouponId": 301, "clientPayAmount": 9000 }
```
Expected: 201; payment `amount` should be 9000 (can confirm by `GET /api/orders/{id}`)

Request (Rate 10%)
POST `/api/orders`
```json
{ "userId": 1, "itemId": 101, "quantity": 1, "userCouponId": 302, "clientPayAmount": 9000 }
```
Expected: 201; payment `amount` 9000

---

Scenario 3 — Dynamic Search (QueryDSL)
Goal: Filter by category, price range, status.

Requests
- GET `/api/items?categoryId=10&minPrice=2000&maxPrice=20000&status=AVAILABLE&page=0&size=10`
- GET `/api/categories/10/items?page=0&size=10`

Expected
- Items within category 10 (and descendants for the second API) whose price in [2000, 20000] and status `AVAILABLE`.

---

Scenario 4 — Fetch-Join Reads
Goal: Retrieve order detail and user orders without N+1.

Requests
- GET `/api/orders/{orderId}`
- GET `/api/users/1/orders?page=0&size=20`

Expected
- Order detail includes embedded User/Item/Payment summary.
- Paged list for the user.

---

Scenario 5 — Payment Flow and Tampering Guard
Goal: Validate payment success, random failure, and price tampering defense.

Happy Path
POST `/api/orders`
```json
{ "userId": 1, "itemId": 100, "quantity": 1, "clientPayAmount": 1000 }
```
- Expected: 201 with `paymentStatus":"SUCCESS"`

Random Failure (10%)
- Same request; if 409 with message like `VIRTUAL_GATEWAY_RANDOM_FAIL` occurs, this is expected; retry once.

Tampering (Client amount ≠ server computed)
POST `/api/orders`
```json
{ "userId": 1, "itemId": 100, "quantity": 1, "clientPayAmount": 999 }
```
Expected: 400 Bad Request with message similar to "결제 금액이 위변조되었습니다" (exact message may vary if localized).

Approved Amount Mismatch (Gateway returns different amount)
- This case is simulated in automated tests; with the default `VirtualPaymentGateway`, it won’t occur. If you inject a test gateway, mismatch should cause failure and rollback.

---

Troubleshooting
- 404 Not Found: Ensure `userId`, `itemId`, and optional `userCouponId` exist in DB.
- 409 Conflict: For scenario 1 under load, this is expected beyond stock size. For scenario 5, gateway random failure can produce 409 as well.
- 400 Bad Request: Validation errors (quantity < 1), invalid coupon usage, or client/server amount mismatch.
