package com.billbook.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Product::class, Customer::class, Bill::class, BillItem::class,
        Payment::class, StockMovement::class, ShopProfile::class],
    version = 1,
    exportSchema = false,
)
abstract class BillDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun customerDao(): CustomerDao
    abstract fun billDao(): BillDao
    abstract fun billItemDao(): BillItemDao
    abstract fun paymentDao(): PaymentDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun shopProfileDao(): ShopProfileDao

    companion object {
        @Volatile
        private var instance: BillDatabase? = null

        fun get(context: Context): BillDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BillDatabase::class.java,
                    "billbook.db",
                ).build().also { instance = it }
            }
    }
}
