package com.billbook.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.billbook.app.data.Bill
import com.billbook.app.data.BillItem
import com.billbook.app.data.Customer
import com.billbook.app.data.Payment
import com.billbook.app.data.Product
import com.billbook.app.data.formatMoney
import com.billbook.app.data.lineAmount
import com.billbook.app.databinding.ItemBillBinding
import com.billbook.app.databinding.ItemBillItemBinding
import com.billbook.app.databinding.ItemCustomerBinding
import com.billbook.app.databinding.ItemPaymentBinding
import com.billbook.app.databinding.ItemProductBinding
import com.billbook.app.databinding.ItemProductPickBinding

class ProductAdapter(
    private val onEdit: (Product) -> Unit,
    private val onStockIn: (Product) -> Unit,
    private val onDelete: (Product) -> Unit,
) : ListAdapter<Product, ProductAdapter.Holder>(Diff()) {
    class Diff : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(a: Product, b: Product) = a.id == b.id
        override fun areContentsTheSame(a: Product, b: Product) = a == b
    }

    inner class Holder(private val b: ItemProductBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(p: Product) {
            b.textProductName.text = p.name
            b.textProductPrices.text = "Wholesale ${formatMoney(p.wholesalePrice)} · " +
                "Retail ${formatMoney(p.retailPrice)} · GST ${p.gstPercent}%"
            b.textProductStock.text = "Stock: ${p.stock} ${p.unit}"
            b.buttonEdit.setOnClickListener { onEdit(p) }
            b.buttonStockIn.setOnClickListener { onStockIn(p) }
            b.buttonDelete.setOnClickListener { onDelete(p) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
}

class BillItemAdapter(
    private val onEdit: (BillItem) -> Unit,
    private val onDelete: (BillItem) -> Unit,
) : ListAdapter<BillItem, BillItemAdapter.Holder>(Diff()) {
    class Diff : DiffUtil.ItemCallback<BillItem>() {
        override fun areItemsTheSame(a: BillItem, b: BillItem) = a.id == b.id
        override fun areContentsTheSame(a: BillItem, b: BillItem) = a == b
    }

    inner class Holder(private val b: ItemBillItemBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: BillItem) {
            b.textLineName.text = item.productName
            b.textLineDetail.text = "${item.qty} ${item.unit} × ${formatMoney(item.unitPrice)} " +
                "(${item.priceKind}, GST ${item.gstPercent}%) = " +
                formatMoney(lineAmount(item.qty, item.unitPrice))
            b.buttonLineEdit.setOnClickListener { onEdit(item) }
            b.buttonLineDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemBillItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
}

class BillAdapter(
    private val onClick: (Bill) -> Unit,
) : ListAdapter<Bill, BillAdapter.Holder>(Diff()) {
    class Diff : DiffUtil.ItemCallback<Bill>() {
        override fun areItemsTheSame(a: Bill, b: Bill) = a.id == b.id
        override fun areContentsTheSame(a: Bill, b: Bill) = a == b
    }

    inner class Holder(private val b: ItemBillBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(bill: Bill) {
            b.textBillTitle.text = "${bill.billNumber ?: "Draft"} — ${bill.customerName}"
            b.textBillSubtitle.text = "${bill.paymentType} · ${bill.printedDate ?: bill.createdDate} · " +
                formatMoney(bill.total)
            b.root.setOnClickListener { onClick(bill) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemBillBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
}

class CustomerAdapter(
    private val dues: Map<Long, Double>,
    private val onRecordPayment: (Customer) -> Unit,
    private val onHistory: (Customer) -> Unit,
) : ListAdapter<Customer, CustomerAdapter.Holder>(Diff()) {
    class Diff : DiffUtil.ItemCallback<Customer>() {
        override fun areItemsTheSame(a: Customer, b: Customer) = a.id == b.id
        override fun areContentsTheSame(a: Customer, b: Customer) = a == b
    }

    inner class Holder(private val b: ItemCustomerBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(c: Customer) {
            b.textCustomerName.text = c.name
            b.textCustomerDue.text = "Due: ${formatMoney(dues[c.id] ?: 0.0)}"
            b.buttonRecordPayment.setOnClickListener { onRecordPayment(c) }
            b.buttonPaymentHistory.setOnClickListener { onHistory(c) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemCustomerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
}

class PaymentAdapter : ListAdapter<Payment, PaymentAdapter.Holder>(Diff()) {
    class Diff : DiffUtil.ItemCallback<Payment>() {
        override fun areItemsTheSame(a: Payment, b: Payment) = a.id == b.id
        override fun areContentsTheSame(a: Payment, b: Payment) = a == b
    }

    inner class Holder(private val b: ItemPaymentBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(p: Payment) {
            b.textPaymentAmount.text = formatMoney(p.amount)
            b.textPaymentDetail.text = "${p.method} · ${p.date}"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPaymentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
}

class ProductPickAdapter(
    private val onPick: (Product) -> Unit,
) : ListAdapter<Product, ProductPickAdapter.Holder>(Diff()) {
    class Diff : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(a: Product, b: Product) = a.id == b.id
        override fun areContentsTheSame(a: Product, b: Product) = a == b
    }

    inner class Holder(private val b: ItemProductPickBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(p: Product) {
            b.textPickName.text = p.name
            b.textPickPrices.text = "Wholesale ${formatMoney(p.wholesalePrice)} · " +
                "Retail ${formatMoney(p.retailPrice)}"
            b.textPickStock.text = "In stock: ${p.stock} ${p.unit}"
            b.root.setOnClickListener { onPick(p) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemProductPickBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
}
