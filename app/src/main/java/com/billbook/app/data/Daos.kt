package com.billbook.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun observeAll(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): Product?

    @Insert
    suspend fun insert(product: Product): Long

    @Update
    suspend fun update(product: Product)

    @Delete
    suspend fun delete(product: Product)

    @Query("SELECT * FROM products WHERE stock <= lowStockThreshold ORDER BY name ASC")
    fun observeLowStock(): Flow<List<Product>>
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun observeAll(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): Customer?

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: Long): Customer?

    @Insert
    suspend fun insert(customer: Customer): Long
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills WHERE status = 'DRAFT' ORDER BY id ASC")
    fun observeDrafts(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE status = 'PRINTED' ORDER BY id DESC")
    fun observePrinted(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getById(id: Long): Bill?

    @Transaction
    @Query("SELECT * FROM bills WHERE id = :id")
    suspend fun getWithItems(id: Long): BillWithItems?

    @Insert
    suspend fun insert(bill: Bill): Long

    @Update
    suspend fun update(bill: Bill)

    @Delete
    suspend fun delete(bill: Bill)

    /** Sum of printed bills for one day (dashboard "today's sales"). */
    @Query("SELECT COALESCE(SUM(total), 0) FROM bills WHERE status = 'PRINTED' AND printedDate = :date")
    fun observePrintedTotalForDate(date: String): Flow<Double>

    /** All printed Credit bills for a customer — the "owed" half of their khata dues. */
    @Query("SELECT * FROM bills WHERE status = 'PRINTED' AND paymentType = 'Credit' AND customerId = :customerId")
    suspend fun printedCreditBills(customerId: Long): List<Bill>

    /** Sum of printed Credit bills for one customer (the "owed" half, as a total). */
    @Query("SELECT COALESCE(SUM(total), 0) FROM bills WHERE status = 'PRINTED' AND paymentType = 'Credit' AND customerId = :customerId")
    suspend fun printedCreditTotal(customerId: Long): Double

    /** Highest bill number already assigned, for the next-number calculation. */
    @Query("SELECT billNumber FROM bills WHERE billNumber IS NOT NULL ORDER BY id DESC")
    suspend fun allBillNumbers(): List<String>
}

@Dao
interface BillItemDao {
    @Query("SELECT * FROM bill_items WHERE billId = :billId ORDER BY id ASC")
    fun observeForBill(billId: Long): Flow<List<BillItem>>

    @Query("SELECT * FROM bill_items WHERE billId = :billId ORDER BY id ASC")
    suspend fun getForBill(billId: Long): List<BillItem>

    @Insert
    suspend fun insert(item: BillItem): Long

    @Update
    suspend fun update(item: BillItem)

    @Delete
    suspend fun delete(item: BillItem)

    @Query("DELETE FROM bill_items WHERE billId = :billId")
    suspend fun deleteForBill(billId: Long)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE customerId = :customerId ORDER BY id DESC")
    fun observeForCustomer(customerId: Long): Flow<List<Payment>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE customerId = :customerId")
    suspend fun totalPaid(customerId: Long): Double

    @Insert
    suspend fun insert(payment: Payment): Long
}

@Dao
interface StockMovementDao {
    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY id DESC")
    fun observeForProduct(productId: Long): Flow<List<StockMovement>>

    @Insert
    suspend fun insert(movement: StockMovement)
}

@Dao
interface ShopProfileDao {
    @Query("SELECT * FROM shop_profile WHERE id = 1")
    fun observe(): Flow<ShopProfile?>

    @Query("SELECT * FROM shop_profile WHERE id = 1")
    suspend fun get(): ShopProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: ShopProfile)
}
