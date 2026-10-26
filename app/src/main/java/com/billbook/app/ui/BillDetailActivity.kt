package com.billbook.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.billbook.app.R
import com.billbook.app.data.BillDatabase
import com.billbook.app.data.BillRepository
import com.billbook.app.data.PricedLine
import com.billbook.app.data.computeBillTotals
import com.billbook.app.data.formatMoney
import com.billbook.app.data.lineAmount
import com.billbook.app.databinding.ActivityBillDetailBinding
import kotlinx.coroutines.launch
import java.io.FileOutputStream

/**
 * Printed-bill detail: shop profile, lines, GST breakup, plus Print (Android
 * PrintManager), Share as PDF (PdfDocument + FileProvider), and
 * Edit-after-print (reverses stock, back to draft).
 */
class BillDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BILL_ID = "bill_id"
    }

    private lateinit var binding: ActivityBillDetailBinding
    private lateinit var repo: BillRepository
    private var billId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBillDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val db = BillDatabase.get(this)
        repo = BillRepository(db)
        billId = intent.getLongExtra(EXTRA_BILL_ID, -1L)

        // Detail lines are read-only here (editing happens on the draft).
        val adapter = BillItemAdapter({}, {})
        binding.recyclerDetailItems.layoutManager = LinearLayoutManager(this)
        binding.recyclerDetailItems.adapter = adapter

        lifecycleScope.launch {
            val withItems = db.billDao().getWithItems(billId) ?: return@launch
            val bill = withItems.bill
            val items = withItems.items
            val profile = db.shopProfileDao().get()
            val totals = computeBillTotals(
                items.map { PricedLine(lineAmount(it.qty, it.unitPrice), it.gstPercent) },
                bill.discountPercent,
            )
            binding.textShopProfile.text = listOfNotNull(
                profile?.name?.takeIf { it.isNotBlank() },
                profile?.address?.takeIf { it.isNotBlank() },
                profile?.gstin?.takeIf { it.isNotBlank() }?.let { "GSTIN: $it" },
                profile?.phone?.takeIf { it.isNotBlank() }?.let { "Phone: $it" },
            ).joinToString("\n")
            binding.textBillNumber.text = bill.billNumber ?: "Draft"
            binding.textCustomer.text =
                getString(R.string.bill_detail_customer_format, bill.customerName)
            binding.textPaymentType.text =
                getString(R.string.bill_detail_payment_format, bill.paymentType) +
                    (bill.printedDate?.let { " · " + getString(R.string.bill_detail_date_format, it) } ?: "")
            adapter.submitList(items)
            binding.textDetailSubtotal.text =
                getString(R.string.subtotal_format, formatMoney(totals.subtotal))
            binding.textDetailDiscount.text =
                getString(R.string.discount_amount_format, formatMoney(totals.discountAmount))
            binding.textDetailTaxable.text =
                getString(R.string.taxable_format, formatMoney(totals.taxable))
            binding.textDetailCgst.text =
                getString(R.string.cgst_format, formatMoney(totals.cgst))
            binding.textDetailSgst.text =
                getString(R.string.sgst_format, formatMoney(totals.sgst))
            binding.textDetailGrandTotal.text =
                getString(R.string.grand_total_format, formatMoney(totals.grandTotal))

            binding.buttonPrint.setOnClickListener { printBill() }
            binding.buttonSharePdf.setOnClickListener { sharePdf() }
            binding.buttonEdit.setOnClickListener {
                lifecycleScope.launch {
                    repo.revertPrintedBillToDraft(billId)
                    Toast.makeText(this@BillDetailActivity, R.string.bill_reverted, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun printBill() {
        val printManager = getSystemService(PRINT_SERVICE) as PrintManager
        printManager.print(
            "Bill",
            object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?,
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder("bill.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<PageRange>,
                    destination: ParcelFileDescriptor,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?,
                ) {
                    lifecycleScope.launch {
                        val db = BillDatabase.get(this@BillDetailActivity)
                        val withItems = db.billDao().getWithItems(billId)
                        val profile = db.shopProfileDao().get()
                        if (withItems == null) {
                            callback?.onWriteFailed("Bill not found")
                            return@launch
                        }
                        val totals = computeBillTotals(
                            withItems.items.map {
                                PricedLine(lineAmount(it.qty, it.unitPrice), it.gstPercent)
                            },
                            withItems.bill.discountPercent,
                        )
                        try {
                            FileOutputStream(destination.fileDescriptor).use { out ->
                                BillPdf.build(withItems.bill, withItems.items, totals, profile)
                                    .use { doc -> doc.writeTo(out) }
                            }
                            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                        } catch (e: Exception) {
                            callback?.onWriteFailed(e.message)
                        }
                    }
                }
            },
            null,
        )
    }

    private fun sharePdf() {
        lifecycleScope.launch {
            val db = BillDatabase.get(this@BillDetailActivity)
            val withItems = db.billDao().getWithItems(billId) ?: return@launch
            val profile = db.shopProfileDao().get()
            val totals = computeBillTotals(
                withItems.items.map {
                    PricedLine(lineAmount(it.qty, it.unitPrice), it.gstPercent)
                },
                withItems.bill.discountPercent,
            )
            val file = BillPdf.writeToCache(
                this@BillDetailActivity, withItems.bill, withItems.items, totals, profile
            )
            val uri = FileProvider.getUriForFile(
                this@BillDetailActivity,
                "${applicationContext.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    getString(R.string.share_bill_subject, withItems.bill.billNumber ?: ""),
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_pdf)))
        }
    }
}
