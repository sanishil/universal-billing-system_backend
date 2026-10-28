# Backend — Project Structure & Changes (Spring Boot)

This document covers the backend work for three new features in the UBS billing app.

| # | Feature | In one line |
|---|---------|-------------|
| 1 | **Optional GST** | `bills.gst_rate` becomes nullable. `NULL` means No GST. |
| 2 | **Billing loop** | Every user is an issuer and a possible recipient. A customer's email links them to a user account; an invite is created if none exists. |
| 3 | **Public link + email** | A secure public link is created, and the bill is emailed with that link and a PDF attachment. |

> **Actual stack (this repo):** Spring Boot 3.3, Java 21, Spring Security + JWT, JPA/Hibernate, **PostgreSQL**, `spring.jpa.hibernate.ddl-auto=update` (**no Flyway**), `application.properties` (not `.yml`).
> The tree in §1 was written for a different layout. Keep its tables, endpoints and rules, but use the real locations below.

| Doc says | This repo |
|----------|-----------|
| `com.ubs.billing.<feature>/` | entities `com.billing.backend.entity`, services `com.billing.backend.service`, repos `com.billing.backend.repository`, DTOs `com.billing.backend.dto`, controllers `com.billing.controller` |
| `BIGINT` ids | `VARCHAR` ids: `USR-01`, `CUST-001`, `INV-2026-123456` |
| `bills.public_token` | existing column `bills.unique_link` |
| `JwtService`, `CurrentUser` | `util/JwtUtil`; current user id = `authentication.getName()` (set by `JwtAuthFilter`) |
| `BillRequest` | `dto/CreateBillRequest` |
| `application.yml` | `application.properties` + git-ignored `secrets.properties` |

Legend: `[NEW]` new file · `[MOD]` existing file that changes · no tag = unchanged.

## Implementation status

| Phase (§9) | Status |
|------------|--------|
| 1. Optional GST | **Done.** See §3. |
| Public link hardening (part of 5) | **Done:** random 32-byte tokens, safe `PublicBillResponse`, public routes GET-only, public "pay" removed. Expiry/revoke (410) is not done yet. |
| 2. Profile fields | Not started |
| 3. Ownership / per-user scoping | Not started |
| 4. Invites / set-password | Not started |
| 5. PDF + email + retry | Not started |

Fixes made alongside Phase 1:
- `CustomerController` mapping restored to `/api/customers`. It had been changed to `/api`, which broke `POST`/`PUT`/`DELETE`. `GET /api/customers` is now paged and still supports `search`/`status`.
- `BillItem.bill` gets `@JsonIgnore` and is excluded from `toString`/`equals`. Before, Bill ↔ BillItem recursed forever when serialised.
- Share links used the whole comma-separated `app.base-url` list. They now use `app.public-base-url`.
- `GET /api/bills/link/{token}` was never permitted in `SecurityConfig`, so it returned 401. It is now public (GET only).
- Bills no longer get a fake customer GSTIN/PAN (`29AAAAA0000A1Z5` / `AAAAA0000A`). They fall back to the customer's own values.
- Customer-created email: missing space fixed, and it no longer throws an NPE when `company` is null.
- Gmail credentials moved out of `application.properties` into the git-ignored `secrets.properties`. **The app password was committed in `1d9c734`, so revoke it in Google and create a new one.**

---

## 1. Project tree

```
billing-backend/
├── pom.xml                                        [MOD] mail, thymeleaf, openhtmltopdf
├── src/main/resources/
│   ├── application.yml                            [MOD] mail + app.* settings (see §7)
│   ├── db/migration/
│   │   ├── V20__users_profile_and_status.sql      [NEW]
│   │   ├── V21__bills_optional_gst_and_parties.sql [NEW]
│   │   ├── V22__customers_owner_and_link.sql      [NEW]
│   │   ├── V23__user_tokens.sql                   [NEW]
│   │   └── V24__bill_share_and_email_log.sql      [NEW]
│   ├── templates/
│   │   ├── email/
│   │   │   ├── bill.html                          [NEW] bill link + optional invite block
│   │   │   └── invite.html                        [NEW] invite only (no bill)
│   │   └── pdf/
│   │       └── invoice.html                       [NEW] GST / No-GST layout
│   └── fonts/
│       └── NotoSans-Regular.ttf                   [NEW] needed so ₹ renders in the PDF
│
└── src/main/java/com/ubs/billing/
    ├── BillingApplication.java                    [MOD] @EnableAsync @EnableScheduling
    │
    ├── config/
    │   ├── SecurityConfig.java                    [MOD] public: GET /api/bills/link/**, /api/auth/invite/**, /api/auth/set-password
    │   ├── AsyncConfig.java                       [NEW] small thread pool for mail
    │   └── AppProperties.java                     [NEW] @ConfigurationProperties("app")
    │
    ├── security/
    │   ├── JwtService.java
    │   ├── JwtAuthFilter.java
    │   ├── CurrentUser.java                       [NEW] currentUserId() from SecurityContext
    │   └── TokenGenerator.java                    [NEW] SecureRandom tokens + SHA-256 hashing
    │
    ├── auth/
    │   ├── AuthController.java                    [MOD] GET /invite/{token}, POST /set-password
    │   ├── AuthService.java                       [MOD] login by email or phone, setPassword()
    │   └── dto/
    │       ├── LoginRequest.java
    │       ├── LoginResponse.java
    │       ├── InviteInfoResponse.java            [NEW]
    │       └── SetPasswordRequest.java            [NEW]
    │
    ├── user/
    │   ├── User.java                              [MOD] status, businessName, gstin, stateCode, emailVerified
    │   ├── UserStatus.java                        [NEW] INVITED | ACTIVE | DISABLED
    │   ├── UserRepository.java                    [MOD] findByEmailIgnoreCase()
    │   ├── UserToken.java                         [NEW]
    │   ├── UserTokenRepository.java               [NEW]
    │   ├── UserProvisioningService.java           [NEW] findOrInvite(), linkPendingBills()
    │   └── ProfileController.java                 [MOD] /api/settings/profile with new fields
    │
    ├── customer/
    │   ├── Customer.java                          [MOD] owner (User), linkedUser (User)
    │   ├── CustomerRepository.java                [MOD] findByOwnerId(), findByEmailIgnoreCaseAndLinkedUserIsNull()
    │   ├── CustomerService.java                   [MOD] scope by owner, re-link when email changes
    │   ├── CustomerController.java
    │   └── dto/CustomerResponse.java              [MOD] accountStatus
    │
    ├── bill/
    │   ├── Bill.java                              [MOD] gstRate nullable, issuer, recipientUser, supplierStateCode, public link fields
    │   ├── BillRepository.java                    [MOD] findByIssuerId(), findByRecipientUserId(), findByPublicToken()
    │   ├── BillService.java                       [MOD] create/update use GstCalculator; scope by user
    │   ├── BillController.java                    [MOD] ?scope=, new endpoints (see §6)
    │   ├── BillAccessPolicy.java                  [NEW] canView / canManage
    │   ├── GstCalculator.java                     [NEW] single source of truth for totals
    │   ├── GstBreakdown.java                      [NEW] record
    │   └── dto/
    │       ├── BillRequest.java                   [MOD] gstRate nullable, sendEmail, inviteRecipient
    │       ├── BillResponse.java                  [MOD] myRole, issuer, publicUrl, lastEmailStatus
    │       └── PublicBillResponse.java            [NEW] safe subset for the public page
    │
    ├── share/
    │   ├── ShareLinkService.java                  [MOD] getOrCreate(), revoke(), absolute URL
    │   ├── ShareController.java                   [MOD] /api/share/* calls ShareLinkService
    │   └── PublicBillController.java              [NEW] GET /api/bills/link/{token}[/pdf]
    │
    ├── mail/
    │   ├── BillMailService.java                   [NEW] queue email, build content
    │   ├── MailSenderService.java                 [NEW] JavaMailSender wrapper
    │   ├── BillEmailLog.java                      [NEW]
    │   ├── BillEmailLogRepository.java            [NEW]
    │   ├── BillEmailRequestedEvent.java           [NEW]
    │   ├── BillEmailListener.java                 [NEW] @Async @TransactionalEventListener(AFTER_COMMIT)
    │   └── EmailRetryJob.java                     [NEW] @Scheduled resend FAILED (max 3 attempts)
    │
    ├── pdf/
    │   ├── InvoicePdfService.java                 [NEW] Thymeleaf HTML → PDF
    │   └── AmountInWords.java                     [NEW] lakh/crore words (same output as currency-utils.ts)
    │
    ├── payment/
    │   └── PaymentService.java                    [MOD] only the issuer can record payment on a bill
    ├── report/
    │   └── ReportService.java                     [MOD] scope to issuer; separate Non-GST sales
    ├── analytics/
    │   └── AnalyticsService.java                  [MOD] scope to issuer
    ├── notification/
    │   ├── NotificationType.java                  [MOD] + BILL_RECEIVED, BILL_EMAILED, BILL_VIEWED
    │   └── NotificationService.java               [MOD]
    ├── settings/
    │   └── SystemSettings.java                    [MOD] gstRates, defaultGstRate (nullable), autoEmailOnCreate
    └── common/
        ├── ApiError.java
        └── GlobalExceptionHandler.java            [MOD] 404 for no access, 410 for expired links, 422 for incomplete profile
```

---

## 2. Database migrations (written for MySQL; this repo uses PostgreSQL)

> **This repo has no Flyway.** `ddl-auto=update` adds new nullable columns automatically, and that is all Phase 1 needs (`system_settings.gst_rates`). `bills.gst_rate` was already nullable.
> It **cannot** backfill data, add `NOT NULL` to existing columns, or drop constraints. Before Phase 3 (`issuer_user_id NOT NULL`), add Flyway (`flyway-core` + `flyway-database-postgresql`, `spring.flyway.baseline-on-migrate=true`) and switch `ddl-auto` to `validate`.
> When porting the SQL below to PostgreSQL: `MODIFY col TYPE NULL` → `ALTER COLUMN col DROP NOT NULL`, `DATETIME` → `TIMESTAMP`, `BIGINT AUTO_INCREMENT` → `BIGSERIAL`, and user/bill FKs → `VARCHAR(20)`/`VARCHAR(30)`.

Use your next free Flyway numbers. Column names are guesses, so match them to your schema. The backfill lines assume existing data belongs to the first user. If `bills` already has a `created_by` column, use that instead.

### `V20__users_profile_and_status.sql`

```sql
ALTER TABLE users
  ADD COLUMN status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',   -- INVITED | ACTIVE | DISABLED
  ADD COLUMN email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
  ADD COLUMN business_name   VARCHAR(150) NULL,
  ADD COLUMN gstin           VARCHAR(15)  NULL,
  ADD COLUMN state_code      CHAR(2)      NULL,
  ADD COLUMN address         VARCHAR(500) NULL,
  MODIFY password_hash       VARCHAR(100) NULL;                      -- invited users have no password yet

UPDATE users SET email_verified = TRUE WHERE status = 'ACTIVE';      -- existing users
UPDATE users SET state_code = '29' WHERE state_code IS NULL;         -- the old hard-coded supplier state
CREATE UNIQUE INDEX ux_users_email ON users (email);
CREATE INDEX ix_users_phone ON users (phone);
```

### `V21__bills_optional_gst_and_parties.sql`

```sql
ALTER TABLE bills
  MODIFY gst_rate            DECIMAL(5,2) NULL,                      -- NULL = No GST
  ADD COLUMN issuer_user_id      BIGINT  NULL,
  ADD COLUMN recipient_user_id   BIGINT  NULL,
  ADD COLUMN supplier_state_code CHAR(2) NULL,                       -- snapshot of issuer state at creation
  ADD CONSTRAINT fk_bills_issuer    FOREIGN KEY (issuer_user_id)    REFERENCES users(id),
  ADD CONSTRAINT fk_bills_recipient FOREIGN KEY (recipient_user_id) REFERENCES users(id);

UPDATE bills SET issuer_user_id = (SELECT MIN(id) FROM users) WHERE issuer_user_id IS NULL;
UPDATE bills SET supplier_state_code = '29' WHERE supplier_state_code IS NULL;

ALTER TABLE bills
  MODIFY issuer_user_id      BIGINT  NOT NULL,
  MODIFY supplier_state_code CHAR(2) NOT NULL;

CREATE INDEX ix_bills_issuer    ON bills (issuer_user_id, created_at);
CREATE INDEX ix_bills_recipient ON bills (recipient_user_id, created_at);
```

### `V22__customers_owner_and_link.sql`

```sql
ALTER TABLE customers
  ADD COLUMN owner_user_id  BIGINT NULL,     -- whose address book this customer is in
  ADD COLUMN linked_user_id BIGINT NULL,     -- the customer's own login, if any
  ADD CONSTRAINT fk_customers_owner  FOREIGN KEY (owner_user_id)  REFERENCES users(id),
  ADD CONSTRAINT fk_customers_linked FOREIGN KEY (linked_user_id) REFERENCES users(id);

UPDATE customers SET owner_user_id = (SELECT MIN(id) FROM users) WHERE owner_user_id IS NULL;
ALTER TABLE customers MODIFY owner_user_id BIGINT NOT NULL;

CREATE INDEX ix_customers_owner ON customers (owner_user_id);
CREATE INDEX ix_customers_email ON customers (email);
```

> The same person can be a customer in many address books, one row per owner. All of those rows point to one `linked_user_id`.

### `V23__user_tokens.sql`

```sql
CREATE TABLE user_tokens (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT      NOT NULL,
  purpose     VARCHAR(20) NOT NULL,             -- INVITE | RESET_PASSWORD
  token_hash  CHAR(64)    NOT NULL UNIQUE,      -- SHA-256 of the raw token; the raw token is only in the email
  expires_at  DATETIME    NOT NULL,
  used_at     DATETIME    NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

### `V24__bill_share_and_email_log.sql`

```sql
-- If the existing unique link column is short or guessable, regenerate it with TokenGenerator.
ALTER TABLE bills
  ADD COLUMN public_created_at DATETIME NULL,
  ADD COLUMN public_expires_at DATETIME NULL,          -- NULL = never expires
  ADD COLUMN public_revoked    BOOLEAN  NOT NULL DEFAULT FALSE;

CREATE TABLE bill_email_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  bill_id     BIGINT       NOT NULL,
  sent_by     BIGINT       NOT NULL,
  to_email    VARCHAR(254) NOT NULL,
  cc_emails   VARCHAR(1000) NULL,
  message     VARCHAR(1000) NULL,
  attach_pdf  BOOLEAN      NOT NULL DEFAULT TRUE,
  type        VARCHAR(20)  NOT NULL,                   -- BILL | BILL_WITH_INVITE
  status      VARCHAR(10)  NOT NULL,                   -- QUEUED | SENT | FAILED
  attempts    INT          NOT NULL DEFAULT 0,
  error       VARCHAR(500) NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  sent_at     DATETIME     NULL,
  CONSTRAINT fk_email_bill FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE,
  CONSTRAINT fk_email_user FOREIGN KEY (sent_by) REFERENCES users(id)
);
CREATE INDEX ix_email_retry ON bill_email_log (status, attempts);
```

---

## 3. Feature 1 — Optional GST ✅ implemented

**As built in this repo**

| File | Change |
|------|--------|
| `service/GstCalculator.java` [NEW], `dto/GstBreakdown.java` [NEW] | Totals as below. Subtotal = sum of the *rounded* line amounts. `sgst = tax − cgst`. |
| `entity/Bill.java` [MOD] | `gstRate` has no default (NULL = No GST); JSON adds `gstApplicable` |
| `dto/CreateBillRequest.java` [MOD] | `gstRate` is `BigDecimal`. **`"gstRate": null` → No GST. Key omitted → `SystemSettings.taxRate` on create, unchanged on update.** `isInterState` removed. |
| `service/BillService.java` [MOD] | Create/update go through `GstCalculator`. **Inter-state is derived** from `app.supplier.state-code` vs. `stateCode` (request, else the customer's). Changing only `gstRate` on update recalculates totals. |
| `entity/SystemSettings.java` [MOD] + `DecimalListConverter` [NEW] | `gstRates` stored as `"0,5,18,40"` in `gst_rates`. NULL falls back to `SettingsService.DEFAULT_GST_RATES`. |
| `service/ReportsService.java` [MOD] | `/api/reports/stats` adds `gstCollected` (paid GST bills) and `nonGstSales` (paid No-GST bills) |
| `test/.../GstCalculatorTest.java` [NEW] | No GST, CGST+SGST = tax rounding, IGST |

> Frontend impact: 12% and 28% are no longer accepted (400) unless you add them back with `PUT /api/settings/system { "gstRates": [...] }`. A "No GST" option must send `gstRate: null`. Use `gstApplicable` to hide the tax rows.

**Rules**

- In `BillRequest.gstRate` (`BigDecimal`, nullable), `null` means No GST. Any other value must be in `SystemSettings.gstRates`, for example `[0, 5, 18, 40]`. The main slabs since 22 Sept 2025 are 0%, 5%, 18% and 40%, and special rates like 3% (gold) can be added.
- **The server always recomputes** subtotal, tax and total with `GstCalculator`. Any totals sent by the client are ignored.
- When the bill has No GST, the PDF, email and public page hide the CGST/SGST/IGST rows and show "GST not applicable". The title can be "Bill of Supply".
- Reports keep No-GST bills out of tax totals and list them as "Non-GST sales".

### `bill/GstCalculator.java` [NEW]

```java
@Component
public class GstCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    public GstBreakdown calculate(List<BillItem> items, BigDecimal gstRate,
                                  String supplierStateCode, String placeOfSupplyStateCode) {

        BigDecimal subtotal = items.stream()
                .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        boolean interState = !Objects.equals(supplierStateCode, placeOfSupplyStateCode);

        if (gstRate == null) {                                   // No GST
            return new GstBreakdown(subtotal, null, interState,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, subtotal);
        }

        // gstRate is a percentage (18.00). If yours is a fraction (0.18), remove the divide by 100.
        BigDecimal tax = subtotal.multiply(gstRate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal cgst = BigDecimal.ZERO, sgst = BigDecimal.ZERO, igst = BigDecimal.ZERO;

        if (interState) {
            igst = tax;
        } else {
            cgst = tax.divide(TWO, 2, RoundingMode.HALF_UP);
            sgst = tax.subtract(cgst);                           // cgst + sgst == tax exactly
        }
        return new GstBreakdown(subtotal, gstRate, interState, cgst, sgst, igst, tax, subtotal.add(tax));
    }
}

public record GstBreakdown(BigDecimal subtotal, BigDecimal gstRate, boolean interState,
                           BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
                           BigDecimal taxTotal, BigDecimal total) {}
```

---

## 4. Feature 2 — Billing loop

```
A creates bill for customer B (email b@x.com)
  └─ BillService.create()
       ├─ supplierStateCode = A.stateCode          (422 PROFILE_INCOMPLETE if missing)
       ├─ totals = GstCalculator.calculate(...)
       ├─ recipient = UserProvisioningService.findOrInvite(customer, invite = request.inviteRecipient)
       │     ├─ user with b@x.com exists → link it
       │     ├─ not found + invite=true  → create User(status=INVITED, no password) + INVITE token
       │     └─ not found + invite=false → recipient = null
       ├─ bill.recipientUser = recipient; customer.linkedUser = recipient
       ├─ notification BILL_RECEIVED → recipient (if ACTIVE)
       └─ if sendEmail or invite created → publish BillEmailRequestedEvent
                                              (one email: bill link + set-password link)

B opens /set-password?token=… → POST /api/auth/set-password
  └─ AuthService.setPassword()
       ├─ token valid, not used, not expired (else 410)
       ├─ password = BCrypt, status = ACTIVE, emailVerified = true, token.usedAt = now
       ├─ UserProvisioningService.linkPendingBills(B)   ← picks up bills from A, C, D… sent to b@x.com
       └─ return LoginResponse (JWT + user)              ← same as /login

B creates bill for C → same flow → C logs in → … (loop)
```

### Who can do what — `bill/BillAccessPolicy.java` [NEW]

| Action | Issuer | Recipient | Public link |
|--------|:------:|:---------:|:-----------:|
| View bill | Yes | Yes | Yes (safe subset) |
| Download PDF | Yes | Yes | Yes |
| Edit / delete | Yes | No | No |
| Mark paid / record payment | Yes | No | No |
| Create share link / send email | Yes | No | No |

- If the user can't view a bill, return **404, not 403**, so that bill IDs can't be probed.
- Every query is scoped with `CurrentUser.currentUserId()`:
  - customers by `owner_user_id`
  - bills by `issuer_user_id`, or by `recipient_user_id` for `scope=received`
  - payments, reports, analytics and stats by issuer
  - notifications by user

### `user/UserProvisioningService.java` [NEW] — key rules

```java
@Transactional
public User findOrInvite(Customer customer, boolean invite) {
    String email = normalise(customer.getEmail());           // trim + lower-case
    if (email == null) return null;
    if (email.equals(currentUser.email())) return null;      // don't link a bill to yourself

    return userRepository.findByEmailIgnoreCase(email)
        .orElseGet(() -> invite ? createInvitedUser(customer) : null);
}

@Transactional
public void linkPendingBills(User user) {
    // 1. customers.linked_user_id = user.id  WHERE lower(email) = user.email AND linked_user_id IS NULL
    // 2. bills.recipient_user_id   = user.id  WHERE customer_id IN (those customers) AND recipient_user_id IS NULL
}
```

- **Link by email only**, never by phone alone. Otherwise anyone who registers with someone else's phone number could see their bills. Add phone linking only after phone OTP verification exists.
- **Received bills are only listed when `email_verified = true`.** Completing set-password through the emailed link is what proves the email belongs to the user.
- When an issuer changes a customer's email, re-run linking for that customer and clear the old link.
- `POST /auth/register` (no password today) can use the same flow: create the user as INVITED and email a set-password link.

---

## 5. Feature 3 — Public link + email

> **Done so far in this repo**
> - New bills get `unique_link = TokenGenerator.urlSafe(32)` (`util/TokenGenerator.java`).
> - `POST /api/bills/generate-link` replaces old guessable `bill-<name>-<digits>` links with a random token the first time it is called. The old link then stops working.
> - `controller/PublicBillController` serves `GET /api/bills/link/{token}` and the legacy `GET /api/bill/public/{token}`. Both return `dto/PublicBillResponse`, which has the same field names as `Bill` plus an `issuer` block, and no `customerId`, notes or link.
> - `PATCH /api/bill/public/{token}/pay` and `PaymentService.simulatePublicPayment` are **removed**. Payment needs login (`POST /api/payments/process` or `PATCH /api/bills/{id}/mark-paid`).
> - Not yet done: expiry/revoke columns (410), PDF and email.

### `share/ShareLinkService.java` [MOD]

```java
public ShareLink getOrCreate(Bill bill) {
    if (bill.getPublicToken() == null || bill.isPublicRevoked()) {
        bill.setPublicToken(tokenGenerator.urlSafe(32));     // 32 random bytes → 43-char Base64URL
        bill.setPublicCreatedAt(now());
        bill.setPublicExpiresAt(props.shareLinkTtl() == null ? null : now().plus(props.shareLinkTtl()));
        bill.setPublicRevoked(false);
    }
    String url = props.publicBaseUrl() + "/bill/view/" + bill.getPublicToken();
    return new ShareLink(url, bill.getPublicExpiresAt());
}
```

- The URL is **absolute**, built from `app.public-base-url`. The email has to work outside your network, so it can't use the API host.
- `POST /share/generate` (used by the existing Share page) calls the same method, so old and new screens give the same link.

### Public endpoints — `share/PublicBillController.java` [NEW]

| Method | Path | Returns |
|--------|------|---------|
| GET | `/api/bills/link/{token}` | `PublicBillResponse`: bill number, dates, items, totals, GST rows (or none), issuer business name/GSTIN/address, customer name/address, status. **No** internal IDs, notes, payment history or other customers' data. |
| GET | `/api/bills/link/{token}/pdf` | `application/pdf`, `Content-Disposition: inline; filename="INV-0012.pdf"` |

- Unknown token returns `404`. Expired or revoked returns `410 Gone`.
- Optional: the first view each day creates a `BILL_VIEWED` notification for the issuer.
- **Remove public access to `PATCH /bills/{id}/mark-paid`.** It is issuer-only and requires login.

### Sending the email

```
POST /api/bills/{id}/send-email  { to?, cc?, message?, attachPdf? }
  └─ BillMailService.queue()
       ├─ BillAccessPolicy: issuer only
       ├─ ShareLinkService.getOrCreate(bill)
       ├─ save BillEmailLog(status = QUEUED)
       └─ publish BillEmailRequestedEvent(logId)
            └─ BillEmailListener  @Async @TransactionalEventListener(AFTER_COMMIT)
                 ├─ render templates/email/bill.html (Thymeleaf)
                 ├─ if attachPdf → InvoicePdfService.render(bill)
                 ├─ MailSenderService.send(...)
                 └─ log.status = SENT (sentAt) | FAILED (error, attempts++)
                      └─ EmailRetryJob every 5 min: FAILED with attempts < 3 → send again
  ← 202 Accepted { emailLogId, status: "QUEUED" }
```

- Sending happens **after commit** and **asynchronously**, so a mail-server outage never fails bill creation.
- `POST /api/bills` with `sendEmail: true` uses the same path.
- If an invite token was also created, the email `type` is `BILL_WITH_INVITE` and the email includes a "Create your login" button. The customer gets one email, not two.

**Email (`templates/email/bill.html`) — variables**

| Variable | Example |
|----------|---------|
| `issuerName` | Sharma Traders |
| `billNumber` | INV-0012 |
| `billDate`, `dueDate` | 28 Oct 2026 |
| `total`, `totalInWords` | ₹12,345.00 · Twelve Thousand Three Hundred Forty-Five Rupees Only |
| `gstApplicable` | false → "GST not applicable" |
| `viewUrl` | https://app.example.com/bill/view/Xy… |
| `inviteUrl` | https://app.example.com/set-password?token=… (only for BILL_WITH_INVITE) |
| `message` | issuer's optional note |

Subject: `Invoice {billNumber} from {issuerName} – {total}`

**PDF (`pdf/InvoicePdfService.java`)**

- Thymeleaf renders `templates/pdf/invoice.html`, and OpenHTMLtoPDF turns it into a PDF.
- Embed `fonts/NotoSans-Regular.ttf`. The default PDF fonts don't include the ₹ sign.
- `AmountInWords.java` must give the same text as `currency-utils.ts` (lakh/crore).

---

## 6. API reference (new and changed)

| Method | Path | Auth | Request | Response |
|--------|------|------|---------|----------|
| GET | `/api/bills?scope=sent\|received&status=&customerId=` | user | — | `BillResponse[]`, each with `myRole` |
| GET | `/api/bills/stats?scope=` | user | — | stats for that scope |
| GET | `/api/bills/{id}` | issuer or recipient | — | `BillResponse` / 404 |
| POST | `/api/bills` | user (profile complete) | `BillRequest` (below) | `201 BillResponse` / 422 |
| PUT | `/api/bills/{id}` | issuer | `BillRequest` | `BillResponse` |
| DELETE | `/api/bills/{id}` | issuer | — | 204 |
| PATCH | `/api/bills/{id}/mark-paid` | issuer | — | `BillResponse` |
| POST | `/api/bills/{id}/share-link` | issuer | `{ regenerate?: boolean }` | `{ url, expiresAt }` |
| DELETE | `/api/bills/{id}/share-link` | issuer | — | 204 (revoke) |
| POST | `/api/bills/{id}/send-email` | issuer | `{ to?, cc?, message?, attachPdf? }` | `202 { emailLogId, status }` |
| GET | `/api/bills/{id}/emails` | issuer | — | `BillEmailLog[]` |
| GET | `/api/bills/link/{token}` | **public** | — | `PublicBillResponse` / 404 / 410 |
| GET | `/api/bills/link/{token}/pdf` | **public** | — | PDF / 404 / 410 |
| GET | `/api/auth/invite/{token}` | **public** | — | `{ name, email, invitedBy }` / 410 |
| POST | `/api/auth/set-password` | **public** | `{ token, password }` | `LoginResponse` / 410 |
| POST | `/api/auth/login` | public | `{ username (email or phone), password, captchaToken }` | `LoginResponse` |
| GET/PUT | `/api/settings/profile` | user | + `businessName, gstin, stateCode, address` | profile |
| GET/PUT | `/api/settings/system` | user | + `gstRates, defaultGstRate (nullable), autoEmailOnCreate` | settings |
| GET | `/api/customers` | user (own) | — | + `accountStatus: NONE\|INVITED\|REGISTERED` |

### `BillRequest` [MOD]

```json
{
  "customerId": 42,
  "items": [{ "description": "Consulting", "quantity": 2, "price": 5000 }],
  "gstRate": null,
  "sendEmail": true,
  "inviteRecipient": true
}
```

Validation rules:
- `gstRate` must be `null` or in the allowed list.
- `quantity` must be greater than 0 and `price` 0 or more.
- The customer must belong to the current user, otherwise return 404.

---

## 7. Configuration

### `application.yml` [MOD]

> This repo uses `application.properties`. It already has `app.public-base-url=${APP_PUBLIC_BASE_URL:http://localhost:4200}` and `spring.mail.*`, with username and password read from `secrets.properties` (git-ignored, loaded via `spring.config.import`) or `MAIL_USERNAME`/`MAIL_PASSWORD`. `spring-boot-starter-mail` is already in `pom.xml`. Only Thymeleaf and OpenHTMLtoPDF are still needed (Phase 5).
> The DB password, `jwt.secret` and `recaptcha.secret-key` are still in the tracked `application.properties`. Move them the same way.

```yaml
spring:
  mail:
    host: ${MAIL_HOST:smtp.gmail.com}
    port: ${MAIL_PORT:587}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}          # Gmail: use an App Password, not the account password
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

app:
  public-base-url: ${APP_PUBLIC_BASE_URL:http://localhost:4200}   # frontend URL used in emails
  mail-from: ${APP_MAIL_FROM:UBS Billing <no-reply@example.com>}
  invite-token-ttl: 72h
  share-link-ttl:                       # empty = links never expire; e.g. 30d
  email-max-attempts: 3
```

For production, use a transactional email provider (Amazon SES, SendGrid, Brevo, etc.) and set SPF/DKIM on your domain so bills don't land in spam.

### `pom.xml` [MOD]

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
<dependency>
  <groupId>com.openhtmltopdf</groupId>
  <artifactId>openhtmltopdf-pdfbox</artifactId>
  <version>1.0.10</version>
</dependency>
```

### `SecurityConfig.java` [MOD]

```java
.requestMatchers(HttpMethod.GET,  "/api/bills/link/**").permitAll()
.requestMatchers(HttpMethod.GET,  "/api/auth/invite/**").permitAll()
.requestMatchers(HttpMethod.POST, "/api/auth/set-password", "/api/auth/login", "/api/auth/register").permitAll()
.anyRequest().authenticated()
```

---

## 8. Security checklist

- [ ] All bill, customer, payment, report and notification queries are scoped to the current user.
- [ ] A bill with no access returns 404, not 403.
- [x] Public link tokens come from `SecureRandom` (32 bytes) and are never sequential IDs. *(Old links are replaced on the next `generate-link`.)*
- [ ] Invite/reset tokens are stored as SHA-256 hashes, are single use, and expire in 72h.
- [ ] Recipients are linked by verified email only, never by phone alone.
- [x] `mark-paid` requires login and is not reachable from the public link. *(Issuer-only waits for Phase 3.)*
- [x] `PublicBillResponse` exposes no internal IDs, notes or payment history.
- [x] Bill totals are always recomputed on the server.
- [ ] Rate-limit `POST /send-email` (e.g. 10 per bill per day) and `/auth/set-password`.
- [x] Mail credentials are not in tracked config. *(Rotate the Gmail app password: it is in git history.)*

---

## 9. Build order

1. ✅ **Feature 1:** migration `V21` (the nullable `gst_rate` part), then `GstCalculator`, then `BillRequest`/`BillService`, then the system-settings rate list. This can ship on its own.
2. **Profile:** migration `V20`, then the profile fields, then `supplierStateCode` on bills (replacing `'29'`).
3. **Ownership:** migrations `V21` and `V22` (owner/issuer columns), then `CurrentUser`, then scope every repository query, then `BillAccessPolicy`.
4. **Invites:** migration `V23`, then `TokenGenerator`, `UserProvisioningService` and the `AuthController` endpoints.
5. **Share + email:** migration `V24`, then `ShareLinkService`, `PublicBillController`, `InvoicePdfService`, the mail classes and `EmailRetryJob`.
6. Lock down `mark-paid`, add rate limits, and work through the §8 checklist.