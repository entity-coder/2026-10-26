package com.billbook.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.billbook.app.data.Bill
import com.billbook.app.data.BillItem
import com.billbook.app.data.BillTotals
import com.billbook.app.data.ShopProfile
import com.billbook.app.data.formatMoney
import com.billbook.app.data.lineAmount
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a bill as a one-or-more-page PDF. The same document is used for
 * Android printing (PrintManager) and for Share-as-PDF, so the printed
 * copy and the shared copy can never disagree.
 */
object BillPdf {

    private const val PAGE_WIDTH = 595 // A4 at 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val LINE_HEIGHT = 20f

    fun build(
        bill: Bill,
        items: List<BillItem>,
        totals: BillTotals,
        profile: ShopProfile?,
    ): PdfDocument {
        val doc = PdfDocument()
        val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true }
        val normalPaint = Paint().apply { textSize = 13f }
        val boldPaint = Paint().apply { textSize = 13f; isFakeBoldText = true }

        var page = doc.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        )
        var canvas: Canvas = page.canvas
        var y = MARGIN + 20f

        fun line(text: String, paint: Paint = normalPaint) {
            if (y > PAGE_HEIGHT - MARGIN) {
                doc.finishPage(page)
                page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                )
                canvas = page.canvas
                y = MARGIN + 20f
            }
            canvas.drawText(text, MARGIN, y, paint)
            y += LINE_HEIGHT
        }

        if (!profile?.name.isNullOrBlank()) line(profile!!.name, titlePaint)
        if (!profile?.address.isNullOrBlank()) line(profile!!.address)
        if (!profile?.gstin.isNullOrBlank()) line("GSTIN: ${profile!!.gstin}")
        if (!profile?.phone.isNullOrBlank()) line("Phone: ${profile!!.phone}")
        line("")
        line("Bill ${bill.billNumber ?: "Draft"}", boldPaint)
        line("Customer: ${bill.customerName}")
        line("Payment: ${bill.paymentType}")
        if (bill.printedDate != null) line("Date: ${bill.printedDate}")
        line("--------------------------------------------------")
        for (item in items) {
            line(
                "${item.productName} — ${item.qty} ${item.unit} x " +
                    "${formatMoney(item.unitPrice)} (${item.priceKind}) = " +
                    formatMoney(lineAmount(item.qty, item.unitPrice))
            )
        }
        line("--------------------------------------------------")
        line("Subtotal: ${formatMoney(totals.subtotal)}")
        line("Discount: -${formatMoney(totals.discountAmount)}")
        line("Taxable value: ${formatMoney(totals.taxable)}")
        line("CGST: ${formatMoney(totals.cgst)}")
        line("SGST: ${formatMoney(totals.sgst)}")
        line("Grand total: ${formatMoney(totals.grandTotal)}", boldPaint)
        doc.finishPage(page)
        return doc
    }

    /** Write the bill PDF into the app cache and return the file. */
    fun writeToCache(
        context: Context,
        bill: Bill,
        items: List<BillItem>,
        totals: BillTotals,
        profile: ShopProfile?,
    ): File {
        val file = File(context.cacheDir, "bill-${bill.billNumber ?: "draft-${bill.id}"}.pdf")
        FileOutputStream(file).use { out ->
            build(bill, items, totals, profile).use { doc -> doc.writeTo(out) }
        }
        return file
    }
}
