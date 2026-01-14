### TimeDeal Pro REST API Spec (Scenarios 1–5)

#### Base URL
- Local: `http://localhost:8080`

---

### 1) Items — Dynamic Search (Scenario 3)
GET `/api/items`
- Query Params
  - `categoryId` (Long, optional)
  - `minPrice` (Long, optional)
  - `maxPrice` (Long, optional)
  - `status` (String, optional: `AVAILABLE|INACTIVE`)
  - `page` (int, default 0)
  - `size` (int, default 20)
  - `sort` (String, default `createdAt,desc`) — if property exists; otherwise falls back to id desc
- Response: `PageResponse<ItemResponse>`
```json
{
  "content": [
    {"id": 1, "name": "Item A", "price": 1200, "stock": 5, "status": "AVAILABLE", "categoryId": 10, "categoryName": "Electronics"}
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

GET `/api/categories/{categoryId}/items`
- Description: Include descendant categories recursively, then search items in all of them.
- Query Params: `page`, `size`
- Response: `PageResponse<ItemResponse>` (same as above)

---

### 2) Orders — Create (Scenarios 1,2,5)
POST `/api/orders`
- Body
```json
{
  "userId": 1,
  "itemId": 2,
  "quantity": 1,
  "userCouponId": 3,
  "clientPayAmount": 9000
}
```
- Behavior
  - Scenario 1: Item read with PESSIMISTIC_WRITE lock, validate and decrease stock atomically.
  - Scenario 2: Apply discount policy via strategy based on coupon type (fixed/rate).
  - Scenario 5: Call virtual payment gateway (10% random failure). If failure, rollback so stock restores; also verify approved amount.
  - Price tampering validation against server-side computed final amount.
- Responses
  - 201 Created
```json
{"orderId": 100, "orderStatus": "PAID", "paymentStatus": "SUCCESS"}
```
  - For free orders (final amount 0): `paymentStatus` can be `null` and `orderStatus` becomes `PAID` immediately.
- Error Codes
  - 400 Bad Request: invalid args, invalid coupon, price tampering
  - 404 Not Found: user/item/coupon not found
  - 409 Conflict: insufficient stock, payment failed
  - 500 Internal Server Error: others

---

### 3) Orders — Read (Scenario 4)
GET `/api/orders/{orderId}`
- Description: Fetch join Order+User+Item+Payment to avoid N+1.
- Response `OrderDetailResponse`
```json
{
  "orderId": 100,
  "orderStatus": "PAID",
  "quantity": 1,
  "totalPrice": 9000,
  "userId": 1,
  "userName": "Alice",
  "itemId": 2,
  "itemName": "Keyboard",
  "itemPrice": 10000,
  "paymentStatus": "SUCCESS",
  "paymentId": 55,
  "paymentAmount": 9000
}
```

GET `/api/users/{userId}/orders`
- Query Params: `page`, `size`
- Response: `PageResponse<OrderDetailResponse>` (server currently performs fetch join then simple in-memory paging; for large datasets, add dedicated paged queries)

---

### 4) Error Response (Global)
- Schema
```json
{
  "timestamp": "2026-01-13T13:12:00.123Z",
  "status": 409,
  "error": "Conflict",
  "message": "재고가 부족합니다.",
  "path": "/api/orders"
}
```

---

### 5) Scenario Mapping
- Scenario 1 (Concurrency & Stock): `POST /api/orders` uses pessimistic lock via `ItemRepository.findByIdForUpdate`
- Scenario 2 (Discount Strategy): `POST /api/orders` applies `DiscountPolicyFactory`
- Scenario 3 (Dynamic Search): `GET /api/items`, `GET /api/categories/{id}/items`
- Scenario 4 (Fetch Join Read): `GET /api/orders/{id}`, `GET /api/users/{userId}/orders`
- Scenario 5 (Virtual Payment & Validation): `POST /api/orders` integrates `VirtualPaymentGateway` and tampering guard

---

### 6) Curl Examples
- Search items
```
curl "http://localhost:8080/api/items?categoryId=10&minPrice=1000&maxPrice=5000&status=AVAILABLE&page=0&size=10"
```
- Search items under category hierarchy
```
curl "http://localhost:8080/api/categories/10/items?page=0&size=10"
```
- Create order
```
curl -X POST "http://localhost:8080/api/orders" \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"itemId":2,"quantity":1,"userCouponId":3,"clientPayAmount":9000}'
```
- Get order detail
```
curl "http://localhost:8080/api/orders/100"
```
- Get user orders
```
curl "http://localhost:8080/api/users/1/orders?page=0&size=20"
```
