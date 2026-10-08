# Universal Billing System — Backend Overview

> **Stack:** Java 21 · Spring Boot 3.3.0 · Spring Security · JPA/Hibernate · PostgreSQL · JWT · Lombok  
> **Server:** `http://localhost:8080`  
> **Frontend target:** `http://localhost:4200` (Angular)

---

## Table of Contents

1. [What This Project Does](#1-what-this-project-does)
2. [Tech Stack](#2-tech-stack)
3. [Project Structure](#3-project-structure)
4. [Configuration — application.properties](#4-configuration--applicationproperties)
5. [How Every Request Flows](#5-how-every-request-flows)
6. [Security Layer](#6-security-layer)
7. [DTOs — Request & Response Shapes](#7-dtos--request--response-shapes)
8. [Controllers — All API Endpoints](#8-controllers--all-api-endpoints)
9. [Services — Business Logic](#9-services--business-logic)
10. [Repositories — Database Queries](#10-repositories--database-queries)
11. [Entities — Database Tables](#11-entities--database-tables)
12. [Utilities](#12-utilities)
13. [Exception Handling](#13-exception-handling)
14. [ID Generation Rules](#14-id-generation-rules)
15. [Cross-Service Dependencies](#15-cross-service-dependencies)
16. [GST Computation Logic](#16-gst-computation-logic)
17. [Quick Reference](#17-quick-reference)

---

## 1. What This Project Does

This is the **REST API backend** for a billing and invoicing system built for the Indian market. It manages:

- **Users** — admin accounts that log in and manage the system
- **Customers** — clients who receive invoices
- **Bills/Invoices** — GST-compliant invoices with CGST/SGST/IGST computation
- **Payments** — records of payments against invoices (UPI, Net Banking, RuPay, NEFT, etc.)
- **Notifications** — email and SMS notifications to customers
- **Reports** — dashboard statistics, monthly revenue charts, analytics
- **Settings** — per-user profile settings and global system configuration

The backend is **stateless** — it never stores sessions. Every request must carry a **JWT token** in the `Authorization` header (except a few public routes).

---

## 2. Tech Stack

| Technology | Purpose |
|---|---|
| **Java 21** | Programming language |
| **Spring Boot 3.3.0** | Application framework — auto-configures everything |
| **Spring Security** | JWT validation, route protection, password encoding |
| **Spring Data JPA / Hibernate** | ORM — maps Java classes to PostgreSQL tables |
| **PostgreSQL** | Relational database |
| **JWT (jjwt 0.11.5)** | Authentication tokens — signed with HMAC-SHA256 |
| **Lombok** | Auto-generates getters, setters, constructors at compile time |
| **Spring Mail** | Sends emails via SMTP (Gmail) |
| **Bean Validation** | `@NotBlank`, `@Email`, `@Size` annotations on DTOs |
| **Maven** | Build tool and dependency management |

---

## 3. Project Structure

```
src/main/java/
│
├── com/billing/controller/          ← HTTP layer — receives requests, returns responses
│   ├── AuthController.java
│   ├── BillController.java
│   ├── CustomerController.java
│   ├── PaymentController.java
│   ├── NotificationController.java
│   ├── ReportsController.java
│   ├── SettingsController.java
│   └── PublicBillController.java
│
└── com/billing/backend/
    │
    ├── BackendApplication.java      ← Entry point — starts the server
    │
    ├── config/
    │   └── SecurityConfig.java      ← JWT + CORS + public/protected routes
    │
    ├── dto/                         ← Request/response data shapes
    │   ├── AuthResponse.java
    │   ├── LoginRequest.java
    │   ├── RegisterRequest.java
    │   ├── ForgotPasswordRequest.java
    │   ├── ResetPasswordRequest.java
    │   ├── CreateBillRequest.java
    │   ├── ProcessPaymentRequest.java
    │   └── SendNotificationRequest.java
    │
    ├── entity/                      ← Database table mappings
    │   ├── User.java                → table: users
    │   ├── Customer.java            → table: customers
    │   ├── Bill.java                → table: bills
    │   ├── BillItem.java            → table: bill_items
    │   ├── Payment.java             → table: payments
    │   ├── Notification.java        → table: notifications
    │   ├── PasswordResetToken.java  → table: password_reset_tokens
    │   └── SystemSettings.java      → table: system_settings
    │
    ├── repository/                  ← Database query interfaces
    │   ├── UserRepository.java
    │   ├── CustomerRepository.java
    │   ├── BillRepository.java
    │   ├── BillItemRepository.java
    │   ├── PaymentRepository.java
    │   ├── NotificationRepository.java
    │   ├── PasswordResetTokenRepository.java
    │   └── SystemSettingsRepository.java
    │
    ├── service/                     ← Business logic
    │   ├── AuthService.java
    │   ├── BillService.java
    │   ├── CustomerService.java
    │   ├── PaymentService.java
    │   ├── NotificationService.java
    │   ├── ReportsService.java
    │   ├── SettingsService.java
    │   └── CaptchaService.java
    │
    ├── security/
    │   └── JwtAuthFilter.java       ← Intercepts every request to validate JWT
    │
    ├── util/
    │   ├── JwtUtil.java             ← Generates and validates JWT tokens
    │   └── IndianCurrencyUtil.java  ← Converts numbers to Indian Rupee words
    │
    └── exception/
        ├── GlobalExceptionHandler.java    ← Catches all errors, formats JSON response
        ├── BadRequestException.java       ← 400
        ├── ConflictException.java         ← 409
        └── ResourceNotFoundException.java ← 404

src/main/resources/
    └── application.properties       ← Database, JWT, mail, CORS configuration
```

> **Why two packages?**  
> Controllers live in `com.billing.controller` and everything else in `com.billing.backend`.  
> `BackendApplication.java` uses `scanBasePackages = "com.billing"` to scan **both** packages so Spring finds all the classes.

---

## 4. Configuration — application.properties

**File:** `src/main/resources/application.properties`

| Property | Value | Purpose |
|---|---|---|
| `server.port` | `8080` | The port the backend runs on |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/universal_billing` | PostgreSQL connection |
| `spring.datasource.username` | `postgres` | DB username |
| `spring.datasource.password` | `Sani@2003` | DB password |
| `spring.jpa.hibernate.ddl-auto` | `update` | Auto-creates/updates tables on startup |
| `spring.jpa.show-sql` | `true` | Prints SQL to console (useful for debugging) |
| `jwt.secret` | long string | Secret key used to sign JWT tokens |
| `jwt.expiration` | `86400000` | Token expires after 24 hours (in milliseconds) |
| `cors.allowed-origins` | `http://localhost:4200` | Only this origin can call the API |
| `spring.mail.host` | `smtp.gmail.com` | SMTP server for sending emails |
| `spring.mail.port` | `587` | Gmail SMTP port |
| `spring.mail.username` | your Gmail | The "From" email address |
| `app.supplier.gstin` | `29AABCU9603R1ZM` | Your company's GST number (used on invoices) |
| `app.supplier.state-code` | `29` | Your state code (Karnataka = 29) |
| `app.base-url` | `http://localhost:4200` | Frontend URL (used in share links) |
| `recaptcha.enabled` | `false` | Set `true` in production to enable CAPTCHA |

---

## 5. How Every Request Flows

Every single HTTP request travels through these layers in order:

```
[Browser / Postman]
        │
        │  HTTP Request
        ▼
┌──────────────────────────────┐
│  JwtAuthFilter               │  ← Runs FIRST, before everything
│  Reads Authorization header  │
│  Validates JWT token         │
│  Sets user identity          │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│  SecurityConfig              │  ← Decides: allowed or blocked?
│  Public routes → allow       │
│  Protected routes → check    │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│  Controller                  │  ← Receives HTTP data
│  Reads URL, body, params     │
│  Validates DTO with @Valid   │
│  Calls Service               │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│  Service                     │  ← Does the actual work
│  Applies business rules      │
│  Calls Repository            │
└──────────────┬───────────────┘
               │
               ▼
┌──────────────────────────────┐
│  Repository                  │  ← Talks to the database
│  Runs SQL queries            │
│  Returns entity objects      │
└──────────────┬───────────────┘
               │
               ▼
        PostgreSQL Database

               │  Result travels back up
               ▼

┌──────────────────────────────┐
│  GlobalExceptionHandler      │  ← If anything threw an exception,
│  Catches all errors          │    catches it here and formats JSON
│  Returns clean JSON error    │
└──────────────────────────────┘
               │
               ▼
        [Browser receives JSON response]
```

---

## 6. Security Layer

### 6a. JwtAuthFilter (`security/JwtAuthFilter.java`)

This filter runs **before every request**. It reads the JWT from the `Authorization` header and figures out who the user is.

```
Incoming request
        │
        ▼
Does "Authorization: Bearer <token>" header exist?
  │
  ├── NO  → Skip JWT check, pass request along
  │         (public routes like /api/auth/login will still work)
  │
  └── YES → Extract token (remove "Bearer " prefix)
                │
                ▼
        Is token valid? (signature OK + not expired)
          │
          ├── NO  → Pass request along
          │         (Spring Security will block protected routes with 401)
          │
          └── YES → Extract userId ("USR-01") and role ("Administrator")
                        │
                        ▼
                Store in SecurityContextHolder
                (Spring's way of remembering who this request belongs to)
                        │
                        ▼
                Continue to Controller
```

### 6b. SecurityConfig (`config/SecurityConfig.java`)

Defines which routes require authentication and which are open to everyone.

**Public routes (no JWT needed):**

```
POST /api/auth/login
POST /api/auth/register
POST /api/auth/forgot-password
POST /api/auth/reset-password
POST /api/auth/resend-activation
GET  /api/auth/account-status
GET  /api/bill/public/**
```

**Protected routes:** Every other URL — requires a valid JWT.

**Other security settings:**
- `CSRF disabled` — REST APIs don't need CSRF protection (no browser sessions)
- `CORS allowed` — `http://localhost:4200` is allowed to call this API
- `Stateless sessions` — server never stores sessions
- `BCryptPasswordEncoder(12)` — passwords are hashed with 12 rounds of BCrypt

---

## 7. DTOs — Request & Response Shapes

**Folder:** `dto/`

A DTO (Data Transfer Object) defines exactly what data a request body should contain. Spring automatically converts the incoming JSON into a DTO object.

### LoginRequest
```json
{
  "username": "admin@ubs.io",
  "password": "secret123",
  "captchaToken": "reCAPTCHA-token-from-frontend"
}
```

### RegisterRequest
```json
{
  "name": "John Doe",
  "email": "john@company.com",
  "company": "My Company Ltd",
  "password": "mypassword123"
}
```

### AuthResponse *(sent back after login/register)*
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": "USR-01",
    "name": "John Doe",
    "email": "john@company.com",
    "role": "Administrator",
    "avatarUrl": null
  }
}
```

### ForgotPasswordRequest
```json
{ "emailOrUsername": "john@company.com" }
```

### ResetPasswordRequest
```json
{
  "token": "a3f8c2d1e4b5...",
  "newPassword": "newpassword123"
}
```

### CreateBillRequest
```json
{
  "customerId": "CUST-001",
  "customerName": "Infosys Ltd",
  "customerGstin": "29AAACI4321A1ZG",
  "placeOfSupply": "Karnataka (29)",
  "stateCode": "29",
  "pan": "AAAAA9999A",
  "isInterState": false,
  "gstRate": 18,
  "dueDate": "2026-10-22",
  "notes": "Payment via UPI preferred",
  "items": [
    { "name": "Cloud Services", "hsnSac": "998314", "quantity": 2, "price": 50000 },
    { "name": "Security Audit", "hsnSac": "998314", "quantity": 1, "price": 25000 }
  ]
}
```

### ProcessPaymentRequest
```json
{
  "billId": "INV-2026-842109",
  "customerName": "Infosys Ltd",
  "amount": 147500.00,
  "method": "UPI",
  "upiId": "infosys@oksbi",
  "bankName": null
}
```

### SendNotificationRequest
```json
{
  "type": "EMAIL",
  "recipient": "client@infosys.com",
  "message": "Your invoice INV-2026-001 is ready for payment."
}
```

---

## 8. Controllers — All API Endpoints

**Folder:** `com/billing/controller/`

Controllers are the HTTP entry points. They receive the request, call the service, and return a response. They contain **no business logic**.

---

### AuthController — `/api/auth`

| Method | Endpoint | Request Body | Response | Status |
|---|---|---|---|---|
| POST | `/api/auth/login` | `LoginRequest` | `AuthResponse` | 200 |
| POST | `/api/auth/register` | `RegisterRequest` | `AuthResponse` | 201 |
| POST | `/api/auth/logout` | *(none)* | `{ message }` | 200 |
| POST | `/api/auth/forgot-password` | `ForgotPasswordRequest` | `{ message }` | 200 |
| POST | `/api/auth/reset-password` | `ResetPasswordRequest` | `{ message }` | 200 |
| POST | `/api/auth/resend-activation` | `{ email }` | `{ message }` | 200 |
| GET | `/api/auth/account-status?email=x` | *(query param)* | `{ status }` | 200 |

**Login flow inside the controller:**
```
POST /api/auth/login
  1. @Valid validates LoginRequest fields (username and password not blank)
  2. captchaService.verify(captchaToken) — checks Google reCAPTCHA
  3. authService.login(username, password) — authenticates the user
  4. authService.generateToken(user) — creates JWT
  5. Builds AuthResponse with token + user fields (never passwordHash)
  6. Returns HTTP 200 OK
```

---

### CustomerController — `/api/customers` *(protected)*

| Method | Endpoint | Query Params | Request Body | Response | Status |
|---|---|---|---|---|---|
| GET | `/api/customers` | `?search=&status=` | *(none)* | `List<Customer>` | 200 |
| GET | `/api/customers/{id}` | *(none)* | *(none)* | `Customer` | 200 / 404 |
| POST | `/api/customers` | *(none)* | `Customer` (entity direct) | `Customer` | 201 |
| PUT | `/api/customers/{id}` | *(none)* | `Customer` (partial fields) | `Customer` | 200 |
| DELETE | `/api/customers/{id}` | *(none)* | *(none)* | `{ deleted: true }` | 200 |

> **Note on DELETE:** Customers are never truly deleted. The service sets `status = INACTIVE` so that bill history is preserved.

---

### BillController — `/api/bills` *(protected)*

| Method | Endpoint | Notes | Status |
|---|---|---|---|
| GET | `/api/bills` | `?status=PAID&search=infosys&customerId=CUST-001` | 200 |
| GET | `/api/bills/stats` | Revenue and count totals | 200 |
| GET | `/api/bills/{id}` | Single bill by ID | 200 / 404 |
| GET | `/api/bills/link/{uniqueLink}` | Single bill by share link | 200 / 404 |
| POST | `/api/bills` | Body: `CreateBillRequest` | 201 |
| PUT | `/api/bills/{id}` | Body: `CreateBillRequest` (partial) | 200 |
| DELETE | `/api/bills/{id}` | Hard delete, adjusts customer counters | 200 |
| PATCH | `/api/bills/{id}/mark-paid` | Admin manually marks bill as paid | 200 |
| POST | `/api/bills/generate-link` | Body: `{ "billId": "INV-..." }` | 200 |

> **Important:** `/api/bills/stats` is declared **before** `/api/bills/{id}` in the controller. If it came after, Spring would try to find a bill with `id = "stats"`.

---

### PublicBillController — `/api/bill/public` *(no auth required)*

| Method | Endpoint | Notes | Status |
|---|---|---|---|
| GET | `/api/bill/public/{uniqueId}` | View bill via share link — no JWT needed | 200 / 404 |
| PATCH | `/api/bill/public/{uniqueId}/pay` | Simulate UPI payment from public view | 200 |

Used when a customer receives a share link like `https://app.com/bill/view/bill-infosys-842109` and opens it without logging in.

---

### PaymentController — `/api/payments` *(protected)*

| Method | Endpoint | Notes | Status |
|---|---|---|---|
| GET | `/api/payments` | `?billId=&status=&method=` | 200 |
| POST | `/api/payments/process` | Body: `ProcessPaymentRequest` | 201 |

**Supported payment methods:** `UPI`, `NET_BANKING`, `RUPAY_CARD`, `NEFT_RTGS`, `CREDIT_CARD`, `BANK_TRANSFER`, `PAYPAL`, `STRIPE`

---

### NotificationController — `/api/notifications` *(protected)*

| Method | Endpoint | Notes | Status |
|---|---|---|---|
| GET | `/api/notifications` | Returns all notifications, newest first | 200 |
| POST | `/api/notifications/send` | Body: `SendNotificationRequest` | 201 |

---

### ReportsController — `/api/reports` *(protected)*

| Method | Endpoint | Returns | Status |
|---|---|---|---|
| GET | `/api/reports/stats` | `{ totalRevenue, paidCount, pendingCount, overdueCount, totalCount }` | 200 |
| GET | `/api/reports/dashboard` | `{ totalRevenueYTD, totalBillsGenerated, collectionRate, monthlyData[] }` | 200 |
| GET | `/api/reports/analytics` | `{ paymentMethods[], billStatusBreakdown[] }` | 200 |

No request body for any of these — all read-only aggregation queries.

---

### SettingsController — `/api/settings` *(protected)*

| Method | Endpoint | Notes | Status |
|---|---|---|---|
| GET | `/api/settings/profile` | Gets profile of the logged-in user | 200 |
| PUT | `/api/settings/profile` | Updates name/email of the logged-in user | 200 |
| GET | `/api/settings/system` | Global system settings (singleton) | 200 |
| PUT | `/api/settings/system` | Updates global system settings | 200 |

The logged-in user's ID is extracted from the JWT: `authentication.getName()` returns `"USR-01"`.

---

## 9. Services — Business Logic

**Folder:** `backend/service/`

Services contain all the rules. They never deal with HTTP — they just receive data, apply logic, call repositories, and return results.

---

### AuthService

Handles login, registration, password reset, and JWT generation.

**Login logic:**
```
1. Find user by email OR username in DB
2. If not found → throw BadRequestException (400)
3. If account is LOCKED → throw BadRequestException (400)
4. If account is INACTIVE → throw BadRequestException (400)
5. BCrypt.matches(submittedPassword, storedHash)
6. If wrong password:
   → increment loginAttempts counter
   → if attempts >= 5 → set status = LOCKED
   → tell user how many attempts remain
7. If correct:
   → reset loginAttempts to 0
   → return User entity
```

**Password Reset flow:**
```
1. POST /api/auth/forgot-password
   → Find user by email/username
   → Generate 32-byte random hex token
   → Save to password_reset_tokens table (expires in 1 hour)
   → Return token (controller should email it to user)

2. POST /api/auth/reset-password
   → Find token in DB
   → Check: not expired AND not already used
   → Hash new password with BCrypt
   → Update user.passwordHash
   → Mark token as used = true
```

---

### BillService

The most complex service — handles invoice creation with full GST computation.

**Create Bill — full logic:**
```
Input: bill metadata + list of items

Step 1  Validate customerId, customerName, items not empty
Step 2  Validate each item (name required, qty > 0, price >= 0)
Step 3  Validate gstRate ∈ {0, 5, 12, 18, 28}
Step 4  Compute subtotal = Σ(quantity × price) for all items
Step 5  Compute tax = subtotal × (gstRate / 100)
Step 6  Split tax:
          isInterState = false → CGST = tax/2, SGST = tax/2, IGST = 0
          isInterState = true  → CGST = 0, SGST = 0, IGST = tax
Step 7  total = subtotal + tax
Step 8  amountInWords = IndianCurrencyUtil.numberToWords(total)
Step 9  billId = "INV-" + year + "-" + last6DigitsOfTimestamp
Step 10 uniqueLink = "bill-" + customerNameSlug + "-" + suffix
Step 11 Verify customer exists (throws 404 if not)
Step 12 Save bill + all items to DB (cascade saves items automatically)
Step 13 Increment customer.billsCount by 1
```

**Update Bill:** Re-runs the same computation if new items are provided.

**Delete Bill:** Hard deletes from DB, then adjusts `customer.billsCount` and reverses `customer.totalSpent` if the bill was already paid.

**Mark as Paid:**
```
1. Find bill by ID (throws 404 if not found)
2. If already PAID → throw BadRequestException (400)
3. Set status = PAID, save to DB
4. Add bill.total to customer.totalSpent
```

---

### CustomerService

Handles customer CRUD with validation.

**Create Customer validations:**
- Name and email are required
- Email must match regex: `^[^\s@]+@[^\s@]+\.[^\s@]+$`
- GSTIN must be exactly 15 characters matching the Indian GST format
- PAN must be exactly 10 characters matching `AAAAA9999A` format

**Soft Delete:** Sets `status = INACTIVE` instead of removing the record, preserving all linked bill history.

**Internal methods called by BillService:**
- `incrementBillCount(customerId)` — +1 when a bill is created
- `decrementBillCount(customerId)` — -1 when a bill is deleted
- `addToTotalSpent(customerId, amount)` — adds amount when a bill is paid (pass negative to reverse)

---

### PaymentService

Handles payment processing for all 8 methods.

**Conditional validations:**
- `method = UPI` → `upiId` is required, must contain `@`
- `method = NET_BANKING` or `NEFT_RTGS` → `bankName` is required

**After saving payment:**
```
→ bill.status = PAID (only if not already PAID)
→ customer.totalSpent += bill.total
```

**Transaction ID format by method:**

| Method | Prefix | Example |
|---|---|---|
| UPI | `UPI` | `TXN-UPI-842109` |
| NET_BANKING | `NETB` | `TXN-NETB-842109` |
| RUPAY_CARD | `RUPA` | `TXN-RUPA-842109` |
| NEFT_RTGS | `NEFT` | `TXN-NEFT-842109` |
| CREDIT_CARD | `CRED` | `TXN-CRED-842109` |
| BANK_TRANSFER | `BANK` | `TXN-BANK-842109` |
| PAYPAL | `PAYP` | `TXN-PAYP-842109` |
| STRIPE | `STRI` | `TXN-STRI-842109` |

---

### NotificationService

Sends email via Spring's `JavaMailSender` (SMTP). SMS is stubbed with a log statement.

**On send failure:** Does NOT throw an exception — stores the notification with `status = FAILED` in the DB. This way a failed email never crashes the main operation (like processing a payment).

**Auto-triggered methods (called internally by other services):**
- `notifyBillPaid(email, billId, amount)` — after a bill is marked paid
- `notifyBillOverdue(email, billId)` — when a bill becomes overdue
- `notifyDueSoon(email, billId, dueDate)` — 7 days before due date

---

### CaptchaService

Calls Google's reCAPTCHA v2 `siteverify` API before every login attempt.

```
Request: POST https://www.google.com/recaptcha/api/siteverify
Params:  secret=<YOUR_SECRET_KEY> & response=<USER_TOKEN>
Response: { "success": true/false, "error-codes": [...] }
```

If `recaptcha.enabled=false` in `application.properties`, the check is skipped entirely (useful for local development).

---

### ReportsService

Pure read-only service — runs aggregate queries and formats data for charts.

**Dashboard data:**
- YTD revenue from all PAID bills in the current year
- Collection rate = `(paidCount / totalCount) × 100`
- Monthly breakdown — one entry per month Jan–Dec, `amount = 0` for months with no paid bills

**Analytics data:**
- Payment method breakdown with percentage of total volume
- Bill status breakdown (Paid / Pending / Overdue) with percentage

---

### SettingsService

**System settings** are stored as a single row (`id = 1`) in the `system_settings` table. On first `GET /api/settings/system`, if the row doesn't exist, it's created with all default values.

---

## 10. Repositories — Database Queries

**Folder:** `backend/repository/`

Each repository is an **interface** that extends `JpaRepository<EntityType, IdType>`. Spring automatically provides the implementation — you don't write SQL for standard operations.

**Free methods from JpaRepository (no code needed):**
```java
save(entity)          // INSERT or UPDATE
findById("CUST-001")  // SELECT * WHERE id = ?
findAll()             // SELECT * FROM table
delete(entity)        // DELETE WHERE id = ?
count()               // SELECT COUNT(*) FROM table
existsById("x")       // SELECT EXISTS(...)
```

**Custom queries (method name convention):**
```java
findByEmail(String email)
// → Spring reads "findBy" + "Email" → SELECT * FROM users WHERE email = ?

findByStatus(CustomerStatus status)
// → SELECT * FROM customers WHERE status = ?

existsByEmail(String email)
// → SELECT EXISTS(SELECT 1 FROM users WHERE email = ?)
```

**Custom JPQL queries (for complex logic):**
```java
@Query("SELECT b FROM Bill b WHERE LOWER(b.customerName) LIKE LOWER(CONCAT('%', :term, '%'))")
List<Bill> searchByIdOrCustomerName(@Param("term") String term);
```

---

**Key custom queries per repository:**

**BillRepository:**
```
findByUniqueLink(link)             → find bill for public share page
getTotalRevenue()                  → SUM of all PAID bill totals
getTotalRevenueByYear(year)        → SUM for current year only
getMonthlyRevenue(year)            → list of [month, amount] pairs
getPaymentMethodBreakdown()        → list of [method, count, volume]
findOverdueBills(today)            → PENDING bills past due date
findBillsDueSoon(today, +7days)    → bills due within 7 days
searchByIdOrCustomerName(term)     → powers the search box
```

**CustomerRepository:**
```
searchByNameOrEmail(term)                  → case-insensitive name/email search
searchByNameOrEmailAndStatus(term, status) → search + filter combined
existsByEmail(email)                       → duplicate check on create
```

**PasswordResetTokenRepository:**
```
deleteExpiredTokens(now)           → cleanup job, removes stale tokens
hasActiveToken(userId, now)        → checks if user already has a valid token
deleteAllByUserId(userId)          → cleanup after successful reset
```

---

## 11. Entities — Database Tables

**Folder:** `backend/entity/`

Each entity class maps to a PostgreSQL table. Fields map to columns. Relationships map to foreign keys.

---

### `users` table (User.java)

| Column | Type | Notes |
|---|---|---|
| `id` | `VARCHAR(20)` | PK, format: `USR-01` |
| `name` | `VARCHAR(100)` | Full name |
| `email` | `VARCHAR(150)` | Unique, used for login |
| `username` | `VARCHAR(50)` | Unique, auto-derived from email |
| `password_hash` | `VARCHAR(255)` | BCrypt hash, never plain text |
| `role` | `VARCHAR(50)` | Default: `Administrator` |
| `avatar_url` | `TEXT` | Optional profile picture URL |
| `status` | `VARCHAR(20)` | Enum: `ACTIVE`, `INACTIVE`, `LOCKED` |
| `login_attempts` | `INT` | Resets to 0 on successful login |
| `created_at` | `TIMESTAMP` | Set once on create, never updated |
| `updated_at` | `TIMESTAMP` | Updated on every save |

---

### `customers` table (Customer.java)

| Column | Type | Notes |
|---|---|---|
| `id` | `VARCHAR(20)` | PK, format: `CUST-001` |
| `name` | `VARCHAR(150)` | Required |
| `email` | `VARCHAR(150)` | Required |
| `phone` | `VARCHAR(30)` | Required |
| `address` | `TEXT` | Required |
| `company` | `VARCHAR(150)` | Optional |
| `gstin` | `VARCHAR(15)` | Indian GST number (optional) |
| `pan` | `VARCHAR(10)` | Permanent Account Number (optional) |
| `state` | `VARCHAR(50)` | e.g. `Karnataka` |
| `state_code` | `VARCHAR(5)` | e.g. `29` (Karnataka) |
| `total_spent` | `DECIMAL(15,2)` | Running total of all paid bills |
| `bills_count` | `INT` | Total invoices created for this customer |
| `status` | `VARCHAR(20)` | Enum: `ACTIVE`, `INACTIVE` |

---

### `bills` table (Bill.java)

| Column | Type | Notes |
|---|---|---|
| `id` | `VARCHAR(30)` | PK, format: `INV-2026-842109` |
| `customer_id` | `VARCHAR(20)` | FK reference to customers.id |
| `customer_name` | `VARCHAR(150)` | Denormalized copy for quick display |
| `customer_gstin` | `VARCHAR(15)` | Customer's GST number |
| `supplier_gstin` | `VARCHAR(15)` | Always `29AABCU9603R1ZM` (your company) |
| `pan` | `VARCHAR(10)` | PAN number |
| `place_of_supply` | `VARCHAR(80)` | e.g. `Karnataka (29)` |
| `state_code` | `VARCHAR(5)` | 2-digit state code |
| `is_inter_state` | `BOOLEAN` | true = IGST, false = CGST+SGST |
| `gst_rate` | `DECIMAL(5,2)` | 0, 5, 12, 18, or 28 |
| `subtotal` | `DECIMAL(15,2)` | Sum of all line items |
| `cgst` | `DECIMAL(15,2)` | Central GST (intra-state only) |
| `sgst` | `DECIMAL(15,2)` | State GST (intra-state only) |
| `igst` | `DECIMAL(15,2)` | Integrated GST (inter-state only) |
| `tax` | `DECIMAL(15,2)` | Total tax amount |
| `total` | `DECIMAL(15,2)` | subtotal + tax |
| `amount_in_words` | `TEXT` | e.g. `Rupees One Lakh Forty Seven Thousand Only` |
| `currency` | `VARCHAR(5)` | Always `INR` |
| `status` | `VARCHAR(20)` | Enum: `PENDING`, `PAID`, `OVERDUE` |
| `unique_link` | `VARCHAR(150)` | Unique slug for public share URL |
| `created_at` | `DATE` | Invoice date |
| `due_date` | `DATE` | Payment deadline (default: +14 days) |
| `notes` | `TEXT` | Optional notes on invoice |

---

### `bill_items` table (BillItem.java)

| Column | Type | Notes |
|---|---|---|
| `id` | `VARCHAR(60)` | PK, format: `item-1728000000000-0` |
| `bill_id` | `VARCHAR(30)` | FK to bills.id |
| `name` | `VARCHAR(200)` | Service/product name |
| `hsn_sac` | `VARCHAR(10)` | Default: `998314` (IT services) |
| `quantity` | `DECIMAL(10,2)` | Can be decimal (e.g. 1.5 hours) |
| `price` | `DECIMAL(15,2)` | Price per unit |
| `amount` | `DECIMAL(15,2)` | Computed: `quantity × price` |

**Relationship:** One `Bill` has many `BillItem`s.  
- Saving a bill automatically saves all its items (`CascadeType.ALL`)  
- Deleting a bill automatically deletes all its items (`orphanRemoval = true`)  
- Items are always loaded when a bill is loaded (`FetchType.EAGER`)

---

### `payments` table (Payment.java)

| Column | Type | Notes |
|---|---|---|
| `id` | `VARCHAR(20)` | PK, format: `PAY-0001` |
| `bill_id` | `VARCHAR(30)` | FK reference to bills.id |
| `customer_name` | `VARCHAR(150)` | Copied from bill (denormalized) |
| `amount` | `DECIMAL(15,2)` | Payment amount |
| `currency` | `VARCHAR(5)` | Always `INR` |
| `method` | `VARCHAR(30)` | Enum: `UPI`, `NET_BANKING`, etc. |
| `upi_id` | `VARCHAR(100)` | e.g. `accounts@oksbi` (UPI only) |
| `bank_name` | `VARCHAR(100)` | e.g. `HDFC` (banking methods only) |
| `utr_number` | `VARCHAR(50)` | Unique Transaction Reference |
| `status` | `VARCHAR(20)` | Enum: `SUCCESS`, `FAILED`, `PENDING` |
| `transaction_id` | `VARCHAR(50)` | e.g. `TXN-UPI-842109` |
| `date` | `VARCHAR(80)` | Human-readable: `8 Oct 2026, 02:30 pm` |

---

### `notifications` table (Notification.java)

| Column | Type | Notes |
|---|---|---|
| `id` | `VARCHAR(20)` | PK, format: `NOTIF-001` |
| `type` | `VARCHAR(10)` | Enum: `EMAIL`, `SMS` |
| `recipient` | `VARCHAR(150)` | Email address or phone number |
| `message` | `TEXT` | The message body sent |
| `status` | `VARCHAR(20)` | Enum: `SENT`, `FAILED`, `PENDING` |
| `sent_at` | `VARCHAR(80)` | Human-readable: `8 Oct 2026, 02:30 pm` |

---

### `password_reset_tokens` table (PasswordResetToken.java)

| Column | Type | Notes |
|---|---|---|
| `id` | `VARCHAR(100)` | PK — the token itself (64 char hex) |
| `user_id` | `VARCHAR(20)` | FK reference to users.id |
| `expires_at` | `TIMESTAMP` | now + 1 hour from creation |
| `used` | `BOOLEAN` | Set to `true` after password is reset |
| `created_at` | `TIMESTAMP` | When the token was generated |

---

### `system_settings` table (SystemSettings.java)

This table always has **exactly one row** (`id = 1`). It is a singleton config store.

| Column | Default | Purpose |
|---|---|---|
| `id` | `1` | Always 1 |
| `currency` | `INR` | Display currency |
| `tax_rate` | `18.00` | Default GST % |
| `tax_label` | `GST` | Tax label on invoices |
| `invoice_prefix` | `INV` | Prefix for bill IDs |
| `company_name` | `Universal Billing Pvt. Ltd.` | Shown on invoices |
| `company_email` | `billing@universalbilling.in` | Company contact |
| `company_gstin` | `29AABCU9603R1ZM` | Your GST number |
| `email_on_payment` | `true` | Auto-email customer when paid |
| `email_on_overdue` | `true` | Auto-email customer when overdue |
| `weekly_report` | `true` | Send weekly summary email |

---

## 12. Utilities

**Folder:** `backend/util/`

### JwtUtil

Generates and validates JWT tokens.

**Token structure:**
```
eyJhbGciOiJIUzI1NiJ9    ← Header (base64): algorithm info
.
eyJzdWIiOiJVU1ItMDEi... ← Payload (base64): userId, name, email, role, expiry
.
SflKxwRJSMeKKF2QT4fw... ← Signature: proves it wasn't tampered with
```

**Claims stored in the token:**
```
sub   → userId  ("USR-01")
name  → "John Doe"
email → "john@company.com"
role  → "Administrator"
iat   → issued at timestamp
exp   → expiry timestamp (now + 24h)
```

**Key methods:**
```
generateToken(userId, name, email, role) → returns JWT string
isTokenValid(token)                      → returns true/false
extractUserId(token)                     → returns "USR-01"
extractRole(token)                       → returns "Administrator"
extractEmail(token)                      → returns "john@company.com"
```

---

### IndianCurrencyUtil

Converts a number to Indian Rupee words (printed on invoices).

**Examples:**
```
118000.00  → "Rupees One Lakh Eighteen Thousand Only"
76700.50   → "Rupees Seventy Six Thousand Seven Hundred and Fifty Paise Only"
10000000   → "Rupees One Crore Only"
```

**Indian number system used:**
```
100        = Hundred
1,000      = Thousand
1,00,000   = Lakh  (100 thousand)
1,00,00,000 = Crore (10 million)
```

**Also provides `formatIndianCurrency(amount)`:**
```
1234567.89 → "12,34,567.89"
100000.00  → "1,00,000.00"
```

---

## 13. Exception Handling

**Folder:** `backend/exception/`

`GlobalExceptionHandler` is annotated with `@RestControllerAdvice`, meaning it automatically catches exceptions thrown from **any controller** in the entire application.

**Exception types and their HTTP codes:**

| Exception class | HTTP Status | When it's thrown |
|---|---|---|
| `ResourceNotFoundException` | `404 Not Found` | Entity not found by ID (customer, bill, etc.) |
| `BadRequestException` | `400 Bad Request` | Invalid business rules (wrong password, bill already paid, invalid GST rate) |
| `ConflictException` | `409 Conflict` | Duplicate data (email already registered) |
| `MethodArgumentNotValidException` | `422 Unprocessable Entity` | `@Valid` constraint violated on a DTO field |
| `Exception` (catch-all) | `500 Internal Server Error` | Any unexpected runtime error |

**Every error returns this exact JSON shape:**
```json
{
  "statusCode": 404,
  "error": "Not Found",
  "message": "Customer with id 'CUST-999' not found",
  "timestamp": "2026-10-08T07:00:00Z"
}
```

**Validation errors (422) return a list of field-level errors:**
```json
{
  "statusCode": 422,
  "error": "Validation Error",
  "errors": [
    { "field": "email", "message": "Invalid email format" },
    { "field": "password", "message": "Password must be at least 8 characters" }
  ],
  "timestamp": "2026-10-08T07:00:00Z"
}
```

---

## 14. ID Generation Rules

| Entity | Format | Example | Generation logic |
|---|---|---|---|
| User | `USR-XX` | `USR-01` | `"USR-" + padded(count + 1, 2 digits)` |
| Customer | `CUST-XXX` | `CUST-001` | `"CUST-" + padded(count + 1, 3 digits)` |
| Bill | `INV-YYYY-XXXXXX` | `INV-2026-842109` | `"INV-" + year + "-" + last6(timestamp)` |
| BillItem | `item-TS-INDEX` | `item-1728000000000-0` | `"item-" + fullTimestamp + "-" + itemIndex` |
| Payment | `PAY-XXXX` | `PAY-0001` | `"PAY-" + padded(count + 1, 4 digits)` |
| Notification | `NOTIF-XXX` | `NOTIF-001` | `"NOTIF-" + padded(count + 1, 3 digits)` |
| Reset Token | 64-char hex | `a3f8c2d1...` | `SecureRandom` 32 bytes → hex string |

---

## 15. Cross-Service Dependencies

```
AuthController
  └── calls → CaptchaService.verify()             (before login logic)

BillService
  ├── calls → CustomerService.getCustomerById()   (verify customer exists)
  ├── calls → CustomerService.incrementBillCount() (after bill created)
  ├── calls → CustomerService.decrementBillCount() (after bill deleted)
  ├── calls → CustomerService.addToTotalSpent()   (after bill marked paid)
  └── calls → IndianCurrencyUtil.numberToWords()  (total → Indian words)

PaymentService
  ├── calls → CustomerService.addToTotalSpent()   (after payment processed)
  └── uses  → BillRepository directly             (update bill status to PAID)

NotificationService
  └── triggered by BillService / PaymentService   (on paid/overdue events)

SettingsService
  └── calls → UserRepository                      (profile read/write)
```

---

## 16. GST Computation Logic

India's GST system has two modes depending on whether the transaction is within the same state or across states.

```
INTRA-STATE (customer state == supplier state):
  CGST = tax / 2   (Central GST — goes to central government)
  SGST = tax / 2   (State GST — goes to state government)
  IGST = 0

INTER-STATE (customer state != supplier state):
  CGST = 0
  SGST = 0
  IGST = tax       (Integrated GST — goes to central government, redistributed)
```

**Example with GST 18%, intra-state:**

```
Items:
  Cloud Services   qty=2   price=50,000   amount=1,00,000
  Security Audit   qty=1   price=25,000   amount=25,000

Subtotal       = 1,25,000.00
GST 18%        = 1,25,000 × 18/100 = 22,500.00
CGST (9%)      = 11,250.00
SGST (9%)      = 11,250.00
IGST (0%)      = 0.00
─────────────────────────────
Total          = 1,47,500.00
Amount in words: "Rupees One Lakh Forty Seven Thousand Five Hundred Only"
```

**Allowed GST rates:** `0%`, `5%`, `12%`, `18%`, `28%` — anything else throws a 400 error.

---

## 17. Quick Reference

### Find the right file to edit

| I want to change... | Edit this file |
|---|---|
| Add a new API endpoint | `com/billing/controller/` |
| Change what data a request accepts | `com/billing/backend/dto/` |
| Change business rules / calculations | `com/billing/backend/service/` |
| Add or change a database query | `com/billing/backend/repository/` |
| Change database table columns | `com/billing/backend/entity/` |
| Change which routes need a JWT | `backend/config/SecurityConfig.java` |
| Change JWT expiry or secret | `application.properties` |
| Change error response format | `backend/exception/GlobalExceptionHandler.java` |
| Change SMTP email settings | `application.properties` |
| Change database connection | `application.properties` |

### Common HTTP status codes used

| Code | Meaning | When returned |
|---|---|---|
| `200 OK` | Success | GET, PUT, PATCH, DELETE, POST logout |
| `201 Created` | New resource created | POST (create bill, customer, payment) |
| `400 Bad Request` | Invalid request data | Wrong password, bad GST rate, etc. |
| `401 Unauthorized` | Not logged in | No JWT or expired JWT on protected route |
| `404 Not Found` | Resource doesn't exist | Customer/bill/payment not found |
| `409 Conflict` | Duplicate data | Email already registered |
| `422 Unprocessable` | Validation failed | `@NotBlank`, `@Email` etc. violated |
| `500 Server Error` | Unexpected crash | Unhandled exception |

### How to run the project

```bash
# Prerequisites:
#   1. PostgreSQL running on localhost:5432
#   2. Database "universal_billing" created
#   3. Username/password updated in application.properties

# Run with Maven wrapper:
./mvnw spring-boot:run

# Or build a JAR and run:
./mvnw clean package
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

The API will be available at: `http://localhost:8080`
