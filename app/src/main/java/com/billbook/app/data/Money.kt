package com.billbook.app.data

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The single place for all money math in BillBook. Every screen, DAO result
 * and PDF line goes through here, so totals can never drift between views.
 *
 * Rules (GST-inclusive prices, as on a wholesale bill):
 *  - A line amount is qty * unit price (the price the customer actually pays).
 *  - Discount % applies to the subtotal first.
 *  - On the discounted amount, taxable = amount / (1 + gst%) and
 *    gst = amount - taxable, split equally into CGST and SGST.
 *  - Every intermediate value is rounded to 2 decimals, HALF_UP.
 */

/** Round to 2 decimals, HALF_UP (e.g. 10.005 -> 10.01, never banker's rounding). */
fun round2(value: Double): Double =
    BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()

fun Double.round2(): Double = round2(this)

/** One bill line as priced on screen: qty * unit price, rounded. */
fun lineAmount(qty: Double, unitPrice: Double): Double = round2(qty * unitPrice)

/** Taxable portion of a GST-inclusive amount at [gstPercent] %. */
fun taxableOf(inclusiveAmount: Double, gstPercent: Double): Double =
    round2(inclusiveAmount / (1.0 + gstPercent / 100.0))

/** Total GST inside a GST-inclusive amount. */
fun gstOf(inclusiveAmount: Double, gstPercent: Double): Double =
    round2(inclusiveAmount - taxableOf(inclusiveAmount, gstPercent))

/** GST split equally into CGST and SGST (each rounded; CGST gets any 1-paisa remainder). */
fun cgstOf(inclusiveAmount: Double, gstPercent: Double): Double =
    round2(gstOf(inclusiveAmount, gstPercent) / 2.0)

fun sgstOf(inclusiveAmount: Double, gstPercent: Double): Double =
    round2(gstOf(inclusiveAmount, gstPercent) - cgstOf(inclusiveAmount, gstPercent))

/** A line ready for totals: its amount and its GST rate. */
data class PricedLine(val amount: Double, val gstPercent: Double)

/** Full breakup of a bill, computed from its lines and discount %. */
data class BillTotals(
    val subtotal: Double,
    val discountAmount: Double,
    val discountedSubtotal: Double,
    val taxable: Double,
    val cgst: Double,
    val sgst: Double,
    val grandTotal: Double,
)

/**
 * Compute a bill's totals. Discount is taken off the subtotal first; the GST
 * breakup is then worked out on the discounted amount, line by line, so a
 * bill mixing GST rates still breaks up correctly.
 */
fun computeBillTotals(lines: List<PricedLine>, discountPercent: Double): BillTotals {
    val subtotal = round2(lines.sumOf { it.amount })
    val discountAmount = round2(subtotal * discountPercent / 100.0)
    val discountedSubtotal = round2(subtotal - discountAmount)
    // Scale each line by the discount factor, then break up its GST.
    val factor = if (subtotal > 0.0) discountedSubtotal / subtotal else 0.0
    var taxable = 0.0
    var cgst = 0.0
    var sgst = 0.0
    for (line in lines) {
        val discounted = round2(line.amount * factor)
        taxable = round2(taxable + taxableOf(discounted, line.gstPercent))
        cgst = round2(cgst + cgstOf(discounted, line.gstPercent))
        sgst = round2(sgst + sgstOf(discounted, line.gstPercent))
    }
    return BillTotals(
        subtotal = subtotal,
        discountAmount = discountAmount,
        discountedSubtotal = discountedSubtotal,
        taxable = taxable,
        cgst = cgst,
        sgst = sgst,
        grandTotal = round2(taxable + cgst + sgst),
    )
}

/** Format a rupee amount with 2 decimals, e.g. 1234.5 -> "₹1234.50". */
fun formatMoney(value: Double): String = "₹%.2f".format(round2(value))
