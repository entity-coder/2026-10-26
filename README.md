# BillBook Android

Wholesale billing on a phone — the Android port of the desktop BillBook app.
Offline-first: everything lives in a local Room database, no account, no
server, no internet needed.

> **Not compiled or device-tested here.** This project was written on a
> machine with no Android SDK, so it has never been built or run on a device
> from this workspace. Open it in Android Studio (steps below) and run it
> there — that is the first real build and test.

## What it does

- **Dashboard tab** — today's sales total (printed bills dated today), total
  pending dues across all customers, and low-stock alerts. The shop profile
  (name, address, GSTIN, phone) is edited from here.
- **Products tab** — product catalog with create / edit / delete. Each
  product has a name, unit (kg, box, pcs…), wholesale price, retail price,
  GST %, stock, and a low-stock threshold. **Stock In** adds stock and logs
  a stock movement (the audited path for increasing stock).
- **Billing tab** — a bill can only be started after a customer name and a
  payment type (Cash / UPI / Credit) are given. Multiple draft bills stay
  open at once and are switched with chips. From the product picker, each
  line picks wholesale or retail pricing. A discount % applies to the
  subtotal, and the GST breakup (taxable value, CGST, SGST) is shown live.
  Prices are GST-inclusive: per line, taxable = amount / (1 + GST %) and GST
  = amount − taxable, split equally into CGST/SGST.
- **Bills tab** — printed bills, newest first. Tapping one opens its detail
  screen with **Print** (Android PrintManager), **Share as PDF**
  (PdfDocument written to cache and shared via FileProvider), and
  **Edit-after-print**.
- **Khata tab** — one row per customer with their dues, a record-payment
  dialog (Cash / UPI / Bank), and payment history.
- **Shop profile** — a single-row table edited from the Dashboard and shown
  on every bill detail screen and PDF.

## The rules that matter (all implemented in `BillRepository`)

- **Saving a draft never touches stock.** Only printing deducts stock.
- **Printing refuses if any item exceeds stock**, and names the short
  products (e.g. "Rice (needs 10 kg, have 4 kg)").
- **Edit-after-print, exactly:** editing a printed bill first adds its items
  back to stock (logged as an "Edit reversal" movement) and sets the bill
  back to DRAFT. Re-printing deducts the new items.
- **Bill numbers:** a bill has no number while it is a draft. At print time
  it is assigned the next number (BB-0001, BB-0002, … — max existing + 1).
  An edited bill **keeps** the number it was first printed with, so a
  re-print never renumbers it.
- **Khata dues are never stored.** A customer's due is always computed as
  (sum of their printed Credit bills) − (sum of their payments), so edits
  and payments flow through automatically.
- **All money math lives in one file** (`data/Money.kt`): rounding to
  2 decimals, HALF_UP, everywhere. No screen computes a total its own way.

## How to open and run it in Android Studio

1. Open Android Studio → **Open** → select this project folder.
2. Let Gradle sync finish (it downloads the Android Gradle Plugin 8.2.2,
   Kotlin 1.9.22, Room 2.6.1 and Material 1.11.0 on first sync).
3. Connect a phone with USB debugging, or start an emulator
   (API 24 or higher; target API 34).
4. Press **Run**. No API keys, no setup, no sign-in — the database is
   created empty on first launch.
5. Suggested first run: Dashboard → Edit shop profile → Products → add a
   product → Billing → start a bill → Print it → Bills → open it → Share
   as PDF.

## Tech

- Kotlin, ViewBinding, fragments + `BottomNavigationView` (no Compose)
- Room (single `BillDatabase`) with entities Product, Customer, Bill,
  BillItem, Payment, StockMovement, ShopProfile; DAOs expose Flow queries
- `minSdk 24`, `targetSdk 34`, `compileSdk 34`
- Adaptive launcher icon is XML only (no PNG assets anywhere in the repo)

## Project structure

```
app/src/main/java/com/billbook/app/
├── data/
│   ├── Money.kt          # the only money-math code in the app
│   ├── Entities.kt       # Room entities + bill/item relation
│   ├── Daos.kt           # DAO interfaces with Flow queries
│   ├── BillDatabase.kt   # single Room database
│   └── BillRepository.kt # stock/print/edit/dues rules
└── ui/
    ├── MainActivity.kt       # 5 bottom-nav tabs
    ├── DashboardFragment.kt  # today's sales, dues, low stock
    ├── ProductsFragment.kt   # catalog + Stock In
    ├── BillingFragment.kt    # drafts, picker, live GST breakup
    ├── KhataFragment.kt      # per-customer dues + payments
    ├── BillsFragment.kt      # printed bills, newest first
    ├── BillDetailActivity.kt # print / share PDF / edit-after-print
    ├── BillPdf.kt            # one PDF renderer for print + share
    ├── Dialogs.kt            # product, stock-in, shop, payment dialogs
    └── Adapters.kt           # RecyclerView adapters
```

## Direct APK download (once the repo owner adds the workflow)

The repo owner adds `.github/workflows/build-apk.yml` via the GitHub web UI
(this integration cannot write workflow files). After the first successful
build, the rolling release provides:

```
https://github.com/entity-coder/2026-10-26/releases/download/apk-latest/BillBook-v1.0.apk
```

That link only works after the release actually exists — do not share it as
working before then.

## Interview notes

**What is this project?** An offline-first Android billing app for wholesale
shops: products with wholesale/retail pricing and GST, multi-draft billing,
a customer khata (dues ledger), bill history with print/share/edit, and a
shop profile printed on every bill.

**Why Room with a single database?** One database keeps the seven related
tables (products, customers, bills, items, payments, movements, profile)
consistent in one place, and Room's Flow queries mean every tab updates
itself when data changes — no manual refresh calls. The alternative (a file
per screen, or SharedPreferences) would scatter the billing rules and make
the stock/dues invariants impossible to guarantee.

**Why is all money math in one file?** Because billing bugs are almost
always "two screens computed the total differently". `Money.kt` owns
rounding (2 decimals, HALF_UP — the rounding a shopkeeper expects, not
banker's rounding), the GST-inclusive breakup, and the discount-first
ordering. Every screen, and the PDF, calls the same functions, so the number
on screen, on paper, and in the khata can never drift apart.

**Why are dues computed instead of stored?** A stored dues column goes
stale the moment a bill is edited, a payment is recorded, or stock is
reversed. Computing dues as printed Credit bills minus payments means an
edit-after-print automatically corrects the khata — there is no second
number to keep in sync, and no migration if the rule changes.

**Why is the bill number assigned at print time, not at draft creation?**
Drafts are thrown away all the time; numbering them would leave holes in
the BB-0001 sequence and confuse a shop's paper records. Numbering at
print time (max + 1) keeps the printed sequence gap-free, and keeping the
number across an edit means the paper trail for one sale never changes.

**Main trade-offs / limitations:**

- No barcode scanning, no UPI payment collection, no cloud sync or backup
  — the app is deliberately offline-only, so a shop with two phones would
  have two separate ledgers.
- Stock is a single quantity per product (no batches, no expiry, no godown
  split), and GST is a single rate per product (no HSN codes, no
  inter-state IGST — the breakup is always CGST + SGST).
- The print path uses Android's PrintManager, so it prints to whatever
  printer the phone can reach; there is no built-in 80mm thermal formatting
  like the desktop app has.
- It has not been compiled or run on a device from this workspace (no
  Android SDK here), so the first Android Studio build may surface small
  build issues that still need fixing.

**How I'd extend it:** barcode scan to add lines (CameraX + ML Kit),
per-product HSN + IGST for inter-state sales, an export/backup to a file
the shop can move to a new phone, and a simple daily sales report screen
from the printed-bills table that already exists.
