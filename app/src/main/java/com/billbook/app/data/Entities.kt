package com.billbook.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Payment types offered at billing time and when recording a payment. */
object PaymentType {
    const val CASH = "Cash"
    const val UPI = "UPI"
    const val CREDIT = "Credit"
    const val BANK = "Bank"
}

/** Bill lifecycle. DRAFT bills never touch stock; PRINTED bills already deducted it. */
object BillStatus {
    const val DRAFT = "DRAFT"
    const val PRINTED = "PRINTED"
}

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String, // e.g. "kg", "box", "pcs"
    val wholesalePrice: Double,
    val retailPrice: Double,
    val gstPercent: Double,
    val stock: Double,
    val lowStockThreshold: Double,
)

/**
 * A customer row is created automatically the first time a bill is started
 * with a new name (see BillRepository). Dues are NEVER stored on this row —
 * they are always computed (see BillRepository.customerDue).
 */
@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
)

/**
 * A bill. [billNumber] is null while the bill is a draft and is assigned at
 * print time (BB-0001, BB-0002, ...) — see BillRepository.printBill. A bill
 * edited after printing keeps the number it was first printed with, so a
 * re-print never renumbers it. [total] is the grand total frozen at print
 * time; the detail screen recomputes the live breakup from the items.
 */
@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val customerName: String,
    val paymentType: String,
    val status: String = BillStatus.DRAFT,
    val billNumber: String? = null,
    val discountPercent: Double = 0.0,
    val total: Double = 0.0,
    val createdDate: String, // yyyy-MM-dd
    val printedDate: String? = null, // yyyy-MM-dd, set at print time
)

@Entity(tableName = "bill_items")
data class BillItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billId: Long,
    val productId: Long,
    val productName: String,
    val unit: String,
    val qty: Double,
    val unitPrice: Double, // wholesale or retail price chosen for this line
    val priceKind: String, // "Wholesale" or "Retail"
    val gstPercent: Double,
)

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val customerName: String,
    val amount: Double,
    val method: String, // Cash / UPI / Bank
    val date: String, // yyyy-MM-dd
    val note: String = "",
)

/** Every stock change is logged here: Stock In, print deduction, edit reversal. */
@Entity(tableName = "stock_movements")
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val change: Double, // + for stock in / reversal, - for print deduction
    val reason: String, // "Stock In", "Bill BB-0001", "Edit reversal BB-0001"
    val date: String, // yyyy-MM-dd
)

/** Single-row shop profile, shown on every bill and PDF. id is always 1. */
@Entity(tableName = "shop_profile")
data class ShopProfile(
    @PrimaryKey val id: Long = 1,
    val name: String = "",
    val address: String = "",
    val gstin: String = "",
    val phone: String = "",
)

/** A bill together with its lines, for the detail screen and PDF. */
data class BillWithItems(
    @Embedded val bill: Bill,
    @Relation(parentColumn = "id", entityColumn = "billId")
    val items: List<BillItem>,
)
