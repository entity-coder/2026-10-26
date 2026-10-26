package com.billbook.app.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Today's date as stored in the database (yyyy-MM-dd). */
fun todayString(): String = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

/**
 * All billing rules live here, not in fragments, so the UI can never apply
 * them inconsistently:
 *  - Saving a draft never touches stock.
 *  - Printing deducts stock and refuses if any item exceeds stock.
 *  - Editing a printed bill first reverses its stock effect, then it is a
 *    draft again; re-printing deducts the new items.
 *  - Bill numbers are assigned at print time (BB-0001, ...) and kept on
 *    re-print, so an edited bill is never renumbered.
 *  - Khata dues are computed, never stored:
 *    sum(printed Credit bills) - sum(payments).
 */
class BillRepository(val db: BillDatabase) {

    // ---- Customers -------------------------------------------------------

    /** Find the customer by name, or create the row (billing auto-creates). */
    suspend fun getOrCreateCustomer(name: String): Customer {
        val trimmed = name.trim()
        return db.customerDao().getByName(trimmed)
            ?: run {
                val id = db.customerDao().insert(Customer(name = trimmed))
                db.customerDao().getById(id)!!
            }
    }

    /** Dues for one customer, computed live from printed Credit bills minus payments. */
    suspend fun customerDue(customerId: Long): Double {
        val owed = db.billDao().printedCreditBills(customerId).sumOf { it.total }
        val paid = db.paymentDao().totalPaid(customerId)
        return round2(owed - paid)
    }

    // ---- Products / stock ------------------------------------------------

    /** Add stock and log the movement. Used by the Products tab "Stock In". */
    suspend fun stockIn(product: Product, qty: Double) {
        db.productDao().update(product.copy(stock = round2(product.stock + qty)))
        db.stockMovementDao().insert(
            StockMovement(
                productId = product.id,
                productName = product.name,
                change = qty,
                reason = "Stock In",
                date = todayString(),
            )
        )
    }

    // ---- Bills -----------------------------------------------------------

    /** Result of attempting to print: either success, or the short products. */
    sealed class PrintResult {
        object Success : PrintResult()
        data class InsufficientStock(val shortProducts: List<String>) : PrintResult()
    }

    /**
     * Print a draft bill. Refuses (naming the short products) if any line's
     * qty exceeds current stock. Otherwise deducts stock, logs a movement
     * per line, assigns the bill number if this bill has none yet, freezes
     * the grand total and marks the bill PRINTED.
     */
    suspend fun printBill(billId: Long): PrintResult {
        val bill = db.billDao().getById(billId) ?: return PrintResult.Success
        val items = db.billItemDao().getForBill(billId)
        val short = mutableListOf<String>()
        val products = mutableMapOf<Long, Product>()
        for (item in items) {
            val product = db.productDao().getById(item.productId) ?: continue
            products[item.productId] = product
            if (item.qty > product.stock) {
                short += "${item.productName} (needs ${item.qty} ${item.unit}, have ${product.stock})"
            }
        }
        if (short.isNotEmpty()) return PrintResult.InsufficientStock(short)

        val totals = computeBillTotals(
            items.map { PricedLine(lineAmount(it.qty, it.unitPrice), it.gstPercent) },
            bill.discountPercent,
        )
        val number = bill.billNumber ?: nextBillNumber()
        for (item in items) {
            val product = products[item.productId] ?: continue
            db.productDao().update(product.copy(stock = round2(product.stock - item.qty)))
            db.stockMovementDao().insert(
                StockMovement(
                    productId = product.id,
                    productName = product.name,
                    change = -item.qty,
                    reason = "Bill $number",
                    date = todayString(),
                )
            )
        }
        db.billDao().update(
            bill.copy(
                status = BillStatus.PRINTED,
                billNumber = number,
                total = totals.grandTotal,
                printedDate = todayString(),
            )
        )
        return PrintResult.Success
    }

    /**
     * Edit-after-print: reverse the printed bill's stock effect (items added
     * back, movement logged) and set it back to DRAFT. The bill keeps its
     * number, so re-printing reuses it. Dues update automatically because
     * they are computed from printed bills only.
     */
    suspend fun revertPrintedBillToDraft(billId: Long) {
        val bill = db.billDao().getById(billId) ?: return
        if (bill.status != BillStatus.PRINTED) return
        val items = db.billItemDao().getForBill(billId)
        for (item in items) {
            val product = db.productDao().getById(item.productId) ?: continue
            db.productDao().update(product.copy(stock = round2(product.stock + item.qty)))
            db.stockMovementDao().insert(
                StockMovement(
                    productId = product.id,
                    productName = product.name,
                    change = item.qty,
                    reason = "Edit reversal ${bill.billNumber ?: ""}".trim(),
                    date = todayString(),
                )
            )
        }
        db.billDao().update(
            bill.copy(status = BillStatus.DRAFT, printedDate = null, total = 0.0)
        )
    }

    /** Next bill number: max existing numeric part + 1, formatted BB-0001. */
    private suspend fun nextBillNumber(): String {
        val max = db.billDao().allBillNumbers()
            .mapNotNull { it.removePrefix("BB-").toIntOrNull() }
            .maxOrNull() ?: 0
        return "BB-%04d".format(max + 1)
    }

    /** Totals for a bill's current lines (used live on the billing screen). */
    suspend fun totalsFor(billId: Long, discountPercent: Double): BillTotals {
        val items = db.billItemDao().getForBill(billId)
        return computeBillTotals(
            items.map { PricedLine(lineAmount(it.qty, it.unitPrice), it.gstPercent) },
            discountPercent,
        )
    }
}
